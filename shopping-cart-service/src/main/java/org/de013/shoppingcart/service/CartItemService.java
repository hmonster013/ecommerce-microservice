package org.de013.shoppingcart.service;

import org.de013.shoppingcart.dto.request.AddToCartDto;
import org.de013.shoppingcart.dto.request.GiftOptionsDto;

import org.de013.shoppingcart.dto.request.UpdateCartItemDto;
import org.de013.shoppingcart.dto.response.CartItemResponseDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Cart Item Service Interface
 * Handles cart item operations including add, update, remove, and validation
 */
public interface CartItemService {

    // ==================== ITEM ADDITION ====================

    /**
     * Add item to cart
     */
    CartItemResponseDto addItemToCart(Long cartId, AddToCartDto request);

    // ==================== ITEM UPDATES ====================

    /**
     * Update cart item
     */
    CartItemResponseDto updateCartItem(Long itemId, UpdateCartItemDto request);

    /**
     * Update gift options for cart item
     */
    CartItemResponseDto updateGiftOptions(Long itemId, GiftOptionsDto giftOptions);

    // ==================== ITEM REMOVAL ====================

    /**
     * Remove cart item by ID
     */
    boolean removeCartItem(Long itemId);

    // ==================== CART CALCULATIONS ====================

    /**
     * Calculate cart subtotal
     */
    BigDecimal calculateCartSubtotal(Long cartId);

    /**
     * Get cart item count
     */
    int getCartItemCount(Long cartId);

    /**
     * Get cart total quantity
     */
    int getCartTotalQuantity(Long cartId);

    /**
     * Get cart items
     */
    List<CartItemResponseDto> getCartItems(Long cartId);

    /**
     * Get cart item by ID
     */
    Optional<CartItemResponseDto> getCartItemById(Long itemId);
}
