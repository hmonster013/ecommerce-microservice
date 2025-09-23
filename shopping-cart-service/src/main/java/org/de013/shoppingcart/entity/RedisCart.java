package org.de013.shoppingcart.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.entity.enums.CartType;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RedisCart Entity
 * Represents a shopping cart stored in Redis for high-performance operations
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@RedisHash(value = "cart", timeToLive = 86400) // 24 hours default TTL
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
public class RedisCart implements Serializable {

    @Id
    private String id; // Format: "cart:{cartId}" or "session:{sessionId}" or "user:{userId}"

    @Indexed
    private Long cartId;

    @Indexed
    private String userId;

    @Indexed
    private String sessionId;

    private CartStatus status;
    private CartType cartType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastActivityAt;

    private String currency;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private Integer totalQuantity;
    private String couponCode;
    private String notes;

    @Builder.Default
    private List<RedisCartItem> items = new ArrayList<>();

    /**
     * RedisCartItem - Nested class for cart items in Redis
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    public static class RedisCartItem implements Serializable {
        
        private Long itemId;
        private String productId;
        private String productSku;
        private String productName;
        private String productDescription;
        private String productImageUrl;
        private String categoryId;
        private String categoryName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal originalPrice;
        private BigDecimal discountAmount;
        private BigDecimal totalPrice;
        private String currency;
        private BigDecimal weight;
        private String dimensions;
        private String variantId;
        private String variantAttributes;
        private String specialInstructions;
        private String availabilityStatus;
        private Integer stockQuantity;
        private Integer maxQuantityPerOrder;
        private Boolean isGift;
        private String giftMessage;
        private String giftWrapType;
        private BigDecimal giftWrapPrice;
        private Boolean priceChanged;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime addedAt;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime lastPriceCheckAt;

        /**
         * Calculate total price for this item
         */
        public void calculateTotalPrice() {
            if (quantity != null && unitPrice != null) {
                BigDecimal baseTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
                BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
                BigDecimal giftWrap = (Boolean.TRUE.equals(isGift) && giftWrapPrice != null) ? giftWrapPrice : BigDecimal.ZERO;
                
                this.totalPrice = baseTotal.subtract(discount).add(giftWrap);
            }
        }

        /**
         * Check if item is available
         */
        public boolean isAvailable() {
            return "AVAILABLE".equals(availabilityStatus) && 
                   (stockQuantity == null || stockQuantity > 0);
        }

        /**
         * Convert to JPA CartItem entity
         */
        public CartItem toCartItem() {
            CartItem cartItem = CartItem.builder()
                    .productId(productId)
                    .productSku(productSku)
                    .productName(productName)
                    .productDescription(productDescription)
                    .productImageUrl(productImageUrl)
                    .categoryId(categoryId)
                    .categoryName(categoryName)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .originalPrice(originalPrice)
                    .discountAmount(discountAmount)
                    .totalPrice(totalPrice)
                    .currency(currency)
                    .weight(weight)
                    .dimensions(dimensions)
                    .variantId(variantId)
                    .variantAttributes(variantAttributes)
                    .specialInstructions(specialInstructions)
                    .availabilityStatus(availabilityStatus)
                    .stockQuantity(stockQuantity)
                    .maxQuantityPerOrder(maxQuantityPerOrder)
                    .isGift(isGift)
                    .giftMessage(giftMessage)
                    .giftWrapType(giftWrapType)
                    .giftWrapPrice(giftWrapPrice)
                    .priceChanged(priceChanged)
                    .addedAt(addedAt)
                    .lastPriceCheckAt(lastPriceCheckAt)
                    .build();

            // Set ID separately since it's inherited from BaseEntity
            if (itemId != null) {
                cartItem.setId(itemId);
            }

            return cartItem;
        }

