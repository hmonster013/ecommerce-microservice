package org.de013.shoppingcart.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CartItem Entity
 * Represents an item in a shopping cart
 */
@Entity
@Table(name = "cart_items", indexes = {
    @Index(name = "idx_cart_item_cart_id", columnList = "cart_id"),
    @Index(name = "idx_cart_item_product_id", columnList = "product_id"),
    @Index(name = "idx_cart_item_cart_product", columnList = "cart_id, product_id"),
    @Index(name = "idx_cart_item_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"cart"})
@EqualsAndHashCode(callSuper = true, exclude = {"cart"})
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_item_cart"))
    private Cart cart;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "product_sku", length = 100)
    private String productSku;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_description", length = 1000)
    private String productDescription;

    @Column(name = "product_image_url", length = 500)
    private String productImageUrl;

    @Column(name = "category_id", length = 36)
    private String categoryId;

    @Column(name = "category_name", length = 255)
    private String categoryName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "original_price", precision = 19, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "discount_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "weight", precision = 10, scale = 3)
    private BigDecimal weight;

    @Column(name = "dimensions", length = 100)
    private String dimensions;

    @Column(name = "variant_id", length = 36)
    private String variantId;

    @Column(name = "variant_attributes", length = 1000)
    private String variantAttributes;

    @Column(name = "special_instructions", length = 500)
    private String specialInstructions;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @Column(name = "last_price_check_at")
    private LocalDateTime lastPriceCheckAt;

    @Column(name = "price_changed", nullable = false)
    @Builder.Default
    private Boolean priceChanged = false;

    @Column(name = "availability_status", length = 20)
    @Builder.Default
    private String availabilityStatus = "AVAILABLE";

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "max_quantity_per_order")
    private Integer maxQuantityPerOrder;

    @Column(name = "is_gift", nullable = false)
    @Builder.Default
    private Boolean isGift = false;

    @Column(name = "gift_message", length = 500)
    private String giftMessage;

    @Column(name = "gift_wrap_type", length = 50)
    private String giftWrapType;

    @Column(name = "gift_wrap_price", precision = 19, scale = 2)
    private BigDecimal giftWrapPrice;

    /**
     * Calculate total price based on quantity and unit price
     */
    public void calculateTotalPrice() {
        if (quantity != null && unitPrice != null) {
            BigDecimal baseTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
            BigDecimal giftWrap = (isGift && giftWrapPrice != null) ? giftWrapPrice : BigDecimal.ZERO;

            this.totalPrice = baseTotal.subtract(discount).add(giftWrap);
        } else {
            // Set to zero if quantity or unitPrice is null
            this.totalPrice = BigDecimal.ZERO;
        }
    }

    /**
     * Update quantity and recalculate total
     */
    public void updateQuantity(Integer newQuantity) {
        this.quantity = newQuantity;
        calculateTotalPrice();
        if (cart != null) {
            cart.updateCartTotals();
        }
    }

    /**
     * Update unit price and recalculate total
     */
    public void updateUnitPrice(BigDecimal newUnitPrice) {
        if (this.unitPrice != null && !this.unitPrice.equals(newUnitPrice)) {
            this.priceChanged = true;
        }
        this.unitPrice = newUnitPrice;
        this.lastPriceCheckAt = LocalDateTime.now();
        calculateTotalPrice();
        if (cart != null) {
            cart.updateCartTotals();
        }
    }

    /**
     * Check if item is available for purchase
     */
    public boolean isAvailable() {
        return "AVAILABLE".equals(availabilityStatus) && 
               (stockQuantity == null || stockQuantity > 0);
    }

    /**
     * Check if requested quantity is available
     */
    public boolean isQuantityAvailable() {
        return stockQuantity == null || stockQuantity >= quantity;
    }

    /**
     * Check if quantity exceeds maximum allowed per order
     */
    public boolean exceedsMaxQuantity() {
        return maxQuantityPerOrder != null && quantity > maxQuantityPerOrder;
    }

    /**
     * Get discount percentage
     */
    public BigDecimal getDiscountPercentage() {
        if (originalPrice != null && originalPrice.compareTo(BigDecimal.ZERO) > 0 && 
            discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            return discountAmount.divide(originalPrice, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
        calculateTotalPrice();
    }

    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
        calculateTotalPrice();
    }
}
