package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Cache Invalidation Service
 * Handles intelligent cache invalidation strategies for shopping cart data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationService {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;

    // Cache names
    private static final String ACTIVE_CARTS_CACHE = "active-carts";
    private static final String GUEST_CARTS_CACHE = "guest-carts";
    private static final String SESSION_CARTS_CACHE = "session-carts";
    private static final String PRODUCT_INFO_CACHE = "product-info";
    private static final String CART_VALIDATION_CACHE = "cart-validation";

    // ==================== CART-SPECIFIC INVALIDATION ====================

    /**
     * Invalidate all caches related to a specific cart
     */
    public void invalidateCartCaches(Long cartId) {
        try {
            log.debug("Invalidating caches for cart: {}", cartId);
            
            CompletableFuture.runAsync(() -> {
                // Invalidate cart-specific caches
                evictFromCache(ACTIVE_CARTS_CACHE, cartId.toString());
                evictFromCache(CART_VALIDATION_CACHE, cartId.toString());
                
                // Invalidate related Redis keys
                invalidateRedisCartKeys(cartId);
            });
            
        } catch (Exception e) {
            log.error("Error invalidating cart caches for cart {}: {}", cartId, e.getMessage(), e);
        }
    }

    /**
     * Invalidate caches for a specific user
     */
    public void invalidateUserCaches(String userId) {
        try {
            log.debug("Invalidating caches for user: {}", userId);
            
            CompletableFuture.runAsync(() -> {
                // Find and invalidate user's cart caches
                String userCartPattern = "user_cart:" + userId;
                Set<String> userCartKeys = redisTemplate.keys(userCartPattern + "*");
                
                if (userCartKeys != null && !userCartKeys.isEmpty()) {
                    redisTemplate.delete(userCartKeys);
                    log.debug("Invalidated {} user cart keys for user: {}", userCartKeys.size(), userId);
                }
                
                // Invalidate user-specific validation caches
                String validationPattern = "cart-validation::user:" + userId + "*";
                Set<String> validationKeys = redisTemplate.keys(validationPattern);
                if (validationKeys != null && !validationKeys.isEmpty()) {
                    redisTemplate.delete(validationKeys);
                }
            });
            
        } catch (Exception e) {
            log.error("Error invalidating user caches for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Invalidate caches for a specific session
     */
    public void invalidateSessionCaches(String sessionId) {
        try {
            log.debug("Invalidating caches for session: {}", sessionId);
            
            CompletableFuture.runAsync(() -> {
                // Find and invalidate session cart caches
                String sessionCartPattern = "session_cart:" + sessionId;
                Set<String> sessionCartKeys = redisTemplate.keys(sessionCartPattern + "*");
                
                if (sessionCartKeys != null && !sessionCartKeys.isEmpty()) {
                    redisTemplate.delete(sessionCartKeys);
                    log.debug("Invalidated {} session cart keys for session: {}", sessionCartKeys.size(), sessionId);
                }
                
                // Invalidate from Spring Cache
                evictFromCache(SESSION_CARTS_CACHE, sessionId);
            });
            
        } catch (Exception e) {
            log.error("Error invalidating session caches for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    // ==================== PRODUCT-SPECIFIC INVALIDATION ====================

    /**
     * Invalidate product-related caches when product information changes
     */
    public void invalidateProductCaches(String productId) {
        try {
            log.debug("Invalidating product caches for product: {}", productId);
            
            CompletableFuture.runAsync(() -> {
                // Invalidate product info cache
                evictFromCache(PRODUCT_INFO_CACHE, productId);
                
                // Find and invalidate carts containing this product
                invalidateCartsContainingProduct(productId);
            });
            
        } catch (Exception e) {
            log.error("Error invalidating product caches for product {}: {}", productId, e.getMessage(), e);
        }
    }

    /**
     * Invalidate multiple product caches
     */
    public void invalidateProductCaches(List<String> productIds) {
        try {
            log.debug("Invalidating product caches for {} products", productIds.size());
            
            CompletableFuture.runAsync(() -> {
                for (String productId : productIds) {
                    evictFromCache(PRODUCT_INFO_CACHE, productId);
                    invalidateCartsContainingProduct(productId);
                }
            });
            
        } catch (Exception e) {
            log.error("Error invalidating multiple product caches: {}", e.getMessage(), e);
        }
    }

    // ==================== BULK INVALIDATION ====================

    /**
     * Invalidate all cart-related caches
     */
    public void invalidateAllCartCaches() {
        try {
            log.info("Invalidating all cart-related caches");
            
            CompletableFuture.runAsync(() -> {
                // Clear Spring Cache caches
                clearCache(ACTIVE_CARTS_CACHE);
                clearCache(GUEST_CARTS_CACHE);
                clearCache(SESSION_CARTS_CACHE);
                clearCache(CART_VALIDATION_CACHE);
                
                // Clear Redis cart keys
                clearRedisCartKeys();
            });
            
        } catch (Exception e) {
            log.error("Error invalidating all cart caches: {}", e.getMessage(), e);
        }
    }

    /**
     * Invalidate expired caches
     */
    public void invalidateExpiredCaches() {
        try {
            log.debug("Invalidating expired caches");
            
            CompletableFuture.runAsync(() -> {
                // Find and remove expired cart keys
                String expiredCartPattern = "cart:*";
                Set<String> cartKeys = redisTemplate.keys(expiredCartPattern);
                
                if (cartKeys != null) {
                    for (String key : cartKeys) {
                        Long ttl = redisTemplate.getExpire(key);
                        if (ttl != null && ttl <= 0) {
                            redisTemplate.delete(key);
                            log.debug("Removed expired cache key: {}", key);
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            log.error("Error invalidating expired caches: {}", e.getMessage(), e);
        }
    }

    // ==================== CACHE WARMING ====================

    /**
     * Warm up frequently accessed caches
     */
    public void warmUpCaches() {
        try {
            log.info("Warming up frequently accessed caches");
            
            CompletableFuture.runAsync(() -> {
                // This would typically pre-load frequently accessed data
                // For now, just log the operation
                log.debug("Cache warm-up completed");
            });
            
        } catch (Exception e) {
            log.error("Error warming up caches: {}", e.getMessage(), e);
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Evict specific key from cache
     */
    private void evictFromCache(String cacheName, String key) {
        try {
            if (cacheManager.getCache(cacheName) != null) {
                cacheManager.getCache(cacheName).evict(key);
                log.debug("Evicted key '{}' from cache '{}'", key, cacheName);
            }
        } catch (Exception e) {
            log.warn("Error evicting key '{}' from cache '{}': {}", key, cacheName, e.getMessage());
        }
    }

    /**
     * Clear entire cache
     */
    private void clearCache(String cacheName) {
        try {
            if (cacheManager.getCache(cacheName) != null) {
                cacheManager.getCache(cacheName).clear();
                log.debug("Cleared cache: {}", cacheName);
            }
        } catch (Exception e) {
            log.warn("Error clearing cache '{}': {}", cacheName, e.getMessage());
        }
    }

    /**
     * Invalidate Redis keys for a specific cart
     */
    private void invalidateRedisCartKeys(Long cartId) {
        try {
            String cartPattern = "cart:" + cartId + "*";
            Set<String> cartKeys = redisTemplate.keys(cartPattern);
            
            if (cartKeys != null && !cartKeys.isEmpty()) {
                redisTemplate.delete(cartKeys);
                log.debug("Invalidated {} Redis keys for cart: {}", cartKeys.size(), cartId);
            }
        } catch (Exception e) {
            log.warn("Error invalidating Redis cart keys for cart {}: {}", cartId, e.getMessage());
        }
    }

    /**
     * Invalidate carts containing a specific product
     */
    private void invalidateCartsContainingProduct(String productId) {
        try {
            // This would require a more sophisticated approach in production
            // For now, we'll invalidate validation caches that might be affected
            String validationPattern = "cart-validation::*product:" + productId + "*";
            Set<String> validationKeys = redisTemplate.keys(validationPattern);
            
            if (validationKeys != null && !validationKeys.isEmpty()) {
                redisTemplate.delete(validationKeys);
                log.debug("Invalidated {} validation keys for product: {}", validationKeys.size(), productId);
            }
        } catch (Exception e) {
            log.warn("Error invalidating carts containing product {}: {}", productId, e.getMessage());
        }
    }

    /**
     * Clear all Redis cart keys
     */
    private void clearRedisCartKeys() {
        try {
            String[] patterns = {
                "cart:*",
                "user_cart:*",
                "session_cart:*",
                "cart_lock:*",
                "cart_activity:*",
                "cart_metrics:*"
            };
            
            for (String pattern : patterns) {
                Set<String> keys = redisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                    log.debug("Cleared {} keys matching pattern: {}", keys.size(), pattern);
                }
            }
        } catch (Exception e) {
            log.warn("Error clearing Redis cart keys: {}", e.getMessage());
        }
    }
}