        /**
         * Create from JPA CartItem entity
         */
        public static RedisCartItem fromCartItem(CartItem cartItem) {
            return RedisCartItem.builder()
                    .itemId(cartItem.getId())
                    .productId(cartItem.getProductId())
                    .productSku(cartItem.getProductSku())
                    .productName(cartItem.getProductName())
                    .productDescription(cartItem.getProductDescription())
                    .productImageUrl(cartItem.getProductImageUrl())
                    .categoryId(cartItem.getCategoryId())
                    .categoryName(cartItem.getCategoryName())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(cartItem.getUnitPrice())
                    .originalPrice(cartItem.getOriginalPrice())
                    .discountAmount(cartItem.getDiscountAmount())
                    .totalPrice(cartItem.getTotalPrice())
                    .currency(cartItem.getCurrency())
                    .weight(cartItem.getWeight())
                    .dimensions(cartItem.getDimensions())
                    .variantId(cartItem.getVariantId())
                    .variantAttributes(cartItem.getVariantAttributes())
                    .specialInstructions(cartItem.getSpecialInstructions())
                    .availabilityStatus(cartItem.getAvailabilityStatus())
                    .stockQuantity(cartItem.getStockQuantity())
                    .maxQuantityPerOrder(cartItem.getMaxQuantityPerOrder())
                    .isGift(cartItem.getIsGift())
                    .giftMessage(cartItem.getGiftMessage())
                    .giftWrapType(cartItem.getGiftWrapType())
                    .giftWrapPrice(cartItem.getGiftWrapPrice())
                    .priceChanged(cartItem.getPriceChanged())
                    .addedAt(cartItem.getAddedAt())
                    .lastPriceCheckAt(cartItem.getLastPriceCheckAt())
                    .build();
        }
    }

    /**
     * Add item to Redis cart
     */
    public void addItem(RedisCartItem item) {
        if (items == null) {
            items = new ArrayList<>();
        }
        
        // Check if item already exists, update quantity if so
        RedisCartItem existingItem = items.stream()
                .filter(i -> i.getProductId().equals(item.getProductId()) && 
                           (i.getVariantId() == null ? item.getVariantId() == null : 
                            i.getVariantId().equals(item.getVariantId())))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + item.getQuantity());
            existingItem.calculateTotalPrice();
        } else {
            item.calculateTotalPrice();
            items.add(item);
        }
        
        updateCartTotals();
    }

    /**
     * Remove item from Redis cart
     */
    public boolean removeItem(String productId, String variantId) {
        boolean removed = items.removeIf(item -> 
            item.getProductId().equals(productId) && 
            (variantId == null ? item.getVariantId() == null : variantId.equals(item.getVariantId())));
        
        if (removed) {
            updateCartTotals();
        }
        return removed;
    }

    /**
     * Update cart totals
     */
    public void updateCartTotals() {
        if (items == null) {
            items = new ArrayList<>();
        }

        this.itemCount = items.size();
        this.totalQuantity = items.stream().mapToInt(RedisCartItem::getQuantity).sum();
        this.subtotal = items.stream()
                .map(RedisCartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate total
        BigDecimal tax = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        BigDecimal shipping = shippingAmount != null ? shippingAmount : BigDecimal.ZERO;
        BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;

        this.totalAmount = subtotal.add(tax).add(shipping).subtract(discount);
        this.lastActivityAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if cart is empty
     */
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    /**
     * Check if cart is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Convert to JPA Cart entity
     */
    public Cart toCart() {
        Cart cart = Cart.builder()
                .userId(userId)
                .sessionId(sessionId)
                .status(status)
                .cartType(cartType)
                .expiresAt(expiresAt)
                .currency(currency)
                .subtotal(subtotal)
                .taxAmount(taxAmount)
                .shippingAmount(shippingAmount)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .itemCount(itemCount)
                .totalQuantity(totalQuantity)
                .couponCode(couponCode)
                .notes(notes)
                .lastActivityAt(lastActivityAt)
                .build();

        // Set audit fields
        cart.setCreatedAt(createdAt);
        cart.setUpdatedAt(updatedAt);

        // Set ID separately since it's inherited from BaseEntity
        if (cartId != null) {
            cart.setId(cartId);
        }

        return cart;
    }

    /**
     * Create from JPA Cart entity
     */
    public static RedisCart fromCart(Cart cart) {
        RedisCart redisCart = RedisCart.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .sessionId(cart.getSessionId())
                .status(cart.getStatus())
                .cartType(cart.getCartType())
                .expiresAt(cart.getExpiresAt())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .lastActivityAt(cart.getLastActivityAt())
                .currency(cart.getCurrency())
                .subtotal(cart.getSubtotal())
                .taxAmount(cart.getTaxAmount())
                .shippingAmount(cart.getShippingAmount())
                .discountAmount(cart.getDiscountAmount())
                .totalAmount(cart.getTotalAmount())
                .itemCount(cart.getItemCount())
                .totalQuantity(cart.getTotalQuantity())
                .couponCode(cart.getCouponCode())
                .notes(cart.getNotes())
                .build();

        // Convert cart items
        if (cart.getCartItems() != null) {
            List<RedisCartItem> redisItems = cart.getCartItems().stream()
                    .map(RedisCartItem::fromCartItem)
                    .toList();
            redisCart.setItems(redisItems);
        }

        // Set Redis ID - consistent with generateCartKey() logic
        if (cart.getUserId() != null) {
            redisCart.setId("user_cart:" + cart.getUserId());
        } else if (cart.getSessionId() != null) {
            redisCart.setId("session_cart:" + cart.getSessionId());
        } else {
            redisCart.setId("cart:" + cart.getId());
        }

        return redisCart;
    }
}
