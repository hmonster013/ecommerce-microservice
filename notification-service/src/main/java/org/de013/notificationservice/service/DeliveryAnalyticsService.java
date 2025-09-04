package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.enums.DeliveryStatus;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.repository.NotificationDeliveryRepository;
import org.de013.notificationservice.repository.NotificationRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for delivery analytics and reporting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryAnalyticsService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Record delivery attempt for analytics
     */
    public void recordDeliveryAttempt(Long notificationId, NotificationChannel channel, 
                                    NotificationType type, DeliveryStatus status, long processingTimeMs) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
            
            // Record delivery metrics
            recordMetric("delivery_attempts", channel, type, timestamp);
            recordMetric("delivery_status_" + status.name().toLowerCase(), channel, type, timestamp);
            
            // Record processing time
            recordProcessingTime(channel, type, timestamp, processingTimeMs);
            
            // Record success/failure rates
            if (status.isSuccess()) {
                recordMetric("delivery_success", channel, type, timestamp);
            } else if (status.isFailure()) {
                recordMetric("delivery_failure", channel, type, timestamp);
            }
            
            log.debug("Recorded delivery attempt: notificationId={}, channel={}, type={}, status={}, processingTime={}ms", 
                    notificationId, channel, type, status, processingTimeMs);
            
        } catch (Exception e) {
            log.error("Error recording delivery attempt: notificationId={}, error={}", notificationId, e.getMessage(), e);
        }
    }

    /**
     * Get delivery statistics for a time period
     */
    public DeliveryStatistics getDeliveryStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        try {
            log.info("Generating delivery statistics from {} to {}", startDate, endDate);
            
            // Get notification statistics from database
            List<Object[]> notificationStats = notificationRepository.getNotificationStatistics(startDate, endDate);
            List<Object[]> deliveryStats = deliveryRepository.getDeliveryStatistics(startDate, endDate);
            
            DeliveryStatistics statistics = new DeliveryStatistics();
            statistics.setStartDate(startDate);
            statistics.setEndDate(endDate);
            statistics.setGeneratedAt(LocalDateTime.now());
            
            // Process notification statistics
            Map<String, Map<String, Long>> notificationByTypeAndChannel = new HashMap<>();
            Map<String, Long> notificationByStatus = new HashMap<>();
            
            for (Object[] row : notificationStats) {
                NotificationType type = (NotificationType) row[0];
                NotificationChannel channel = (NotificationChannel) row[1];
                NotificationStatus status = (NotificationStatus) row[2];
                Long count = (Long) row[3];
                
                // Group by type and channel
                notificationByTypeAndChannel
                        .computeIfAbsent(type.name(), k -> new HashMap<>())
                        .put(channel.name(), count);
                
                // Group by status
                notificationByStatus.merge(status.name(), count, Long::sum);
            }
            
            // Process delivery statistics
            Map<String, Map<String, Object>> deliveryByChannel = new HashMap<>();
            
            for (Object[] row : deliveryStats) {
                NotificationChannel channel = (NotificationChannel) row[0];
                DeliveryStatus status = (DeliveryStatus) row[1];
                Long count = (Long) row[2];
                Double avgProcessingTime = (Double) row[3];
                
                Map<String, Object> channelStats = deliveryByChannel.computeIfAbsent(channel.name(), k -> new HashMap<>());
                channelStats.put(status.name() + "_count", count);
                if (avgProcessingTime != null) {
                    channelStats.put("avg_processing_time_ms", avgProcessingTime);
                }
            }
            
            statistics.setNotificationsByTypeAndChannel(notificationByTypeAndChannel);
            statistics.setNotificationsByStatus(notificationByStatus);
            statistics.setDeliveriesByChannel(deliveryByChannel);
            
            // Calculate success rates
            statistics.setSuccessRatesByChannel(calculateSuccessRates(deliveryStats));
            
            // Get real-time metrics from Redis
            statistics.setRealTimeMetrics(getRealTimeMetrics());
            
            log.info("Generated delivery statistics successfully");
            return statistics;
            
        } catch (Exception e) {
            log.error("Error generating delivery statistics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate delivery statistics", e);
        }
    }

    /**
     * Get real-time delivery metrics
     */
    public Map<String, Object> getRealTimeMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            String currentHour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
            
            // Get metrics for each channel
            for (NotificationChannel channel : NotificationChannel.values()) {
                Map<String, Object> channelMetrics = new HashMap<>();
                
                channelMetrics.put("attempts", getMetricValue("delivery_attempts", channel, currentHour));
                channelMetrics.put("successes", getMetricValue("delivery_success", channel, currentHour));
                channelMetrics.put("failures", getMetricValue("delivery_failure", channel, currentHour));
                
                // Calculate success rate
                long attempts = (Long) channelMetrics.get("attempts");
                long successes = (Long) channelMetrics.get("successes");
                double successRate = attempts > 0 ? (double) successes / attempts * 100 : 0;
                channelMetrics.put("success_rate", Math.round(successRate * 100.0) / 100.0);
                
                metrics.put(channel.name().toLowerCase(), channelMetrics);
            }
            
            metrics.put("timestamp", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            log.error("Error getting real-time metrics: {}", e.getMessage(), e);
            metrics.put("error", e.getMessage());
        }
        
        return metrics;
    }

    /**
     * Get delivery performance metrics
     */
    public Map<String, Object> getPerformanceMetrics(NotificationChannel channel, LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // Get average processing time
            Double avgProcessingTime = deliveryRepository.getAverageProcessingTimeByChannel(channel, startDate, endDate);
            metrics.put("avg_processing_time_ms", avgProcessingTime != null ? avgProcessingTime : 0);
            
            // Get success/failure counts
            List<DeliveryStatus> successStatuses = List.of(DeliveryStatus.SUCCESS);
            List<DeliveryStatus> failureStatuses = List.of(DeliveryStatus.FAILED, DeliveryStatus.BOUNCED, 
                    DeliveryStatus.REJECTED, DeliveryStatus.TIMEOUT);
            
            long successCount = deliveryRepository.countSuccessfulDeliveriesByChannel(
                    channel, DeliveryStatus.SUCCESS, startDate, endDate);
            long failureCount = deliveryRepository.countFailedDeliveriesByChannel(
                    channel, failureStatuses, startDate, endDate);
            
            metrics.put("success_count", successCount);
            metrics.put("failure_count", failureCount);
            metrics.put("total_count", successCount + failureCount);
            
            // Calculate success rate
            long totalCount = successCount + failureCount;
            double successRate = totalCount > 0 ? (double) successCount / totalCount * 100 : 0;
            metrics.put("success_rate", Math.round(successRate * 100.0) / 100.0);
            
            // Get cost information
            Long totalCost = deliveryRepository.getTotalCostByChannel(channel, startDate, endDate);
            metrics.put("total_cost_cents", totalCost != null ? totalCost : 0);
            
        } catch (Exception e) {
            log.error("Error getting performance metrics for channel {}: {}", channel, e.getMessage(), e);
            metrics.put("error", e.getMessage());
        }
        
        return metrics;
    }

    /**
     * Record metric in Redis
     */
    private void recordMetric(String metricName, NotificationChannel channel, NotificationType type, String timestamp) {
        try {
            String key = "metrics:" + metricName + ":" + channel.name().toLowerCase() + ":" + type.name().toLowerCase() + ":" + timestamp;
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, java.time.Duration.ofDays(7)); // Keep metrics for 7 days
        } catch (Exception e) {
            log.error("Error recording metric: {}", e.getMessage(), e);
        }
    }

    /**
     * Record processing time metric
     */
    private void recordProcessingTime(NotificationChannel channel, NotificationType type, String timestamp, long processingTimeMs) {
        try {
            String key = "metrics:processing_time:" + channel.name().toLowerCase() + ":" + type.name().toLowerCase() + ":" + timestamp;
            String countKey = key + ":count";
            String totalKey = key + ":total";
            
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.opsForValue().increment(totalKey, processingTimeMs);
            redisTemplate.expire(countKey, java.time.Duration.ofDays(7));
            redisTemplate.expire(totalKey, java.time.Duration.ofDays(7));
        } catch (Exception e) {
            log.error("Error recording processing time metric: {}", e.getMessage(), e);
        }
    }

    /**
     * Get metric value from Redis
     */
    private long getMetricValue(String metricName, NotificationChannel channel, String timestamp) {
        try {
            String pattern = "metrics:" + metricName + ":" + channel.name().toLowerCase() + ":*:" + timestamp;
            return redisTemplate.keys(pattern).stream()
                    .mapToLong(key -> {
                        String value = redisTemplate.opsForValue().get(key);
                        return value != null ? Long.parseLong(value) : 0;
                    })
                    .sum();
        } catch (Exception e) {
            log.error("Error getting metric value: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Calculate success rates by channel
     */
    private Map<String, Double> calculateSuccessRates(List<Object[]> deliveryStats) {
        Map<String, Long> successCounts = new HashMap<>();
        Map<String, Long> totalCounts = new HashMap<>();
        
        for (Object[] row : deliveryStats) {
            NotificationChannel channel = (NotificationChannel) row[0];
            DeliveryStatus status = (DeliveryStatus) row[1];
            Long count = (Long) row[2];
            
            String channelName = channel.name();
            totalCounts.merge(channelName, count, Long::sum);
            
            if (status.isSuccess()) {
                successCounts.merge(channelName, count, Long::sum);
            }
        }
        
        Map<String, Double> successRates = new HashMap<>();
        for (String channel : totalCounts.keySet()) {
            long total = totalCounts.get(channel);
            long success = successCounts.getOrDefault(channel, 0L);
            double rate = total > 0 ? (double) success / total * 100 : 0;
            successRates.put(channel, Math.round(rate * 100.0) / 100.0);
        }
        
        return successRates;
    }

    /**
     * Delivery statistics DTO
     */
    @lombok.Data
    public static class DeliveryStatistics {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private LocalDateTime generatedAt;
        private Map<String, Map<String, Long>> notificationsByTypeAndChannel;
        private Map<String, Long> notificationsByStatus;
        private Map<String, Map<String, Object>> deliveriesByChannel;
        private Map<String, Double> successRatesByChannel;
        private Map<String, Object> realTimeMetrics;
    }
}
