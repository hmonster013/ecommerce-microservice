package org.de013.shoppingcart.service;

import org.de013.shoppingcart.entity.Cart;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Cart Expiration Management Service Interface
 * Handles cart expiration policies, cleanup, and lifecycle management
 */
public interface CartExpirationService {

    // ==================== EXPIRATION CALCULATION ====================

    /**
     * Calculate expiration time for a cart based on its type and status
     */
    LocalDateTime calculateExpirationTime(Cart cart);

    /**
     * Check if a cart is expired
     */
    boolean isCartExpired(Cart cart);

    /**
     * Get time until cart expires
     */
    Map<String, Object> getTimeUntilExpiration(Cart cart);

    // ==================== EXPIRATION PROCESSING ====================

    /**
     * Process expired carts
     */
    void processExpiredCarts();

    /**
     * Process individual expired cart
     */
    void processExpiredCart(Cart cart);

    // ==================== CART EXTENSION ====================

    /**
     * Extend cart expiration time
     */
    Map<String, Object> extendCartExpiration(Long cartId, int additionalHours);

    /**
     * Refresh cart activity and extend expiration
     */
    Map<String, Object> refreshCartActivity(Long cartId);

    // ==================== BULK OPERATIONS ====================

    /**
     * Get expiration status for multiple carts
     */
    CompletableFuture<Map<String, Object>> getMultipleCartExpirationStatus(List<Long> cartIds);

    // ==================== CLEANUP OPERATIONS ====================

    /**
     * Clean up old deleted carts
     */
    void cleanupOldDeletedCarts();

    // ==================== STATISTICS ====================

    /**
     * Get cart expiration statistics
     */
    Map<String, Object> getExpirationStatistics();
}
