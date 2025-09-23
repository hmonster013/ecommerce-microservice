package org.de013.shoppingcart.service;

import org.de013.shoppingcart.dto.response.CartResponseDto;
import org.de013.shoppingcart.entity.Cart;

import java.util.List;

/**
 * Cart Merge Service Interface
 * Handles merging of guest carts to user carts during login and other merge scenarios
 */
public interface CartMergeService {

    // ==================== MAIN MERGE OPERATIONS ====================

    /**
     * Merge guest cart to user cart during login
     */
    CartResponseDto mergeGuestCartToUser(String sessionId, String userId);

    /**
     * Merge multiple carts for a user
     */
    CartResponseDto mergeUserCarts(String userId, List<Long> cartIdsToMerge, Long targetCartId);

    // ==================== MERGE VALIDATION ====================

    /**
     * Validate merge operation
     */
    boolean canMergeCarts(Cart sourceCart, Cart targetCart);
}
