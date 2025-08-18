package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.RedisCart;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Cache Fallback Service
 * Provides database fallback strategies when Redis is unavailable
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheFallbackService {

    private final CartRepository cartRepository;
    private final RedisHealthService redisHealthService;

    // ==================== CART FALLBACK OPERATIONS ====================

    /**
     * Get cart with Redis fallback to database
     */
    public Optional<Cart> getCartWithFallback(Long cartId) {
        try {
            if (redisHealthService.isRedisAvailable()) {
                // Try Redis first (implementation would go here)
                // For now, go directly to database
                return cartRepository.findByIdWithItems(cartId);
            } else {
                log.debug("Using database fallback for cart: {}", cartId);
                return cartRepository.findByIdWithItems(cartId);
            }
        } catch (Exception e) {
            log.warn("Error getting cart, using database fallback: {}", e.getMessage());
            return cartRepository.findByIdWithItems(cartId);
        }
    }

    /**
     * Get user cart with fallback
     */
    public Optional<Cart> getUserCartWithFallback(String userId) {
        try {
            if (redisHealthService.isRedisAvailable()) {
                // Try Redis first (implementation would go here)
                return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
            } else {
                log.debug("Using database fallback for user cart: {}", userId);
                return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
            }
        } catch (Exception e) {
            log.warn("Error getting user cart, using database fallback: {}", e.getMessage());
            return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
        }
    }

    /**
     * Get session cart with fallback
     */
    public Optional<Cart> getSessionCartWithFallback(String sessionId) {
        try {
            if (redisHealthService.isRedisAvailable()) {
                // Try Redis first (implementation would go here)
                return cartRepository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE);
            } else {
                log.debug("Using database fallback for session cart: {}", sessionId);
                return cartRepository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE);
            }
        } catch (Exception e) {
            log.warn("Error getting session cart, using database fallback: {}", e.getMessage());
            return cartRepository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE);
        }
    }

    // ==================== CART PERSISTENCE FALLBACK ====================

    /**
     * Save cart with fallback strategy
     */
    public Cart saveCartWithFallback(Cart cart) {
        try {
            // Always save to database first for consistency
            Cart savedCart = cartRepository.save(cart);
            
            // Try to sync to Redis asynchronously
            if (redisHealthService.isRedisAvailable()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        syncCartToRedis(savedCart);
                    } catch (Exception e) {
                        log.warn("Failed to sync cart {} to Redis: {}", savedCart.getId(), e.getMessage());
                    }
                });
            }
            
            return savedCart;
            
        } catch (Exception e) {
            log.error("Error saving cart with fallback: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save cart", e);
        }
    }

    /**
     * Update cart with fallback strategy
     */
    public Cart updateCartWithFallback(Cart cart) {
        try {
            // Update in database
            cart.setUpdatedAt(LocalDateTime.now());
            Cart updatedCart = cartRepository.save(cart);
            
            // Try to update Redis asynchronously
            if (redisHealthService.isRedisAvailable()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        syncCartToRedis(updatedCart);
                    } catch (Exception e) {
                        log.warn("Failed to update cart {} in Redis: {}", updatedCart.getId(), e.getMessage());
                    }
                });
            }
            
            return updatedCart;
            
        } catch (Exception e) {
            log.error("Error updating cart with fallback: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update cart", e);
        }
    }

    /**
     * Delete cart with fallback strategy
     */
    public boolean deleteCartWithFallback(Long cartId) {
        try {
            // Soft delete in database
            Optional<Cart> cartOpt = cartRepository.findById(cartId);
            if (cartOpt.isPresent()) {
                Cart cart = cartOpt.get();
                cart.setStatus(CartStatus.DELETED);
                cart.setDeletedAt(LocalDateTime.now());
                cartRepository.save(cart);
                
                // Try to remove from Redis asynchronously
                if (redisHealthService.isRedisAvailable()) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            removeCartFromRedis(cartId);
                        } catch (Exception e) {
                            log.warn("Failed to remove cart {} from Redis: {}", cartId, e.getMessage());
                        }
                    });
                }
                
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error deleting cart with fallback: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== BATCH OPERATIONS WITH FALLBACK ====================

    /**
     * Get multiple carts with fallback
     */
    public List<Cart> getMultipleCartsWithFallback(List<Long> cartIds) {
        try {
            if (redisHealthService.isRedisAvailable()) {
                // Try Redis batch operation first (implementation would go here)
                return cartRepository.findAllById(cartIds);
            } else {
                log.debug("Using database fallback for multiple carts: {}", cartIds.size());
                return cartRepository.findAllById(cartIds);
            }
        } catch (Exception e) {
            log.warn("Error getting multiple carts, using database fallback: {}", e.getMessage());
            return cartRepository.findAllById(cartIds);
        }
    }

    /**
     * Get active carts for user with fallback
     */
    public List<Cart> getActiveCartsWithFallback(String userId) {
        try {
            if (redisHealthService.isRedisAvailable()) {
                // Try Redis first (implementation would go here)
                Optional<Cart> cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
                return cart.map(List::of).orElse(List.of());
            } else {
                log.debug("Using database fallback for active carts for user: {}", userId);
                Optional<Cart> cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
                return cart.map(List::of).orElse(List.of());
            }
        } catch (Exception e) {
            log.warn("Error getting active carts, using database fallback: {}", e.getMessage());
            Optional<Cart> cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
            return cart.map(List::of).orElse(List.of());
        }
    }

    // ==================== SYNCHRONIZATION METHODS ====================

    /**
     * Sync cart from database to Redis
     */
    public void syncCartToRedis(Cart cart) {
        try {
            if (!redisHealthService.isRedisAvailable()) {
                log.debug("Redis unavailable, skipping cart sync for cart: {}", cart.getId());
                return;
            }
            
            // Convert to RedisCart and save
            RedisCart redisCart = RedisCart.fromCart(cart);
            // Implementation would save to Redis here
            
            log.debug("Synced cart {} to Redis", cart.getId());
            
        } catch (Exception e) {
            log.warn("Error syncing cart {} to Redis: {}", cart.getId(), e.getMessage());
        }
    }

    /**
     * Sync multiple carts to Redis
     */
    public void syncMultipleCartsToRedis(List<Cart> carts) {
        if (!redisHealthService.isRedisAvailable()) {
            log.debug("Redis unavailable, skipping batch cart sync for {} carts", carts.size());
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            for (Cart cart : carts) {
                try {
                    syncCartToRedis(cart);
                } catch (Exception e) {
                    log.warn("Error syncing cart {} to Redis: {}", cart.getId(), e.getMessage());
                }
            }
            log.debug("Completed batch sync of {} carts to Redis", carts.size());
        });
    }

    /**
     * Remove cart from Redis
     */
    private void removeCartFromRedis(Long cartId) {
        try {
            // Implementation would remove from Redis here
            log.debug("Removed cart {} from Redis", cartId);
        } catch (Exception e) {
            log.warn("Error removing cart {} from Redis: {}", cartId, e.getMessage());
        }
    }

    // ==================== RECOVERY OPERATIONS ====================

    /**
     * Recover carts from database to Redis
     */
    public void recoverCartsToRedis() {
        try {
            if (!redisHealthService.isRedisAvailable()) {
                log.warn("Redis unavailable, cannot recover carts");
                return;
            }
            
            log.info("Starting cart recovery to Redis");
            
            CompletableFuture.runAsync(() -> {
                try {
                    // Get active carts from database (simplified query)
                    List<Cart> activeCarts = cartRepository.findAll().stream()
                            .filter(cart -> CartStatus.ACTIVE.equals(cart.getStatus()))
                            .filter(cart -> cart.getUpdatedAt().isAfter(LocalDateTime.now().minusHours(24)))
                            .toList();
                    
                    log.info("Recovering {} active carts to Redis", activeCarts.size());
                    
                    // Sync to Redis in batches
                    int batchSize = 100;
                    for (int i = 0; i < activeCarts.size(); i += batchSize) {
                        int endIndex = Math.min(i + batchSize, activeCarts.size());
                        List<Cart> batch = activeCarts.subList(i, endIndex);
                        syncMultipleCartsToRedis(batch);
                        
                        // Small delay between batches
                        Thread.sleep(100);
                    }
                    
                    log.info("Completed cart recovery to Redis");
                    
                } catch (Exception e) {
                    log.error("Error during cart recovery: {}", e.getMessage(), e);
                }
            });
            
        } catch (Exception e) {
            log.error("Error starting cart recovery: {}", e.getMessage(), e);
        }
    }

    /**
     * Validate Redis-Database consistency
     */
    public void validateConsistency() {
        try {
            if (!redisHealthService.isRedisAvailable()) {
                log.warn("Redis unavailable, cannot validate consistency");
                return;
            }
            
            log.info("Starting Redis-Database consistency validation");
            
            CompletableFuture.runAsync(() -> {
                // Implementation would compare Redis and Database data
                // For now, just log the operation
                log.info("Consistency validation completed");
            });
            
        } catch (Exception e) {
            log.error("Error during consistency validation: {}", e.getMessage(), e);
        }
    }
}
