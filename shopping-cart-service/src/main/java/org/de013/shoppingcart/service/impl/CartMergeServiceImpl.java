package org.de013.shoppingcart.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.dto.response.CartResponseDto;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.entity.enums.CartType;
import org.de013.shoppingcart.repository.jpa.CartItemRepository;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.de013.shoppingcart.repository.redis.RedisCartSessionManager;
import org.de013.shoppingcart.service.CartMergeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cart Merge Service Implementation
 * Handles merging of guest carts to user carts during login and other merge scenarios
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CartMergeServiceImpl implements CartMergeService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    private final RedisCartSessionManager sessionManager;

    // ==================== MAIN MERGE OPERATIONS ====================

    /**
     * Merge guest cart to user cart during login
     */
    @Override
    public CartResponseDto mergeGuestCartToUser(String sessionId, String userId) {
        try {
            log.debug("Merging guest cart from session {} to user {}", sessionId, userId);
            
            // Get guest cart
            Optional<Cart> guestCartOpt = cartRepository.findBySessionIdAndStatus(sessionId, CartStatus.ACTIVE);
            if (guestCartOpt.isEmpty()) {
                log.debug("No guest cart found for session: {}", sessionId);
                // Return existing user cart or create new one
                return getOrCreateUserCart(userId);
            }
            
            Cart guestCart = guestCartOpt.get();
            
            // Get existing user cart
            Optional<Cart> userCartOpt = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
            
            Cart resultCart;
            if (userCartOpt.isPresent()) {
                // Merge guest cart into existing user cart
                resultCart = mergeCartsInternal(guestCart, userCartOpt.get(), userId);
            } else {
                // Convert guest cart to user cart
                resultCart = convertGuestCartToUserCart(guestCart, userId);
            }
            
            // Update Redis session
            sessionManager.migrateGuestCartToUser(sessionId, userId);
            
            // Analytics removed for basic functionality
            
            log.info("Successfully merged guest cart {} to user cart {} for user {}", 
                    guestCart.getId(), resultCart.getId(), userId);
            
            return convertToResponseDto(resultCart);
            
        } catch (Exception e) {
            log.error("Error merging guest cart to user: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to merge guest cart to user", e);
        }
    }

    /**
     * Merge multiple carts for a user
     */
    @Override
    public CartResponseDto mergeUserCarts(String userId, List<Long> cartIdsToMerge, Long targetCartId) {
        try {
            log.debug("Merging carts {} into target cart {} for user {}", cartIdsToMerge, targetCartId, userId);
            
            // Get target cart
            Optional<Cart> targetCartOpt = cartRepository.findByIdWithItems(targetCartId);
            if (targetCartOpt.isEmpty()) {
                throw new RuntimeException("Target cart not found");
            }
            
            Cart targetCart = targetCartOpt.get();
            
            // Validate target cart belongs to user
            if (!userId.equals(targetCart.getUserId())) {
                throw new RuntimeException("Target cart does not belong to user");
            }
            
            // Get source carts
            List<Cart> sourceCarts = cartRepository.findAllById(cartIdsToMerge);
            
            // Validate all source carts belong to user
            for (Cart sourceCart : sourceCarts) {
                if (!userId.equals(sourceCart.getUserId())) {
                    throw new RuntimeException("Source cart does not belong to user: " + sourceCart.getId());
                }
            }
            
            // Merge each source cart into target
            for (Cart sourceCart : sourceCarts) {
                if (!sourceCart.getId().equals(targetCartId)) {
                    mergeCartsInternal(sourceCart, targetCart, userId);
                    
                    // Mark source cart as merged
                    sourceCart.setStatus(CartStatus.MERGED);
                    sourceCart.setMergedToCartId(targetCartId);
                    cartRepository.save(sourceCart);
                }
            }
            
            log.info("Successfully merged {} carts into cart {} for user {}", 
                    cartIdsToMerge.size(), targetCartId, userId);
            
            return convertToResponseDto(targetCart);
            
        } catch (Exception e) {
            log.error("Error merging user carts: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to merge user carts", e);
        }
    }

    // ==================== INTERNAL MERGE LOGIC ====================

    /**
     * Internal method to merge two carts
     */
    private Cart mergeCartsInternal(Cart sourceCart, Cart targetCart, String userId) {
        try {
            log.debug("Merging cart {} into cart {}", sourceCart.getId(), targetCart.getId());
            
            List<CartItem> sourceItems = cartItemRepository.findByCartId(sourceCart.getId());
            List<CartItem> targetItems = cartItemRepository.findByCartId(targetCart.getId());
            
            List<CartItem> itemsToSave = new ArrayList<>();
            
            // Process each source item
            for (CartItem sourceItem : sourceItems) {
                // Check if item already exists in target cart
                Optional<CartItem> existingItemOpt = findMatchingItem(sourceItem, targetItems);
                
                if (existingItemOpt.isPresent()) {
                    // Merge quantities
                    CartItem existingItem = existingItemOpt.get();
                    int newQuantity = existingItem.getQuantity() + sourceItem.getQuantity();
                    
                    // Validate merged quantity
                    if (newQuantity <= 99) { // Max quantity limit
                        existingItem.updateQuantity(newQuantity);
                        itemsToSave.add(existingItem);
                        
                        log.debug("Merged item {}: {} + {} = {}", 
                                sourceItem.getProductId(), existingItem.getQuantity() - sourceItem.getQuantity(), 
                                sourceItem.getQuantity(), newQuantity);
                    } else {
                        // Keep existing quantity if merge would exceed limit
                        log.warn("Cannot merge item {} - would exceed quantity limit", sourceItem.getProductId());
                    }
                } else {
                    // Move item to target cart
                    sourceItem.setCart(targetCart);
                    itemsToSave.add(sourceItem);
                    
                    log.debug("Moved item {} to target cart", sourceItem.getProductId());
                }
            }
            
            // Save all modified items
            cartItemRepository.saveAll(itemsToSave);
            
            // Update target cart totals
            updateCartTotals(targetCart);
            
            // Mark source cart as merged
            sourceCart.setStatus(CartStatus.MERGED);
            sourceCart.setMergedToCartId(targetCart.getId());
            cartRepository.save(sourceCart);
            
            return targetCart;
            
        } catch (Exception e) {
            log.error("Error in internal cart merge: {}", e.getMessage(), e);
            throw new RuntimeException("Internal cart merge failed", e);
        }
    }

    /**
     * Convert guest cart to user cart
     */
    private Cart convertGuestCartToUserCart(Cart guestCart, String userId) {
        try {
            log.debug("Converting guest cart {} to user cart for user {}", guestCart.getId(), userId);
            
            // Update cart properties
            guestCart.setUserId(userId);
            guestCart.setCartType(CartType.USER);
            guestCart.setExpirationFromType(); // Extend expiration for user cart
            guestCart.setUpdatedAt(LocalDateTime.now());
            
            // Save updated cart
            Cart userCart = cartRepository.save(guestCart);
            
            log.info("Converted guest cart {} to user cart for user {}", guestCart.getId(), userId);
            return userCart;
            
        } catch (Exception e) {
            log.error("Error converting guest cart to user cart: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert guest cart to user cart", e);
        }
    }

    // ==================== MERGE STRATEGIES ====================

    /**
     * Find matching item in target cart
     */
    private Optional<CartItem> findMatchingItem(CartItem sourceItem, List<CartItem> targetItems) {
        return targetItems.stream()
                .filter(targetItem -> 
                    targetItem.getProductId().equals(sourceItem.getProductId()) &&
                    Objects.equals(targetItem.getVariantId(), sourceItem.getVariantId()) &&
                    targetItem.getUnitPrice().equals(sourceItem.getUnitPrice()))
                .findFirst();
    }

    /**
     * Merge item properties (for items with same product but different properties)
     */
    private CartItem mergeItemProperties(CartItem sourceItem, CartItem targetItem) {
        // Merge special instructions
        if (sourceItem.getSpecialInstructions() != null && !sourceItem.getSpecialInstructions().isEmpty()) {
            String mergedInstructions = targetItem.getSpecialInstructions() != null ? 
                targetItem.getSpecialInstructions() + "; " + sourceItem.getSpecialInstructions() :
                sourceItem.getSpecialInstructions();
            targetItem.setSpecialInstructions(mergedInstructions);
        }
        
        // Merge gift options (prefer source if it's a gift)
        if (Boolean.TRUE.equals(sourceItem.getIsGift())) {
            targetItem.setIsGift(true);
            if (sourceItem.getGiftMessage() != null) {
                targetItem.setGiftMessage(sourceItem.getGiftMessage());
            }
            if (sourceItem.getGiftWrapType() != null) {
                targetItem.setGiftWrapType(sourceItem.getGiftWrapType());
            }
        }
        
        return targetItem;
    }

    // ==================== MERGE VALIDATION ====================

    /**
     * Validate merge operation
     */
    @Override
    public boolean canMergeCarts(Cart sourceCart, Cart targetCart) {
        try {
            // Check if carts can be modified
            if (!sourceCart.canBeModified() || !targetCart.canBeModified()) {
                log.warn("Cannot merge carts - one or both carts cannot be modified");
                return false;
            }

            // Check if merge would exceed item limits
            int totalItems = sourceCart.getItemCount() + targetCart.getItemCount();
            if (totalItems > 100) { // Max items per cart
                log.warn("Cannot merge carts - would exceed maximum item limit");
                return false;
            }

            // Check if merge would exceed value limits
            BigDecimal totalValue = sourceCart.getTotalAmount().add(targetCart.getTotalAmount());
            if (totalValue.compareTo(new BigDecimal("10000.00")) > 0) {
                log.warn("Cannot merge carts - would exceed maximum cart value");
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("Error validating cart merge: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== HELPER METHODS ====================

    private CartResponseDto getOrCreateUserCart(String userId) {
        Optional<Cart> userCartOpt = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
        if (userCartOpt.isPresent()) {
            return convertToResponseDto(userCartOpt.get());
        } else {
            // Create new user cart
            Cart newCart = Cart.builder()
                    .userId(userId)
                    .status(CartStatus.ACTIVE)
                    .cartType(CartType.USER)
                    .currency("USD")
                    .subtotal(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.ZERO)
                    .itemCount(0)
                    .totalQuantity(0)
                    .build();

            newCart.setExpirationFromType();
            newCart.setLastActivityAt(LocalDateTime.now());

            Cart savedCart = cartRepository.save(newCart);
            return convertToResponseDto(savedCart);
        }
    }

    private void updateCartTotals(Cart cart) {
        BigDecimal subtotal = cartItemRepository.getCartSubtotal(cart.getId());
        int itemCount = (int) cartItemRepository.countByCartId(cart.getId());
        int totalQuantity = cartItemRepository.getTotalQuantityByCartId(cart.getId());

        cart.setSubtotal(subtotal);
        cart.setTotalAmount(subtotal.add(cart.getTaxAmount() != null ? cart.getTaxAmount() : BigDecimal.ZERO)
                                   .add(cart.getShippingAmount() != null ? cart.getShippingAmount() : BigDecimal.ZERO)
                                   .subtract(cart.getDiscountAmount() != null ? cart.getDiscountAmount() : BigDecimal.ZERO));
        cart.setItemCount(itemCount);
        cart.setTotalQuantity(totalQuantity);
        cart.setUpdatedAt(LocalDateTime.now());

        cartRepository.save(cart);
    }

    private CartResponseDto convertToResponseDto(Cart cart) {
        return CartResponseDto.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .sessionId(cart.getSessionId())
                .status(cart.getStatus())
                .cartType(cart.getCartType())
                .currency(cart.getCurrency())
                .subtotal(cart.getSubtotal())
                .totalAmount(cart.getTotalAmount())
                .itemCount(cart.getItemCount())
                .totalQuantity(cart.getTotalQuantity())
                .couponCode(cart.getCouponCode())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .lastActivityAt(cart.getLastActivityAt())
                .expiresAt(cart.getExpiresAt())
                .build();
    }
}
