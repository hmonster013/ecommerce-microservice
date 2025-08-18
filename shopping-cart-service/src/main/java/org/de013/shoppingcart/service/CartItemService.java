package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.dto.ProductInfo;
import org.de013.shoppingcart.dto.request.AddToCartDto;
import org.de013.shoppingcart.dto.request.RemoveFromCartDto;
import org.de013.shoppingcart.dto.request.UpdateCartItemDto;
import org.de013.shoppingcart.dto.response.CartItemResponseDto;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartAnalytics;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.entity.RedisCart;
import org.de013.shoppingcart.repository.jpa.CartAnalyticsRepository;
import org.de013.shoppingcart.repository.jpa.CartItemRepository;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.de013.shoppingcart.repository.redis.RedisCartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cart Item Service
 * Handles cart item operations including add, update, remove, and validation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final RedisCartRepository redisCartRepository;
    private final CartAnalyticsRepository analyticsRepository;
    private final ProductCatalogClient productCatalogClient;
    private final CartValidationService validationService;

    // ==================== ITEM ADDITION ====================

    /**
     * Add item to cart
     */
    public CartItemResponseDto addItemToCart(Long cartId, AddToCartDto request) {
        try {
            log.debug("Adding item {} to cart {}", request.getProductId(), cartId);
            
            // Get cart
            Optional<Cart> cartOpt = cartRepository.findByIdWithItems(cartId);
            if (cartOpt.isEmpty()) {
                throw new RuntimeException("Cart not found");
            }
            
            Cart cart = cartOpt.get();
            
            // Validate cart can be modified
            if (!cart.canBeModified()) {
                throw new RuntimeException("Cart cannot be modified");
            }
            
            // Get product information
            ProductInfo productInfo = productCatalogClient.getProductInfo(request.getProductId());
            if (productInfo == null) {
                throw new RuntimeException("Product not found");
            }
            
            // Check if item already exists
            Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndProductIdAndVariantId(
                cartId, request.getProductId(), request.getVariantId());
            
            CartItem cartItem;
            if (existingItem.isPresent()) {
                // Update existing item
                cartItem = existingItem.get();
                int newQuantity = cartItem.getQuantity() + request.getQuantity();
                cartItem.updateQuantity(newQuantity);
            } else {
                // Create new item
                cartItem = createNewCartItem(cart, request, productInfo);
                cart.addItem(cartItem);
            }
            
            // Validate item
            validationService.validateCartItemAgainstCatalog(cartItem);
            
            // Save item
            cartItem = cartItemRepository.save(cartItem);
            
            // Update Redis
            updateRedisCart(cartId);
            
            // Record analytics
            recordItemAnalytics(CartAnalytics.createItemAddedEvent(
                cartId, request.getUserId(), request.getSessionId(), 
                request.getProductId(), request.getQuantity(), cartItem.getUnitPrice()));
            
            log.info("Added item {} to cart {}, quantity: {}", 
                    request.getProductId(), cartId, request.getQuantity());
            
            return convertToResponseDto(cartItem);
            
        } catch (Exception e) {
            log.error("Error adding item to cart: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add item to cart", e);
        }
    }

    // ==================== ITEM UPDATES ====================

    /**
     * Update cart item
     */
    public CartItemResponseDto updateCartItem(UpdateCartItemDto request) {
        try {
            log.debug("Updating cart item {}", request.getItemId());
            
            Optional<CartItem> itemOpt = cartItemRepository.findById(request.getItemId());
            if (itemOpt.isEmpty()) {
                throw new RuntimeException("Cart item not found");
            }
            
            CartItem item = itemOpt.get();
            
            // Validate cart can be modified
            if (!item.getCart().canBeModified()) {
                throw new RuntimeException("Cart cannot be modified");
            }
            
            // Update fields
            if (request.getQuantity() != null) {
                item.updateQuantity(request.getQuantity());
            }
            
            if (request.getUnitPrice() != null && request.isPriceUpdate()) {
                item.updateUnitPrice(request.getUnitPrice());
            }
            
            if (request.getSpecialInstructions() != null) {
                item.setSpecialInstructions(request.getSpecialInstructions());
            }
            
            if (request.getIsGift() != null) {
                item.setIsGift(request.getIsGift());
                item.setGiftMessage(request.getGiftMessage());
                item.setGiftWrapType(request.getGiftWrapType());
            }
            
            // Validate updated item
            validationService.validateCartItemAgainstCatalog(item);
            
            // Save item
            item = cartItemRepository.save(item);
            
            // Update Redis
            updateRedisCart(item.getCart().getId());
            
            log.info("Updated cart item {}", request.getItemId());
            return convertToResponseDto(item);
            
        } catch (Exception e) {
            log.error("Error updating cart item: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update cart item", e);
        }
    }

    // ==================== ITEM REMOVAL ====================

    /**
     * Remove item from cart
     */
    public boolean removeItemFromCart(RemoveFromCartDto request) {
        try {
            log.debug("Removing item from cart: {}", request);
            
            if (request.isSingleRemoval()) {
                return removeSingleItem(request.getItemId(), request.getUserId(), request.getSessionId());
            } else if (request.isBulkRemoval()) {
                return removeBulkItems(request.getItemIds(), request.getUserId(), request.getSessionId());
            } else if (request.isRemoveAll()) {
                return removeAllItemsFromCart(request.getUserId(), request.getSessionId());
            } else if (request.isByProduct()) {
                return removeItemsByProduct(request.getProductId(), request.getVariantId(), 
                                          request.getUserId(), request.getSessionId());
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error removing item from cart: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Remove single item
     */
    private boolean removeSingleItem(Long itemId, String userId, String sessionId) {
        Optional<CartItem> itemOpt = cartItemRepository.findById(itemId);
        if (itemOpt.isEmpty()) {
            return false;
        }
        
        CartItem item = itemOpt.get();
        Long cartId = item.getCart().getId();
        
        // Record analytics before removal
        recordItemAnalytics(CartAnalytics.createItemRemovedEvent(
            cartId, userId, sessionId, item.getProductId(), item.getQuantity()));
        
        // Soft delete item
        cartItemRepository.batchSoftDelete(List.of(itemId), LocalDateTime.now(), "USER_REQUEST");
        
        // Update Redis
        updateRedisCart(cartId);
        
        log.info("Removed item {} from cart {}", itemId, cartId);
        return true;
    }

    /**
     * Remove multiple items
     */
    private boolean removeBulkItems(List<Long> itemIds, String userId, String sessionId) {
        if (itemIds == null || itemIds.isEmpty()) {
            return false;
        }
        
        // Get items for analytics
        List<CartItem> items = cartItemRepository.findAllById(itemIds);
        
        // Record analytics
        for (CartItem item : items) {
            recordItemAnalytics(CartAnalytics.createItemRemovedEvent(
                item.getCart().getId(), userId, sessionId, item.getProductId(), item.getQuantity()));
        }
        
        // Soft delete items
        cartItemRepository.batchSoftDelete(itemIds, LocalDateTime.now(), "USER_REQUEST");
        
        // Update Redis for affected carts
        items.stream()
                .map(item -> item.getCart().getId())
                .distinct()
                .forEach(this::updateRedisCart);
        
        log.info("Removed {} items from cart", itemIds.size());
        return true;
    }

    /**
     * Remove all items from cart
     */
    public boolean removeAllItems(Long cartId) {
        try {
            cartItemRepository.batchSoftDeleteByCartId(cartId, LocalDateTime.now(), "CLEAR_CART");
            updateRedisCart(cartId);
            log.info("Removed all items from cart {}", cartId);
            return true;
        } catch (Exception e) {
            log.error("Error removing all items from cart {}: {}", cartId, e.getMessage(), e);
            return false;
        }
    }

    private boolean removeAllItemsFromCart(String userId, String sessionId) {
        // Get cart first
        Optional<Cart> cartOpt = getCartByUserOrSession(userId, sessionId);
        if (cartOpt.isEmpty()) {
            return false;
        }
        
        return removeAllItems(cartOpt.get().getId());
    }

    /**
     * Remove items by product
     */
    private boolean removeItemsByProduct(String productId, String variantId, String userId, String sessionId) {
        Optional<Cart> cartOpt = getCartByUserOrSession(userId, sessionId);
        if (cartOpt.isEmpty()) {
            return false;
        }
        
        Optional<CartItem> itemOpt = cartItemRepository.findByCartIdAndProductIdAndVariantId(
            cartOpt.get().getId(), productId, variantId);
        
        if (itemOpt.isPresent()) {
            return removeSingleItem(itemOpt.get().getId(), userId, sessionId);
        }
        
        return false;
    }

    // ==================== CART CALCULATIONS ====================

    /**
     * Calculate cart subtotal
     */
    public BigDecimal calculateCartSubtotal(Long cartId) {
        try {
            return cartItemRepository.getCartSubtotal(cartId);
        } catch (Exception e) {
            log.error("Error calculating cart subtotal: {}", e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get cart item count
     */
    public int getCartItemCount(Long cartId) {
        try {
            return (int) cartItemRepository.countByCartId(cartId);
        } catch (Exception e) {
            log.error("Error getting cart item count: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Get cart total quantity
     */
    public int getCartTotalQuantity(Long cartId) {
        try {
            return cartItemRepository.getTotalQuantityByCartId(cartId);
        } catch (Exception e) {
            log.error("Error getting cart total quantity: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Get cart items
     */
    public List<CartItemResponseDto> getCartItems(Long cartId) {
        try {
            List<CartItem> items = cartItemRepository.findByCartId(cartId);
            return items.stream()
                    .map(this::convertToResponseDto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting cart items: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // ==================== HELPER METHODS ====================

    private CartItem createNewCartItem(Cart cart, AddToCartDto request, ProductInfo productInfo) {
        return CartItem.builder()
                .cart(cart)
                .productId(request.getProductId())
                .productSku(productInfo.getSku())
                .productName(productInfo.getName())
                .productDescription(productInfo.getDescription())
                .productImageUrl(productInfo.getImageUrl())
                .categoryId(productInfo.getCategoryId())
                .categoryName(productInfo.getCategoryName())
                .quantity(request.getQuantity())
                .unitPrice(request.getUnitPrice() != null ? request.getUnitPrice() : productInfo.getPrice())
                .originalPrice(productInfo.getOriginalPrice())
                .currency("USD")
                .variantId(request.getVariantId())
                .specialInstructions(request.getSpecialInstructions())
                .isGift(request.getIsGift())
                .giftMessage(request.getGiftMessage())
                .giftWrapType(request.getGiftWrapType())
                .addedAt(LocalDateTime.now())
                .availabilityStatus("AVAILABLE")
                .stockQuantity(productInfo.getStockQuantity())
                .build();
    }

    private void updateRedisCart(Long cartId) {
        try {
            Optional<RedisCart> redisCartOpt = redisCartRepository.findByCartId(cartId);
            if (redisCartOpt.isPresent()) {
                RedisCart redisCart = redisCartOpt.get();
                
                // Recalculate totals
                redisCart.setSubtotal(calculateCartSubtotal(cartId));
                redisCart.setItemCount(getCartItemCount(cartId));
                redisCart.setTotalQuantity(getCartTotalQuantity(cartId));
                redisCart.updateCartTotals();
                
                redisCartRepository.save(redisCart);
            }
        } catch (Exception e) {
            log.error("Error updating Redis cart: {}", e.getMessage(), e);
        }
    }

    private Optional<Cart> getCartByUserOrSession(String userId, String sessionId) {
        if (userId != null) {
            return cartRepository.findByUserIdAndStatus(userId, org.de013.shoppingcart.entity.enums.CartStatus.ACTIVE);
        } else if (sessionId != null) {
            return cartRepository.findBySessionIdAndStatus(sessionId, org.de013.shoppingcart.entity.enums.CartStatus.ACTIVE);
        }
        return Optional.empty();
    }

    private CartItemResponseDto convertToResponseDto(CartItem item) {
        return CartItemResponseDto.builder()
                .itemId(item.getId())
                .productId(item.getProductId())
                .productSku(item.getProductSku())
                .productName(item.getProductName())
                .productDescription(item.getProductDescription())
                .productImageUrl(item.getProductImageUrl())
                .categoryId(item.getCategoryId())
                .categoryName(item.getCategoryName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .originalPrice(item.getOriginalPrice())
                .discountAmount(item.getDiscountAmount())
                .totalPrice(item.getTotalPrice())
                .currency(item.getCurrency())
                .variantId(item.getVariantId())
                .variantAttributes(item.getVariantAttributes())
                .specialInstructions(item.getSpecialInstructions())
                .isGift(item.getIsGift())
                .giftMessage(item.getGiftMessage())
                .giftWrapType(item.getGiftWrapType())
                .giftWrapPrice(item.getGiftWrapPrice())
                .addedAt(item.getAddedAt())
                .availabilityStatus(item.getAvailabilityStatus())
                .stockQuantity(item.getStockQuantity())
                .priceChanged(item.getPriceChanged())
                .build();
    }

    private void recordItemAnalytics(CartAnalytics analytics) {
        try {
            analyticsRepository.save(analytics);
        } catch (Exception e) {
            log.error("Error recording item analytics: {}", e.getMessage(), e);
        }
    }


}
