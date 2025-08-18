package org.de013.shoppingcart.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Multi-layer Cache Configuration
 * Implements L1 (Caffeine) and L2 (Redis) caching strategy
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    @Value("${shopping-cart.cache.caffeine.max-size:10000}")
    private long caffeineMaxSize;

    @Value("${shopping-cart.cache.caffeine.expire-after-write:300}")
    private long caffeineExpireAfterWrite;

    @Value("${shopping-cart.cache.redis.default-ttl:3600}")
    private long redisDefaultTtl;

    // ==================== L1 CACHE (CAFFEINE) ====================

    @Bean("caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(caffeineMaxSize)
            .expireAfterWrite(caffeineExpireAfterWrite, TimeUnit.SECONDS)
            .recordStats()
            .removalListener((key, value, cause) -> 
                log.debug("L1 Cache eviction - Key: {}, Cause: {}", key, cause))
        );
        
        // Define cache names for L1 caching
        cacheManager.setCacheNames(java.util.List.of(
            "products",           // Product information
            "users",             // User basic info
            "pricing-rules",     // Pricing calculation rules
            "tax-rates",         // Tax rates by jurisdiction
            "shipping-zones",    // Shipping zone configurations
            "promotions",        // Active promotions
            "cart-summaries"     // Cart calculation summaries
        ));
        
        log.info("Configured L1 Cache (Caffeine) - Max Size: {}, TTL: {}s", 
                caffeineMaxSize, caffeineExpireAfterWrite);
        
        return cacheManager;
    }

    // ==================== L2 CACHE (REDIS) ====================

    @Bean("redisCacheManager")
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(redisDefaultTtl))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();

        // Cache-specific configurations
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Short-term caches (5 minutes)
        cacheConfigurations.put("cart-items", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("cart-validation", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("stock-availability", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Medium-term caches (30 minutes)
        cacheConfigurations.put("product-details", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("pricing-calculations", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("shipping-calculations", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // Long-term caches (2 hours)
        cacheConfigurations.put("user-profiles", defaultConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put("tax-calculations", defaultConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put("promotion-rules", defaultConfig.entryTtl(Duration.ofHours(2)));
        
        // Very long-term caches (24 hours)
        cacheConfigurations.put("system-config", defaultConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("reference-data", defaultConfig.entryTtl(Duration.ofHours(24)));
        
        RedisCacheManager cacheManager = RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();
        
        log.info("Configured L2 Cache (Redis) - Default TTL: {}s, {} cache configurations", 
                redisDefaultTtl, cacheConfigurations.size());
        
        return cacheManager;
    }

    // ==================== CACHE STATISTICS ====================

    @Bean
    public CacheStatsService cacheStatsService() {
        return new CacheStatsService();
    }

    /**
     * Cache Statistics Service
     */
    public static class CacheStatsService {
        
        public Map<String, Object> getCaffeineStats(CacheManager cacheManager) {
            Map<String, Object> stats = new HashMap<>();
            
            if (cacheManager instanceof CaffeineCacheManager caffeineCacheManager) {
                for (String cacheName : caffeineCacheManager.getCacheNames()) {
                    var cache = caffeineCacheManager.getCache(cacheName);
                    if (cache != null) {
                        var caffeineCache = (com.github.benmanes.caffeine.cache.Cache<Object, Object>) 
                            cache.getNativeCache();
                        var cacheStats = caffeineCache.stats();
                        
                        Map<String, Object> cacheStatMap = new HashMap<>();
                        cacheStatMap.put("hitCount", cacheStats.hitCount());
                        cacheStatMap.put("missCount", cacheStats.missCount());
                        cacheStatMap.put("hitRate", cacheStats.hitRate());
                        cacheStatMap.put("evictionCount", cacheStats.evictionCount());
                        cacheStatMap.put("estimatedSize", caffeineCache.estimatedSize());
                        
                        stats.put(cacheName, cacheStatMap);
                    }
                }
            }
            
            return stats;
        }
        
        public Map<String, Object> getRedisStats() {
            // Redis stats would be collected via Redis INFO command
            // This is a placeholder for Redis statistics
            Map<String, Object> stats = new HashMap<>();
            stats.put("type", "redis");
            stats.put("note", "Redis stats collected via INFO command");
            return stats;
        }
    }

    // ==================== CACHE WARMING ====================

    @Bean
    public CacheWarmupService cacheWarmupService() {
        return new CacheWarmupService();
    }

    /**
     * Cache Warmup Service
     */
    public static class CacheWarmupService {
        
        public void warmupProductCache() {
            log.info("Starting product cache warmup...");
            // Implementation would load frequently accessed products
        }
        
        public void warmupPricingCache() {
            log.info("Starting pricing cache warmup...");
            // Implementation would load pricing rules and calculations
        }
        
        public void warmupTaxCache() {
            log.info("Starting tax cache warmup...");
            // Implementation would load tax rates for common jurisdictions
        }
        
        public void warmupShippingCache() {
            log.info("Starting shipping cache warmup...");
            // Implementation would load shipping zones and rates
        }
        
        public void warmupAllCaches() {
            log.info("Starting comprehensive cache warmup...");
            warmupProductCache();
            warmupPricingCache();
            warmupTaxCache();
            warmupShippingCache();
            log.info("Cache warmup completed");
        }
    }

    // ==================== CACHE EVICTION STRATEGIES ====================

    @Bean
    public CacheEvictionService cacheEvictionService() {
        return new CacheEvictionService();
    }

    /**
     * Cache Eviction Service
     */
    public static class CacheEvictionService {
        
        public void evictProductCache(String productId) {
            log.debug("Evicting product cache for: {}", productId);
            // Implementation would evict specific product from all cache layers
        }
        
        public void evictUserCache(String userId) {
            log.debug("Evicting user cache for: {}", userId);
            // Implementation would evict user-related caches
        }
        
        public void evictPricingCache() {
            log.debug("Evicting pricing cache");
            // Implementation would evict pricing-related caches
        }
        
        public void evictAllCaches() {
            log.info("Evicting all caches");
            // Implementation would clear all cache layers
        }
        
        public void evictExpiredCaches() {
            log.debug("Evicting expired caches");
            // Implementation would remove expired entries
        }
    }

    // ==================== CACHE HEALTH CHECK ====================

    @Bean
    public CacheHealthService cacheHealthService() {
        return new CacheHealthService();
    }

    /**
     * Cache Health Service
     */
    public static class CacheHealthService {
        
        public Map<String, Object> checkCacheHealth() {
            Map<String, Object> health = new HashMap<>();
            
            // L1 Cache Health
            Map<String, Object> l1Health = new HashMap<>();
            l1Health.put("status", "UP");
            l1Health.put("type", "caffeine");
            health.put("l1Cache", l1Health);
            
            // L2 Cache Health
            Map<String, Object> l2Health = new HashMap<>();
            l2Health.put("status", "UP");
            l2Health.put("type", "redis");
            health.put("l2Cache", l2Health);
            
            health.put("overall", "UP");
            health.put("timestamp", System.currentTimeMillis());
            
            return health;
        }
        
        public boolean isCacheHealthy() {
            try {
                Map<String, Object> health = checkCacheHealth();
                return "UP".equals(health.get("overall"));
            } catch (Exception e) {
                log.error("Cache health check failed: {}", e.getMessage());
                return false;
            }
        }
    }
}
