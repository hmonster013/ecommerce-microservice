package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis Health Check Service
 * Monitors Redis connectivity and provides fallback strategies
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisHealthService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConnectionFactory connectionFactory;

    private volatile boolean redisAvailable = true;
    private volatile LocalDateTime lastHealthCheck = LocalDateTime.now();
    private volatile String lastError = null;

    // Health check constants
    private static final String HEALTH_CHECK_KEY = "health_check";
    private static final String HEALTH_CHECK_VALUE = "ping";
    private static final int HEALTH_CHECK_TIMEOUT_MS = 1000;

    // ==================== HEALTH CHECK OPERATIONS ====================

    /**
     * Check if Redis is available
     */
    public boolean isRedisAvailable() {
        return redisAvailable;
    }

    /**
     * Perform comprehensive Redis health check
     */
    public Map<String, Object> performHealthCheck() {
        Map<String, Object> healthStatus = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Test basic connectivity
            boolean connectionTest = testConnection();
            
            // Test read/write operations
            boolean readWriteTest = testReadWrite();
            
            // Test pipeline operations
            boolean pipelineTest = testPipeline();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Update availability status
            boolean isHealthy = connectionTest && readWriteTest && pipelineTest;
            updateAvailabilityStatus(isHealthy, null);
            
            // Build health status response
            healthStatus.put("available", isHealthy);
            healthStatus.put("responseTimeMs", responseTime);
            healthStatus.put("connectionTest", connectionTest);
            healthStatus.put("readWriteTest", readWriteTest);
            healthStatus.put("pipelineTest", pipelineTest);
            healthStatus.put("lastCheckTime", LocalDateTime.now());
            healthStatus.put("lastError", lastError);
            
            // Get Redis info
            healthStatus.putAll(getRedisInfo());
            
            log.debug("Redis health check completed: available={}, responseTime={}ms", 
                     isHealthy, responseTime);
            
        } catch (Exception e) {
            log.error("Error performing Redis health check: {}", e.getMessage(), e);
            updateAvailabilityStatus(false, e.getMessage());
            
            healthStatus.put("available", false);
            healthStatus.put("error", e.getMessage());
            healthStatus.put("lastCheckTime", LocalDateTime.now());
        }
        
        return healthStatus;
    }

    /**
     * Test basic Redis connection
     */
    private boolean testConnection() {
        try {
            RedisConnection connection = connectionFactory.getConnection();
            String pong = connection.ping();
            connection.close();
            
            return "PONG".equals(pong);
            
        } catch (Exception e) {
            log.warn("Redis connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Test Redis read/write operations
     */
    private boolean testReadWrite() {
        try {
            String testKey = HEALTH_CHECK_KEY + ":" + System.currentTimeMillis();
            String testValue = HEALTH_CHECK_VALUE;
            
            // Write test
            redisTemplate.opsForValue().set(testKey, testValue, 10, TimeUnit.SECONDS);
            
            // Read test
            Object retrievedValue = redisTemplate.opsForValue().get(testKey);
            
            // Cleanup
            redisTemplate.delete(testKey);
            
            return testValue.equals(retrievedValue);
            
        } catch (Exception e) {
            log.warn("Redis read/write test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Test Redis pipeline operations
     */
    private boolean testPipeline() {
        try {
            String testKey = HEALTH_CHECK_KEY + ":pipeline:" + System.currentTimeMillis();
            
            // Execute pipeline operations
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                connection.set(testKey.getBytes(), "test1".getBytes());
                connection.set((testKey + ":2").getBytes(), "test2".getBytes());
                connection.del(testKey.getBytes());
                connection.del((testKey + ":2").getBytes());
                return null;
            });
            
            return true;
            
        } catch (Exception e) {
            log.warn("Redis pipeline test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get Redis server information
     */
    private Map<String, Object> getRedisInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            RedisConnection connection = connectionFactory.getConnection();
            
            // Get basic server info
            info.put("serverInfo", connection.info("server"));
            info.put("memoryInfo", connection.info("memory"));
            info.put("clientsInfo", connection.info("clients"));
            
            connection.close();
            
        } catch (Exception e) {
            log.warn("Error getting Redis info: {}", e.getMessage());
            info.put("infoError", e.getMessage());
        }
        
        return info;
    }

    // ==================== AVAILABILITY MANAGEMENT ====================

    /**
     * Update Redis availability status
     */
    private void updateAvailabilityStatus(boolean available, String error) {
        boolean previousStatus = this.redisAvailable;
        this.redisAvailable = available;
        this.lastHealthCheck = LocalDateTime.now();
        this.lastError = error;
        
        // Log status changes
        if (previousStatus != available) {
            if (available) {
                log.info("Redis is now available");
            } else {
                log.warn("Redis is now unavailable: {}", error);
            }
        }
    }

    /**
     * Get last health check time
     */
    public LocalDateTime getLastHealthCheckTime() {
        return lastHealthCheck;
    }

    /**
     * Get last error message
     */
    public String getLastError() {
        return lastError;
    }

    // ==================== FALLBACK STRATEGIES ====================

    /**
     * Execute operation with Redis fallback
     */
    public <T> T executeWithFallback(RedisOperation<T> operation, T fallbackValue) {
        try {
            if (!redisAvailable) {
                log.debug("Redis unavailable, using fallback value");
                return fallbackValue;
            }
            
            return operation.execute();
            
        } catch (Exception e) {
            log.warn("Redis operation failed, using fallback: {}", e.getMessage());
            updateAvailabilityStatus(false, e.getMessage());
            return fallbackValue;
        }
    }

    /**
     * Execute operation with Redis fallback and callback
     */
    public <T> T executeWithFallback(RedisOperation<T> operation, FallbackCallback<T> fallbackCallback) {
        try {
            if (!redisAvailable) {
                log.debug("Redis unavailable, executing fallback callback");
                return fallbackCallback.execute();
            }
            
            return operation.execute();
            
        } catch (Exception e) {
            log.warn("Redis operation failed, executing fallback callback: {}", e.getMessage());
            updateAvailabilityStatus(false, e.getMessage());
            return fallbackCallback.execute();
        }
    }

    // ==================== FUNCTIONAL INTERFACES ====================

    @FunctionalInterface
    public interface RedisOperation<T> {
        T execute() throws Exception;
    }

    @FunctionalInterface
    public interface FallbackCallback<T> {
        T execute();
    }

    // ==================== MONITORING METHODS ====================

    /**
     * Get Redis connection pool statistics
     */
    public Map<String, Object> getConnectionPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // This would depend on the specific connection pool implementation
            // For Lettuce, we'd need to access the pool statistics
            stats.put("poolType", "Lettuce");
            stats.put("timestamp", LocalDateTime.now());
            
            // Add more specific pool statistics here
            
        } catch (Exception e) {
            log.warn("Error getting connection pool stats: {}", e.getMessage());
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    /**
     * Get Redis performance metrics
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            long startTime = System.nanoTime();
            
            // Test operation latency
            redisTemplate.opsForValue().get("test_key");
            
            long latencyNs = System.nanoTime() - startTime;
            double latencyMs = latencyNs / 1_000_000.0;
            
            metrics.put("operationLatencyMs", latencyMs);
            metrics.put("available", redisAvailable);
            metrics.put("lastHealthCheck", lastHealthCheck);
            
        } catch (Exception e) {
            log.warn("Error getting performance metrics: {}", e.getMessage());
            metrics.put("error", e.getMessage());
        }
        
        return metrics;
    }
}
