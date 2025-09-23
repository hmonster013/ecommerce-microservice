package org.de013.shoppingcart.service;

import org.de013.shoppingcart.dto.request.ApplyCouponDto;
import org.de013.shoppingcart.dto.response.CartResponseDto;

import java.util.Optional;

/**
 * Core Cart Service Interface
 * Handles main cart operations including CRUD, session management, and cart lifecycle
 */
public interface CartService {

    // ==================== CART CREATION & RETRIEVAL ====================

    /**
     * Get or create cart for user/session
     */
    CartResponseDto getOrCreateCart(String userId, String sessionId);

    /**
     * Get active cart for user or session with Redis fallback
     */
    Optional<CartResponseDto> getActiveCart(String userId, String sessionId);

    /**
     * Create new cart
     */
    CartResponseDto createNewCart(String userId, String sessionId);

    /**
     * Get cart by ID
     */
    Optional<CartResponseDto> getCartById(Long cartId);

    // ==================== CART UPDATES ====================

    /**
     * Update cart totals
     */
    void updateCartTotals(Long cartId);

    /**
     * Update last activity
     */
    void updateLastActivity(Long cartId);

    // ==================== COUPON MANAGEMENT ====================

    /**
     * Apply coupon to cart
     */
    CartResponseDto applyCoupon(ApplyCouponDto request);

    /**
     * Remove coupon from cart
     */
    CartResponseDto removeCoupon(String userId, String sessionId);

    // ==================== CART LIFECYCLE ====================

    /**
     * Clear cart
     */
    CartResponseDto clearCart(String userId, String sessionId);

    /**
     * Delete cart
     */
    boolean deleteCart(String userId, String sessionId);
}
