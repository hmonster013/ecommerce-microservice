package org.de013.shoppingcart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.service.CacheFallbackService;
import org.de013.shoppingcart.service.CacheInvalidationService;
import org.de013.shoppingcart.service.RedisHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Redis Monitoring Controller
 * Provides endpoints for Redis health monitoring, cache management, and diagnostics
 */
@RestController
@RequestMapping("/api/v1/redis")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Redis Monitoring", description = "APIs for Redis health monitoring and cache management")
public class RedisMonitoringController {

    private final RedisHealthService redisHealthService;
    private final CacheInvalidationService cacheInvalidationService;
    private final CacheFallbackService cacheFallbackService;

    // ==================== HEALTH MONITORING ====================

    @Operation(summary = "Get Redis health status", description = "Get comprehensive Redis health and connectivity status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Health status retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getRedisHealth() {
        try {
            log.debug("Getting Redis health status");
            
            Map<String, Object> healthStatus = redisHealthService.performHealthCheck();
            return ResponseEntity.ok(healthStatus);
            
        } catch (Exception e) {
            log.error("Error getting Redis health: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Check Redis availability", description = "Quick check if Redis is available")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Availability status retrieved"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> getRedisAvailability() {
        try {
            boolean available = redisHealthService.isRedisAvailable();
            String lastError = redisHealthService.getLastError();
            
            Map<String, Object> status = Map.of(
                "available", available,
                "lastError", lastError != null ? lastError : "None",
                "lastHealthCheck", redisHealthService.getLastHealthCheckTime()
            );
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error getting Redis availability: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Redis performance metrics", description = "Get Redis performance and latency metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Performance metrics retrieved"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getRedisMetrics() {
        try {
            log.debug("Getting Redis performance metrics");
            
            Map<String, Object> metrics = redisHealthService.getPerformanceMetrics();
            Map<String, Object> poolStats = redisHealthService.getConnectionPoolStats();
            
            Map<String, Object> response = Map.of(
                "performance", metrics,
                "connectionPool", poolStats
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting Redis metrics: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    // ==================== CACHE MANAGEMENT ====================

    @Operation(summary = "Invalidate all cart caches", description = "Clear all cart-related caches")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Caches invalidated successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/cache/invalidate/all")
    public ResponseEntity<Map<String, Object>> invalidateAllCaches() {
        try {
            log.info("Invalidating all cart caches");
            
            cacheInvalidationService.invalidateAllCartCaches();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "All cart caches invalidated",
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error invalidating all caches: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Invalidate cart cache", description = "Invalidate cache for a specific cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart cache invalidated successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/cache/invalidate/cart/{cartId}")
    public ResponseEntity<Map<String, Object>> invalidateCartCache(@PathVariable Long cartId) {
        try {
            log.debug("Invalidating cache for cart: {}", cartId);
            
            cacheInvalidationService.invalidateCartCaches(cartId);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Cart cache invalidated",
                "cartId", cartId,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error invalidating cart cache for {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Invalidate user caches", description = "Invalidate all caches for a specific user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User caches invalidated successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/cache/invalidate/user/{userId}")
    public ResponseEntity<Map<String, Object>> invalidateUserCaches(@PathVariable String userId) {
        try {
            log.debug("Invalidating caches for user: {}", userId);
            
            cacheInvalidationService.invalidateUserCaches(userId);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "User caches invalidated",
                "userId", userId,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error invalidating user caches for {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Invalidate expired caches", description = "Clean up expired cache entries")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Expired caches cleaned up successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/cache/cleanup/expired")
    public ResponseEntity<Map<String, Object>> cleanupExpiredCaches() {
        try {
            log.info("Cleaning up expired caches");
            
            cacheInvalidationService.invalidateExpiredCaches();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Expired caches cleaned up",
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error cleaning up expired caches: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Warm up caches", description = "Pre-load frequently accessed data into cache")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cache warm-up initiated successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/cache/warmup")
    public ResponseEntity<Map<String, Object>> warmupCaches() {
        try {
            log.info("Initiating cache warm-up");
            
            cacheInvalidationService.warmUpCaches();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Cache warm-up initiated",
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error warming up caches: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    // ==================== RECOVERY OPERATIONS ====================

    @Operation(summary = "Recover carts to Redis", description = "Sync active carts from database to Redis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart recovery initiated successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/recovery/carts")
    public ResponseEntity<Map<String, Object>> recoverCartsToRedis() {
        try {
            log.info("Initiating cart recovery to Redis");
            
            cacheFallbackService.recoverCartsToRedis();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Cart recovery to Redis initiated",
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error initiating cart recovery: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Validate consistency", description = "Validate Redis-Database consistency")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Consistency validation initiated successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/validation/consistency")
    public ResponseEntity<Map<String, Object>> validateConsistency() {
        try {
            log.info("Initiating Redis-Database consistency validation");
            
            cacheFallbackService.validateConsistency();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Consistency validation initiated",
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error initiating consistency validation: {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }
}
