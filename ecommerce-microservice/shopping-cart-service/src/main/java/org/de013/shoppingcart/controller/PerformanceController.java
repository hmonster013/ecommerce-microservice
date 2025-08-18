package org.de013.shoppingcart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.config.CacheConfig;
import org.de013.shoppingcart.service.CacheWarmingService;
import org.de013.shoppingcart.service.DatabaseOptimizationService;
import org.de013.shoppingcart.service.PerformanceMonitoringService;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Performance Controller
 * Provides endpoints for performance monitoring, cache management, and optimization
 */
@RestController
@RequestMapping("/api/v1/performance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Performance", description = "APIs for performance monitoring and optimization")
public class PerformanceController {

    private final PerformanceMonitoringService performanceMonitoringService;
    private final CacheWarmingService cacheWarmingService;
    private final DatabaseOptimizationService databaseOptimizationService;
    private final CacheManager cacheManager;
    private final CacheConfig.CacheStatsService cacheStatsService;
    private final CacheConfig.CacheHealthService cacheHealthService;
    private final CacheConfig.CacheEvictionService cacheEvictionService;

    // ==================== PERFORMANCE MONITORING ====================

    @Operation(summary = "Get performance metrics", description = "Get comprehensive performance metrics and statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Performance metrics retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics() {
        try {
            log.debug("Getting performance metrics");
            
            Map<String, Object> metrics = performanceMonitoringService.getPerformanceMetrics();
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Error getting performance metrics: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @Operation(summary = "Get performance status", description = "Get current performance status and health")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Performance status retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getPerformanceStatus() {
        try {
            log.debug("Getting performance status");
            
            Map<String, Object> status = new HashMap<>();
            status.put("performanceStatus", performanceMonitoringService.getPerformanceStatus());
            status.put("cacheHealth", cacheHealthService.checkCacheHealth());
            status.put("databaseHealth", databaseOptimizationService.checkDatabaseHealth());
            status.put("alerts", performanceMonitoringService.checkPerformanceThresholds());
            status.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error getting performance status: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @Operation(summary = "Reset performance metrics", description = "Reset all performance counters and metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Performance metrics reset successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/metrics/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> resetPerformanceMetrics() {
        try {
            log.info("Resetting performance metrics");
            
            performanceMonitoringService.resetMetrics();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Performance metrics reset successfully",
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error resetting performance metrics: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    // ==================== CACHE MANAGEMENT ====================

    @Operation(summary = "Get cache statistics", description = "Get detailed cache statistics for all cache layers")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cache statistics retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/cache/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Map<String, Object>> getCacheStatistics() {
        try {
            log.debug("Getting cache statistics");
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("caffeineStats", cacheStatsService.getCaffeineStats(cacheManager));
            stats.put("redisStats", cacheStatsService.getRedisStats());
            stats.put("cacheHealth", cacheHealthService.checkCacheHealth());
            stats.put("warmingStatus", cacheWarmingService.getCacheWarmingStatus());
            stats.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting cache statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @Operation(summary = "Warm up caches", description = "Trigger cache warming process")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cache warming triggered successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/cache/warmup")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Map<String, Object>> warmupCaches(
            @Parameter(description = "Whether to perform comprehensive warmup")
            @RequestParam(value = "comprehensive", defaultValue = "false") boolean comprehensive) {
        try {
            log.info("Triggering cache warmup - comprehensive: {}", comprehensive);
            
            CompletableFuture<Map<String, Object>> warmupFuture = 
                cacheWarmingService.triggerManualWarmup(comprehensive);
            
            // Return immediately with async response
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Cache warmup triggered successfully",
                "comprehensive", comprehensive,
                "async", true,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error triggering cache warmup: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @Operation(summary = "Evict cache", description = "Evict specific cache or all caches")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cache eviction completed successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/cache/evict")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> evictCache(
            @Parameter(description = "Cache name to evict (optional - evicts all if not specified)")
            @RequestParam(value = "cacheName", required = false) String cacheName,
            @Parameter(description = "Cache key to evict (optional)")
            @RequestParam(value = "key", required = false) String key) {
        try {
            log.info("Evicting cache - cacheName: {}, key: {}", cacheName, key);
            
            if (cacheName == null) {
                // Evict all caches
                cacheEvictionService.evictAllCaches();
                
                Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "All caches evicted successfully",
                    "timestamp", System.currentTimeMillis()
                );
                
                return ResponseEntity.ok(response);
                
            } else {
                // Evict specific cache
                var cache = cacheManager.getCache(cacheName);
                if (cache == null) {
                    return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "Cache not found: " + cacheName,
                        "timestamp", System.currentTimeMillis()
                    ));
                }
                
                if (key != null) {
                    // Evict specific key
                    cache.evict(key);
                    
                    Map<String, Object> response = Map.of(
                        "success", true,
                        "message", "Cache key evicted successfully",
                        "cacheName", cacheName,
                        "key", key,
                        "timestamp", System.currentTimeMillis()
                    );
                    
                    return ResponseEntity.ok(response);
                    
                } else {
                    // Evict entire cache
                    cache.clear();
                    
                    Map<String, Object> response = Map.of(
                        "success", true,
                        "message", "Cache evicted successfully",
                        "cacheName", cacheName,
                        "timestamp", System.currentTimeMillis()
                    );
                    
                    return ResponseEntity.ok(response);
                }
            }
            
        } catch (Exception e) {
            log.error("Error evicting cache: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    // ==================== DATABASE OPTIMIZATION ====================

    @Operation(summary = "Get database performance stats", description = "Get database performance statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Database stats retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/database/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Map<String, Object>> getDatabaseStats() {
        try {
            log.debug("Getting database performance stats");
            
            Map<String, Object> stats = databaseOptimizationService.getDatabasePerformanceStats();
            stats.put("health", databaseOptimizationService.checkDatabaseHealth());
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting database stats: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @Operation(summary = "Cleanup expired carts", description = "Trigger cleanup of expired carts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cleanup completed successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/database/cleanup/expired-carts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanupExpiredCarts(
            @Parameter(description = "Hours before now to consider as expired")
            @RequestParam(value = "hoursAgo", defaultValue = "24") int hoursAgo) {
        try {
            log.info("Triggering cleanup of expired carts - hoursAgo: {}", hoursAgo);
            
            java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusHours(hoursAgo);
            int deletedCount = databaseOptimizationService.batchDeleteExpiredCarts(threshold);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Expired carts cleanup completed",
                "deletedCount", deletedCount,
                "threshold", threshold,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error cleaning up expired carts: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    // ==================== HEALTH CHECK ====================

    @Operation(summary = "Get comprehensive health check", description = "Get comprehensive health check for all performance components")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Health check completed successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthCheck() {
        try {
            log.debug("Performing comprehensive health check");
            
            Map<String, Object> health = new HashMap<>();
            
            // Performance health
            health.put("performance", Map.of(
                "status", performanceMonitoringService.getPerformanceStatus(),
                "alerts", performanceMonitoringService.checkPerformanceThresholds()
            ));
            
            // Cache health
            health.put("cache", cacheHealthService.checkCacheHealth());
            
            // Database health
            health.put("database", databaseOptimizationService.checkDatabaseHealth());
            
            // Overall health
            String overallStatus = determineOverallHealth(health);
            health.put("overall", overallStatus);
            health.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("Error performing health check: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "overall", "DOWN",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Determine overall health status
     */
    private String determineOverallHealth(Map<String, Object> health) {
        try {
            // Check performance status
            @SuppressWarnings("unchecked")
            Map<String, Object> performance = (Map<String, Object>) health.get("performance");
            String perfStatus = (String) performance.get("status");
            
            // Check cache health
            @SuppressWarnings("unchecked")
            Map<String, Object> cache = (Map<String, Object>) health.get("cache");
            String cacheStatus = (String) cache.get("overall");
            
            // Check database health
            @SuppressWarnings("unchecked")
            Map<String, Object> database = (Map<String, Object>) health.get("database");
            String dbStatus = (String) database.get("status");
            
            // Determine overall status
            if ("CRITICAL".equals(perfStatus) || "DOWN".equals(cacheStatus) || "DOWN".equals(dbStatus)) {
                return "DOWN";
            } else if ("WARNING".equals(perfStatus)) {
                return "WARNING";
            } else if ("HEALTHY".equals(perfStatus) && "UP".equals(cacheStatus) && "UP".equals(dbStatus)) {
                return "UP";
            } else {
                return "UNKNOWN";
            }
            
        } catch (Exception e) {
            log.error("Error determining overall health: {}", e.getMessage());
            return "UNKNOWN";
        }
    }
}
