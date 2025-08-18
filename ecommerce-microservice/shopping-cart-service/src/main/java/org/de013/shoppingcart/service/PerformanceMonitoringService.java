package org.de013.shoppingcart.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance Monitoring Service
 * Tracks and monitors application performance metrics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceMonitoringService {

    private final MeterRegistry meterRegistry;
    private final CacheManager cacheManager;

    // Performance counters
    private final AtomicLong cartOperationCount = new AtomicLong(0);
    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);
    private final AtomicLong databaseQueryCount = new AtomicLong(0);
    private final AtomicLong externalServiceCallCount = new AtomicLong(0);

    // Performance timers
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    // ==================== PERFORMANCE TRACKING ====================

    /**
     * Track cart operation performance
     */
    public void trackCartOperation(String operation, Duration duration, boolean success) {
        try {
            cartOperationCount.incrementAndGet();
            
            // Record timing
            Timer timer = getOrCreateTimer("cart.operation", "operation", operation);
            timer.record(duration);
            
            // Record success/failure
            Counter counter = getOrCreateCounter("cart.operation.result", 
                "operation", operation, "result", success ? "success" : "failure");
            counter.increment();
            
            log.debug("Cart operation tracked - Operation: {}, Duration: {}ms, Success: {}", 
                    operation, duration.toMillis(), success);
            
        } catch (Exception e) {
            log.error("Error tracking cart operation: {}", e.getMessage());
        }
    }

    /**
     * Track cache performance
     */
    public void trackCacheOperation(String cacheName, String operation, boolean hit) {
        try {
            if (hit) {
                cacheHitCount.incrementAndGet();
            } else {
                cacheMissCount.incrementAndGet();
            }
            
            Counter counter = getOrCreateCounter("cache.operation", 
                "cache", cacheName, "operation", operation, "result", hit ? "hit" : "miss");
            counter.increment();
            
            log.debug("Cache operation tracked - Cache: {}, Operation: {}, Hit: {}", 
                    cacheName, operation, hit);
            
        } catch (Exception e) {
            log.error("Error tracking cache operation: {}", e.getMessage());
        }
    }

    /**
     * Track database query performance
     */
    public void trackDatabaseQuery(String queryType, Duration duration, int resultCount) {
        try {
            databaseQueryCount.incrementAndGet();
            
            Timer timer = getOrCreateTimer("database.query", "type", queryType);
            timer.record(duration);
            
            Counter counter = getOrCreateCounter("database.query.count", "type", queryType);
            counter.increment();
            
            // Track result count
            meterRegistry.gauge("database.query.results", 
                Map.of("type", queryType), resultCount);
            
            log.debug("Database query tracked - Type: {}, Duration: {}ms, Results: {}", 
                    queryType, duration.toMillis(), resultCount);
            
        } catch (Exception e) {
            log.error("Error tracking database query: {}", e.getMessage());
        }
    }

    /**
     * Track external service call performance
     */
    public void trackExternalServiceCall(String serviceName, String operation, 
                                       Duration duration, boolean success) {
        try {
            externalServiceCallCount.incrementAndGet();
            
            Timer timer = getOrCreateTimer("external.service.call", 
                "service", serviceName, "operation", operation);
            timer.record(duration);
            
            Counter counter = getOrCreateCounter("external.service.result", 
                "service", serviceName, "operation", operation, 
                "result", success ? "success" : "failure");
            counter.increment();
            
            log.debug("External service call tracked - Service: {}, Operation: {}, Duration: {}ms, Success: {}", 
                    serviceName, operation, duration.toMillis(), success);
            
        } catch (Exception e) {
            log.error("Error tracking external service call: {}", e.getMessage());
        }
    }

    // ==================== PERFORMANCE METRICS ====================

    /**
     * Get comprehensive performance metrics
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // Basic counters
            metrics.put("cartOperations", cartOperationCount.get());
            metrics.put("cacheHits", cacheHitCount.get());
            metrics.put("cacheMisses", cacheMissCount.get());
            metrics.put("databaseQueries", databaseQueryCount.get());
            metrics.put("externalServiceCalls", externalServiceCallCount.get());
            
            // Cache hit ratio
            long totalCacheOps = cacheHitCount.get() + cacheMissCount.get();
            double cacheHitRatio = totalCacheOps > 0 ? 
                (double) cacheHitCount.get() / totalCacheOps : 0.0;
            metrics.put("cacheHitRatio", cacheHitRatio);
            
            // Performance summary
            metrics.put("performanceSummary", getPerformanceSummary());
            
            // Cache statistics
            metrics.put("cacheStatistics", getCacheStatistics());
            
            // Timing statistics
            metrics.put("timingStatistics", getTimingStatistics());
            
            metrics.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error getting performance metrics: {}", e.getMessage());
            metrics.put("error", e.getMessage());
        }
        
        return metrics;
    }

    /**
     * Get performance summary
     */
    private Map<String, Object> getPerformanceSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            // Calculate averages and rates
            long totalOps = cartOperationCount.get();
            summary.put("totalOperations", totalOps);
            
            // Cache efficiency
            long totalCacheOps = cacheHitCount.get() + cacheMissCount.get();
            if (totalCacheOps > 0) {
                double efficiency = (double) cacheHitCount.get() / totalCacheOps * 100;
                summary.put("cacheEfficiency", String.format("%.2f%%", efficiency));
            }
            
            // Database query ratio
            if (totalOps > 0) {
                double dbQueryRatio = (double) databaseQueryCount.get() / totalOps;
                summary.put("databaseQueryRatio", String.format("%.2f", dbQueryRatio));
            }
            
            // External service call ratio
            if (totalOps > 0) {
                double extServiceRatio = (double) externalServiceCallCount.get() / totalOps;
                summary.put("externalServiceRatio", String.format("%.2f", extServiceRatio));
            }
            
        } catch (Exception e) {
            log.error("Error calculating performance summary: {}", e.getMessage());
        }
        
        return summary;
    }

    /**
     * Get cache statistics
     */
    private Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // L1 Cache (Caffeine) statistics
            if (cacheManager instanceof org.springframework.cache.caffeine.CaffeineCacheManager) {
                stats.put("l1Cache", "caffeine");
                // Add Caffeine-specific stats
            }
            
            // L2 Cache (Redis) statistics
            stats.put("l2Cache", "redis");
            stats.put("totalHits", cacheHitCount.get());
            stats.put("totalMisses", cacheMissCount.get());
            
            long total = cacheHitCount.get() + cacheMissCount.get();
            if (total > 0) {
                stats.put("hitRatio", (double) cacheHitCount.get() / total);
                stats.put("missRatio", (double) cacheMissCount.get() / total);
            }
            
        } catch (Exception e) {
            log.error("Error getting cache statistics: {}", e.getMessage());
        }
        
        return stats;
    }

    /**
     * Get timing statistics
     */
    private Map<String, Object> getTimingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            for (Map.Entry<String, Timer> entry : timers.entrySet()) {
                Timer timer = entry.getValue();
                Map<String, Object> timerStats = new HashMap<>();
                
                timerStats.put("count", timer.count());
                timerStats.put("totalTime", timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
                timerStats.put("mean", timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
                timerStats.put("max", timer.max(java.util.concurrent.TimeUnit.MILLISECONDS));
                
                stats.put(entry.getKey(), timerStats);
            }
            
        } catch (Exception e) {
            log.error("Error getting timing statistics: {}", e.getMessage());
        }
        
        return stats;
    }

    // ==================== PERFORMANCE ALERTS ====================

    /**
     * Check performance thresholds and generate alerts
     */
    public Map<String, Object> checkPerformanceThresholds() {
        Map<String, Object> alerts = new HashMap<>();
        
        try {
            // Cache hit ratio threshold
            long totalCacheOps = cacheHitCount.get() + cacheMissCount.get();
            if (totalCacheOps > 100) { // Only check if we have enough data
                double hitRatio = (double) cacheHitCount.get() / totalCacheOps;
                if (hitRatio < 0.7) { // Less than 70% hit ratio
                    alerts.put("lowCacheHitRatio", Map.of(
                        "current", hitRatio,
                        "threshold", 0.7,
                        "severity", "WARNING"
                    ));
                }
            }
            
            // High database query ratio
            long totalOps = cartOperationCount.get();
            if (totalOps > 50) {
                double dbRatio = (double) databaseQueryCount.get() / totalOps;
                if (dbRatio > 2.0) { // More than 2 DB queries per operation
                    alerts.put("highDatabaseQueryRatio", Map.of(
                        "current", dbRatio,
                        "threshold", 2.0,
                        "severity", "WARNING"
                    ));
                }
            }
            
            // Check average response times
            checkResponseTimeThresholds(alerts);
            
            alerts.put("timestamp", LocalDateTime.now());
            alerts.put("alertCount", alerts.size() - 1); // Exclude timestamp
            
        } catch (Exception e) {
            log.error("Error checking performance thresholds: {}", e.getMessage());
            alerts.put("error", e.getMessage());
        }
        
        return alerts;
    }

    /**
     * Check response time thresholds
     */
    private void checkResponseTimeThresholds(Map<String, Object> alerts) {
        try {
            for (Map.Entry<String, Timer> entry : timers.entrySet()) {
                Timer timer = entry.getValue();
                if (timer.count() > 10) { // Only check if we have enough data
                    double meanTime = timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
                    
                    // Different thresholds for different operations
                    double threshold = getResponseTimeThreshold(entry.getKey());
                    
                    if (meanTime > threshold) {
                        alerts.put("slowResponseTime_" + entry.getKey(), Map.of(
                            "operation", entry.getKey(),
                            "currentMean", meanTime,
                            "threshold", threshold,
                            "severity", meanTime > threshold * 2 ? "CRITICAL" : "WARNING"
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking response time thresholds: {}", e.getMessage());
        }
    }

    /**
     * Get response time threshold for operation
     */
    private double getResponseTimeThreshold(String operation) {
        // Define thresholds based on operation type
        if (operation.contains("database")) {
            return 100.0; // 100ms for database operations
        } else if (operation.contains("external")) {
            return 500.0; // 500ms for external service calls
        } else if (operation.contains("cache")) {
            return 10.0;  // 10ms for cache operations
        } else {
            return 200.0; // 200ms for general operations
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Get or create timer
     */
    private Timer getOrCreateTimer(String name, String... tags) {
        String key = name + ":" + String.join(":", tags);
        return timers.computeIfAbsent(key, k -> 
            Timer.builder(name)
                .tags(tags)
                .register(meterRegistry));
    }

    /**
     * Get or create counter
     */
    private Counter getOrCreateCounter(String name, String... tags) {
        String key = name + ":" + String.join(":", tags);
        return counters.computeIfAbsent(key, k -> 
            Counter.builder(name)
                .tags(tags)
                .register(meterRegistry));
    }

    /**
     * Reset performance metrics
     */
    public void resetMetrics() {
        cartOperationCount.set(0);
        cacheHitCount.set(0);
        cacheMissCount.set(0);
        databaseQueryCount.set(0);
        externalServiceCallCount.set(0);
        
        log.info("Performance metrics reset");
    }

    /**
     * Get current performance status
     */
    public String getPerformanceStatus() {
        try {
            Map<String, Object> alerts = checkPerformanceThresholds();
            int alertCount = (Integer) alerts.getOrDefault("alertCount", 0);
            
            if (alertCount == 0) {
                return "HEALTHY";
            } else if (alertCount <= 2) {
                return "WARNING";
            } else {
                return "CRITICAL";
            }
        } catch (Exception e) {
            log.error("Error getting performance status: {}", e.getMessage());
            return "UNKNOWN";
        }
    }
}
