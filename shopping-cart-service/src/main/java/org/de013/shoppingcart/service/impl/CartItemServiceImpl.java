package org.de013.shoppingcart.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.ProductDetailDto;
import org.de013.shoppingcart.dto.request.AddToCartDto;
import org.de013.shoppingcart.dto.request.GiftOptionsDto;

import org.de013.shoppingcart.dto.request.UpdateCartItemDto;
import org.de013.shoppingcart.dto.response.CartItemResponseDto;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.entity.RedisCart;
import org.de013.shoppingcart.repository.jpa.CartItemRepository;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.de013.shoppingcart.repository.redis.RedisCartRepository;
import org.de013.shoppingcart.service.CartItemService;
import org.de013.shoppingcart.service.ProductCatalogClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cart Item Service Implementation
 * Handles cart item operations including add, update, remove, and validation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CartItemServiceImpl implements CartItemService {

    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final RedisCartRepository redisCartRepository;

    private final ProductCatalogClient productCatalogClient;

    // ==================== ITEM ADDITION ====================

    /**
     * Add item to cart
     */
    @Override
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
                log.error("Cart {} cannot be modified - Status: {}, Expired: {}, ExpiresAt: {}",
                         cartId, cart.getStatus(), cart.isExpired(), cart.getExpiresAt());
                throw new RuntimeException("Cart cannot be modified");
            }
            
            // Debug logging
            log.debug("Request details: productId={}, quantity={}, variantId={}",
                     request.getProductId(), request.getQuantity(), request.getVariantId());

            // Get product information
            ProductDetailDto productInfo = productCatalogClient.getProductInfo(request.getProductId());
            if (productInfo == null) {
                throw new RuntimeException("Product not found");
            }

            log.debug("Product info - current price: {}, original price: {}",
                     productInfo.getCurrentPrice(), productInfo.getOriginalPrice());
            
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
            
            // Basic validation - check required fields
            if (cartItem.getQuantity() <= 0) {
                throw new RuntimeException("Invalid quantity");
            }
            
            // Save item
            cartItem = cartItemRepository.save(cartItem);
            
            // Update Redis
            updateRedisCart(cartId);
            
            // Analytics removed for basic functionality
            
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
    @Override
    public CartItemResponseDto updateCartItem(Long itemId, UpdateCartItemDto request) {
        try {
            log.debug("Updating cart item {}", itemId);

            Optional<CartItem> itemOpt = cartItemRepository.findById(itemId);
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

            // Note: Price updates from client are not allowed for security reasons
            // Prices are always fetched from Product Catalog Service

            // Optionally refresh price from Product Catalog if needed
            if (request.isRefreshPrice()) {
                refreshItemPrice(item);
            }
            
            if (request.getSpecialInstructions() != null) {
                item.setSpecialInstructions(request.getSpecialInstructions());
            }
            
            if (request.getIsGift() != null) {
                item.setIsGift(request.getIsGift());
                item.setGiftMessage(request.getGiftMessage());
                item.setGiftWrapType(request.getGiftWrapType());
            }
            
            // Basic validation - check required fields
            if (item.getQuantity() <= 0) {
                throw new RuntimeException("Invalid quantity");
            }
            
            // Save item
            item = cartItemRepository.save(item);
            
            // Update Redis
            updateRedisCart(item.getCart().getId());
            
            log.info("Updated cart item {}", itemId);
            return convertToResponseDto(item);
            
        } catch (Exception e) {
            log.error("Error updating cart item: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update cart item", e);
        }
    }

    /**
     * Update gift options for cart item
     */
    @Override
    public CartItemResponseDto updateGiftOptions(Long itemId, GiftOptionsDto giftOptions) {
        try {
            log.debug("Updating gift options for cart item {}", itemId);

            Optional<CartItem> itemOpt = cartItemRepository.findById(itemId);
            if (itemOpt.isEmpty()) {
                throw new RuntimeException("Cart item not found");
            }

            CartItem item = itemOpt.get();

            // Update gift options
            if (giftOptions.getIsGift() != null) {
                item.setIsGift(giftOptions.getIsGift());

                // If disabling gift, clear all gift-related fields
                if (Boolean.FALSE.equals(giftOptions.getIsGift())) {
                    item.setGiftMessage(null);
                    item.setGiftWrapType(null);
                    log.debug("Disabled gift mode for item {}, cleared gift fields", itemId);
                }
            }

            // Update gift message (only if gift is enabled)
            if (giftOptions.getGiftMessage() != null && Boolean.TRUE.equals(item.getIsGift())) {
                item.setGiftMessage(giftOptions.getGiftMessage());
            }

            // Update gift wrap type (only if gift is enabled)
            if (giftOptions.getGiftWrapType() != null && Boolean.TRUE.equals(item.getIsGift())) {
                item.setGiftWrapType(giftOptions.getGiftWrapType());
            }

            // Validate gift requirements
            if (Boolean.TRUE.equals(item.getIsGift())) {
                if (item.getGiftMessage() == null || item.getGiftMessage().trim().isEmpty()) {
                    throw new RuntimeException("Gift message is required when item is marked as gift");
                }
            }

            // Save item
            item = cartItemRepository.save(item);

            // Update Redis
            updateRedisCart(item.getCart().getId());

            log.info("Updated gift options for cart item {}", itemId);
            return convertToResponseDto(item);

        } catch (Exception e) {
            log.error("Error updating gift options for item {}: {}", itemId, e.getMessage(), e);
            throw new RuntimeException("Failed to update gift options", e);
        }
    }

    // ==================== ITEM REMOVAL ====================

    /**
     * Remove cart item by ID
     */
    @Override
    public boolean removeCartItem(Long itemId) {
        try {
            log.debug("Removing cart item: {}", itemId);

            if (!cartItemRepository.existsById(itemId)) {
                log.warn("Cart item {} not found", itemId);
                return false;
            }

            cartItemRepository.deleteById(itemId);
            log.info("Removed cart item: {}", itemId);
            return true;

        } catch (Exception e) {
            log.error("Error removing cart item {}: {}", itemId, e.getMessage(), e);
            return false;
        }
    }











    // ==================== CART CALCULATIONS ====================

    /**
     * Calculate cart subtotal
     */
    @Override
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
    @Override
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
    @Override
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
    @Override
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

    /**
     * Get cart item by ID
     */
    @Override
    public Optional<CartItemResponseDto> getCartItemById(Long itemId) {
        try {
            log.debug("Getting cart item by ID: {}", itemId);

            Optional<CartItem> itemOpt = cartItemRepository.findById(itemId);
            if (itemOpt.isEmpty()) {
                log.debug("Cart item not found: {}", itemId);
                return Optional.empty();
            }

            CartItem item = itemOpt.get();

            // Check if item is deleted
            if (item.isDeleted()) {
                log.debug("Cart item {} is deleted", itemId);
                return Optional.empty();
            }

            return Optional.of(convertToResponseDto(item));

        } catch (Exception e) {
            log.error("Error getting cart item by ID {}: {}", itemId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Refresh item price from Product Catalog Service
     */
    private void refreshItemPrice(CartItem item) {
        try {
            log.debug("Refreshing price for product: {}", item.getProductId());

            ProductDetailDto productInfo = productCatalogClient.getProductInfo(item.getProductId());
            if (productInfo != null && productInfo.getCurrentPrice() != null) {
                BigDecimal newPrice = productInfo.getCurrentPrice();
                if (!newPrice.equals(item.getUnitPrice())) {
                    log.info("Updating price for product {} from {} to {}",
                            item.getProductId(), item.getUnitPrice(), newPrice);
                    item.updateUnitPrice(newPrice);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to refresh price for product {}: {}", item.getProductId(), e.getMessage());
            // Don't fail the update if price refresh fails
        }
    }

    private CartItem createNewCartItem(Cart cart, AddToCartDto request, ProductDetailDto productInfo) {
        // Always use price from Product Catalog for security - never trust client-provided prices
        BigDecimal finalUnitPrice = productInfo.getCurrentPrice() != null ?
                                   productInfo.getCurrentPrice() : BigDecimal.ZERO;

        log.debug("Creating cart item - productInfo.currentPrice: {}, final unitPrice: {}",
                 productInfo.getCurrentPrice(), finalUnitPrice);

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
                .unitPrice(finalUnitPrice)
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
}
