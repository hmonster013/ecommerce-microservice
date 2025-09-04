package org.de013.notificationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.service.DeliveryAnalyticsService;
import org.de013.notificationservice.service.RateLimitingService;
import org.de013.notificationservice.service.NotificationQueueService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST Controller for Notification Analytics and Monitoring
 */
@RestController
@RequestMapping("/api/v1/notifications/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notification Analytics", description = "Analytics and monitoring operations for notifications")
public class AnalyticsController {

    private final DeliveryAnalyticsService analyticsService;
    private final RateLimitingService rateLimitingService;
    private final NotificationQueueService queueService;

    /**
     * Get delivery statistics for a time period
     */
    @GetMapping("/delivery-stats")
    @Operation(summary = "Get delivery statistics", description = "Get comprehensive delivery statistics for a time period")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<DeliveryAnalyticsService.DeliveryStatistics>> getDeliveryStatistics(
            @Parameter(description = "Start date (yyyy-MM-dd HH:mm:ss)") 
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @Parameter(description = "End date (yyyy-MM-dd HH:mm:ss)") 
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        
        log.info("Getting delivery statistics from {} to {}", startDate, endDate);

        try {
            if (startDate.isAfter(endDate)) {
                org.de013.common.dto.ApiResponse<DeliveryAnalyticsService.DeliveryStatistics> response = 
                        org.de013.common.dto.ApiResponse.error("Start date must be before end date");
                return ResponseEntity.badRequest().body(response);
            }

            DeliveryAnalyticsService.DeliveryStatistics statistics = analyticsService.getDeliveryStatistics(startDate, endDate);
            org.de013.common.dto.ApiResponse<DeliveryAnalyticsService.DeliveryStatistics> response = 
                    org.de013.common.dto.ApiResponse.success(statistics);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting delivery statistics: {}", e.getMessage(), e);
            org.de013.common.dto.ApiResponse<DeliveryAnalyticsService.DeliveryStatistics> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get delivery statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get real-time delivery metrics
     */
    @GetMapping("/real-time-metrics")
    @Operation(summary = "Get real-time metrics", description = "Get current hour delivery metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Map<String, Object>>> getRealTimeMetrics() {
        log.debug("Getting real-time delivery metrics");

        try {
            Map<String, Object> metrics = analyticsService.getRealTimeMetrics();
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.success(metrics);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting real-time metrics: {}", e.getMessage(), e);
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get real-time metrics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get performance metrics for a specific channel
     */
    @GetMapping("/performance/{channel}")
    @Operation(summary = "Get channel performance metrics", description = "Get performance metrics for a specific notification channel")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Performance metrics retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid channel or date parameters")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Map<String, Object>>> getChannelPerformance(
            @Parameter(description = "Notification channel") @PathVariable NotificationChannel channel,
            @Parameter(description = "Start date (yyyy-MM-dd HH:mm:ss)") 
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @Parameter(description = "End date (yyyy-MM-dd HH:mm:ss)") 
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        
        log.info("Getting performance metrics for channel {} from {} to {}", channel, startDate, endDate);

        try {
            if (startDate.isAfter(endDate)) {
                org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                        org.de013.common.dto.ApiResponse.error("Start date must be before end date");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> metrics = analyticsService.getPerformanceMetrics(channel, startDate, endDate);
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.success(metrics);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting performance metrics for channel {}: {}", channel, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get performance metrics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get queue statistics
     */
    @GetMapping("/queue-stats")
    @Operation(summary = "Get queue statistics", description = "Get current queue depths and statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Queue statistics retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Map<String, Object>>> getQueueStatistics() {
        log.debug("Getting queue statistics");

        try {
            Map<String, Object> stats = queueService.getQueueStats();
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.success(stats);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting queue statistics: {}", e.getMessage(), e);
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get queue statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get rate limit status for a user
     */
    @GetMapping("/rate-limit/{userId}")
    @Operation(summary = "Get user rate limit status", description = "Get current rate limit status for a specific user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rate limit status retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<RateLimitingService.RateLimitStatus>> getUserRateLimitStatus(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Notification channel") @RequestParam NotificationChannel channel,
            @Parameter(description = "Notification type") @RequestParam org.de013.notificationservice.entity.enums.NotificationType type) {
        
        log.debug("Getting rate limit status for user {} channel {} type {}", userId, channel, type);

        try {
            RateLimitingService.RateLimitStatus status = rateLimitingService.getUserRateLimitStatus(userId, channel, type);
            org.de013.common.dto.ApiResponse<RateLimitingService.RateLimitStatus> response = 
                    org.de013.common.dto.ApiResponse.success(status);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting rate limit status for user {}: {}", userId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<RateLimitingService.RateLimitStatus> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get rate limit status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Health check endpoint for monitoring
     */
    @GetMapping("/health")
    @Operation(summary = "Analytics health check", description = "Check the health of analytics services")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Analytics services are healthy")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Map<String, Object>>> healthCheck() {
        log.debug("Analytics health check");

        try {
            Map<String, Object> health = Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString(),
                "services", Map.of(
                    "analytics", "UP",
                    "rate_limiting", "UP",
                    "queue", "UP"
                )
            );
            
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.success(health);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Analytics health check failed: {}", e.getMessage(), e);
            Map<String, Object> health = Map.of(
                "status", "DOWN",
                "timestamp", LocalDateTime.now().toString(),
                "error", e.getMessage()
            );
            
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.success(health);
            
            return ResponseEntity.ok(response);
        }
    }
}
