package org.de013.shoppingcart.repository.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.entity.RedisCart;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.entity.enums.CartType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Custom Redis operations for cart management
 * Provides advanced Redis operations with TTL management and session handling
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisCartOperations {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis key prefixes
    private static final String CART_PREFIX = "cart:";
    private static final String USER_CART_PREFIX = "user_cart:";
    private static final String SESSION_CART_PREFIX = "session_cart:";
    private static final String CART_LOCK_PREFIX = "cart_lock:";
    private static final String CART_ACTIVITY_PREFIX = "cart_activity:";
    private static final String CART_METRICS_PREFIX = "cart_metrics:";

    // ==================== BASIC CART OPERATIONS ====================

    /**
     * Save cart with custom TTL
     */
    public void saveCartWithTTL(RedisCart cart, Duration ttl) {
        try {
            String key = generateCartKey(cart);
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            
            // Save cart data
            valueOps.set(key, cart, ttl);
            
            // Create index entries
            createIndexEntries(cart, ttl);
            
            // Update activity timestamp
            updateLastActivity(cart.getId());
            
            log.debug("Saved cart {} with TTL {}", key, ttl);
        } catch (Exception e) {
            log.error("Error saving cart to Redis: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save cart to Redis", e);
        }
    }

    /**
     * Get cart by key
     */
    public Optional<RedisCart> getCart(String key) {
        try {
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            Object cartData = valueOps.get(key);
            
            if (cartData == null) {
                return Optional.empty();
            }
            
            RedisCart cart = convertToRedisCart(cartData);
            
            // Update last activity
            if (cart != null) {
                updateLastActivity(cart.getId());
            }
            
            return Optional.ofNullable(cart);
        } catch (Exception e) {
            log.error("Error getting cart from Redis: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Get cart by user ID
     */
    public Optional<RedisCart> getCartByUserId(String userId) {
        String key = USER_CART_PREFIX + userId;
        return getCart(key);
    }

    /**
     * Get cart by session ID
     */
    public Optional<RedisCart> getCartBySessionId(String sessionId) {
        String key = SESSION_CART_PREFIX + sessionId;
        return getCart(key);
    }

    /**
     * Delete cart and all related data
     */
    public boolean deleteCart(String cartKey) {
        try {
            // Get cart first to clean up indexes
            Optional<RedisCart> cartOpt = getCart(cartKey);
            
            // Delete main cart data
            Boolean deleted = redisTemplate.delete(cartKey);
            
            // Clean up indexes if cart existed
            if (cartOpt.isPresent()) {
                cleanupIndexEntries(cartOpt.get());
            }
            
            log.debug("Deleted cart: {}", cartKey);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.error("Error deleting cart from Redis: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== TTL MANAGEMENT ====================

    /**
     * Update cart TTL
     */
    public boolean updateCartTTL(String cartKey, Duration ttl) {
        try {
            Boolean result = redisTemplate.expire(cartKey, ttl);
            log.debug("Updated TTL for cart {} to {}", cartKey, ttl);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Error updating cart TTL: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get cart TTL
     */
    public Duration getCartTTL(String cartKey) {
        try {
            Long ttlSeconds = redisTemplate.getExpire(cartKey, TimeUnit.SECONDS);
            if (ttlSeconds == null || ttlSeconds < 0) {
                return Duration.ZERO;
            }
            return Duration.ofSeconds(ttlSeconds);
        } catch (Exception e) {
            log.error("Error getting cart TTL: {}", e.getMessage(), e);
            return Duration.ZERO;
        }
    }

    /**
     * Extend cart TTL based on cart type
     */
    public boolean extendCartTTL(String cartKey, CartType cartType) {
        Duration newTTL = Duration.ofSeconds(cartType.getDefaultTtlSeconds());
        return updateCartTTL(cartKey, newTTL);
    }

    // ==================== CART LOCKING ====================

    /**
     * Acquire cart lock for atomic operations
     */
    public boolean acquireCartLock(String cartId, Duration lockDuration) {
        try {
            String lockKey = CART_LOCK_PREFIX + cartId;
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            
            Boolean acquired = valueOps.setIfAbsent(lockKey, "locked", lockDuration);
            
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Acquired lock for cart: {}", cartId);
            }
            
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.error("Error acquiring cart lock: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Release cart lock
     */
    public boolean releaseCartLock(String cartId) {
        try {
            String lockKey = CART_LOCK_PREFIX + cartId;
            Boolean released = redisTemplate.delete(lockKey);
            
            if (Boolean.TRUE.equals(released)) {
                log.debug("Released lock for cart: {}", cartId);
            }
            
            return Boolean.TRUE.equals(released);
        } catch (Exception e) {
            log.error("Error releasing cart lock: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== ACTIVITY TRACKING ====================

    /**
     * Update last activity timestamp
     */
    public void updateLastActivity(String cartId) {
        try {
            String activityKey = CART_ACTIVITY_PREFIX + cartId;
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            
            valueOps.set(activityKey, LocalDateTime.now().toString(), Duration.ofHours(24));
        } catch (Exception e) {
            log.error("Error updating cart activity: {}", e.getMessage(), e);
        }
    }

    /**
     * Get last activity timestamp
     */
    public Optional<LocalDateTime> getLastActivity(String cartId) {
        try {
            String activityKey = CART_ACTIVITY_PREFIX + cartId;
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            
            Object activity = valueOps.get(activityKey);
            if (activity != null) {
                return Optional.of(LocalDateTime.parse(activity.toString()));
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error getting cart activity: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ==================== BULK OPERATIONS ====================

    /**
     * Get multiple carts by keys
     */
    public Map<String, RedisCart> getMultipleCarts(Set<String> cartKeys) {
        try {
            ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
            List<Object> cartDataList = valueOps.multiGet(cartKeys);
            
            Map<String, RedisCart> result = new HashMap<>();
            List<String> keysList = new ArrayList<>(cartKeys);
            
            for (int i = 0; i < keysList.size(); i++) {
                Object cartData = cartDataList.get(i);
                if (cartData != null) {
                    RedisCart cart = convertToRedisCart(cartData);
                    if (cart != null) {
                        result.put(keysList.get(i), cart);
                    }
                }
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error getting multiple carts: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Delete multiple carts
     */
    public long deleteMultipleCarts(Set<String> cartKeys) {
        try {
            Long deletedCount = redisTemplate.delete(cartKeys);
            log.debug("Deleted {} carts", deletedCount);
            return deletedCount != null ? deletedCount : 0;
        } catch (Exception e) {
            log.error("Error deleting multiple carts: {}", e.getMessage(), e);
            return 0;
        }
    }

    // ==================== SEARCH & FILTERING ====================

    /**
     * Find carts by pattern
     */
    public Set<String> findCartKeysByPattern(String pattern) {
        try {
            return redisTemplate.keys(pattern);
        } catch (Exception e) {
            log.error("Error finding cart keys by pattern: {}", e.getMessage(), e);
            return new HashSet<>();
        }
    }

    /**
     * Find expired carts
     */
    public Set<String> findExpiredCarts() {
        try {
            Set<String> allCartKeys = findCartKeysByPattern(CART_PREFIX + "*");
            
            return allCartKeys.stream()
                    .filter(key -> {
                        Duration ttl = getCartTTL(key);
                        return ttl.isZero() || ttl.isNegative();
                    })
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("Error finding expired carts: {}", e.getMessage(), e);
            return new HashSet<>();
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generate cart key based on cart data
     */
    private String generateCartKey(RedisCart cart) {
        if (cart.getUserId() != null) {
            return USER_CART_PREFIX + cart.getUserId();
        } else if (cart.getSessionId() != null) {
            return SESSION_CART_PREFIX + cart.getSessionId();
        } else {
            return CART_PREFIX + cart.getCartId();
        }
    }

    /**
     * Create index entries for fast lookups
     */
    private void createIndexEntries(RedisCart cart, Duration ttl) {
        try {
            SetOperations<String, Object> setOps = redisTemplate.opsForSet();
            
            // Index by status
            if (cart.getStatus() != null) {
                String statusKey = "cart_status:" + cart.getStatus();
                setOps.add(statusKey, cart.getId());
                redisTemplate.expire(statusKey, ttl.plusHours(1));
            }
            
            // Index by cart type
            if (cart.getCartType() != null) {
                String typeKey = "cart_type:" + cart.getCartType();
                setOps.add(typeKey, cart.getId());
                redisTemplate.expire(typeKey, ttl.plusHours(1));
            }
        } catch (Exception e) {
            log.error("Error creating index entries: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean up index entries
     */
    private void cleanupIndexEntries(RedisCart cart) {
        try {
            SetOperations<String, Object> setOps = redisTemplate.opsForSet();
            
            // Remove from status index
            if (cart.getStatus() != null) {
                String statusKey = "cart_status:" + cart.getStatus();
                setOps.remove(statusKey, cart.getId());
            }
            
            // Remove from type index
            if (cart.getCartType() != null) {
                String typeKey = "cart_type:" + cart.getCartType();
                setOps.remove(typeKey, cart.getId());
            }
        } catch (Exception e) {
            log.error("Error cleaning up index entries: {}", e.getMessage(), e);
        }
    }

    /**
     * Convert object to RedisCart
     */
    private RedisCart convertToRedisCart(Object cartData) {
        try {
            if (cartData instanceof RedisCart) {
                return (RedisCart) cartData;
            } else if (cartData instanceof String) {
                return objectMapper.readValue((String) cartData, RedisCart.class);
            } else {
                String json = objectMapper.writeValueAsString(cartData);
                return objectMapper.readValue(json, RedisCart.class);
            }
        } catch (JsonProcessingException e) {
            log.error("Error converting to RedisCart: {}", e.getMessage(), e);
            return null;
        }
    }
}
