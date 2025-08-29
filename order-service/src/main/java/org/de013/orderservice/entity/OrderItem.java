package org.de013.orderservice.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.orderservice.entity.valueobject.Money;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Order Item Entity
 * 
 * Represents an individual item within an order.
 * Contains product information and pricing details.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "idx_order_items_order_id", columnList = "order_id"),
    @Index(name = "idx_order_items_product_id", columnList = "product_id"),
    @Index(name = "idx_order_items_order_product", columnList = "order_id, product_id"),
    @Index(name = "idx_order_items_sku", columnList = "sku")
})
@SQLDelete(sql = "UPDATE order_items SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"order"})
@EqualsAndHashCode(callSuper = true, exclude = {"order"})
public class OrderItem extends BaseEntity {
    
    /**
     * Reference to the parent order
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @NotNull(message = "Order is required")
    private Order order;
    
    /**
     * Product ID from product catalog service
     */
    @Column(name = "product_id", nullable = false)
    @NotNull(message = "Product ID is required")
    @Positive(message = "Product ID must be positive")
    private Long productId;
    
    /**
     * Product SKU for reference
     */
    @Column(name = "sku", length = 100, nullable = false)
    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    private String sku;
    
    /**
     * Product name at the time of order
     */
    @Column(name = "product_name", length = 500, nullable = false)
    @NotBlank(message = "Product name is required")
    @Size(max = 500, message = "Product name must not exceed 500 characters")
    private String productName;
    
    /**
     * Product description at the time of order
     */
    @Column(name = "product_description", length = 2000)
    @Size(max = 2000, message = "Product description must not exceed 2000 characters")
    private String productDescription;
    
    /**
     * Product category at the time of order
     */
    @Column(name = "product_category", length = 200)
    @Size(max = 200, message = "Product category must not exceed 200 characters")
    private String productCategory;
    
    /**
     * Product brand at the time of order
     */
    @Column(name = "product_brand", length = 200)
    @Size(max = 200, message = "Product brand must not exceed 200 characters")
    private String productBrand;
    
    /**
     * Quantity ordered
     */
    @Column(name = "quantity", nullable = false)
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;
    
    /**
     * Unit price at the time of order
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "unit_price", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "unit_price_currency", length = 3))
    })
    @Valid
    @NotNull(message = "Unit price is required")
    private Money unitPrice;
    
    /**
     * Total price for this line item (quantity * unit price)
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "total_price", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "total_price_currency", length = 3))
    })
    @Valid
    @NotNull(message = "Total price is required")
    private Money totalPrice;
    
    /**
     * Discount amount applied to this item
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "discount_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "discount_currency", length = 3))
    })
    @Valid
    private Money discountAmount;
    
    /**
     * Tax amount for this item
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "tax_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "tax_currency", length = 3))
    })
    @Valid
    private Money taxAmount;
    
    /**
     * Product weight (for shipping calculations)
     */
    @Column(name = "weight", precision = 10, scale = 3)
    @DecimalMin(value = "0.0", message = "Weight must be non-negative")
    private java.math.BigDecimal weight;
    
    /**
     * Weight unit (kg, lb, etc.)
     */
    @Column(name = "weight_unit", length = 10)
    @Size(max = 10, message = "Weight unit must not exceed 10 characters")
    @Builder.Default
    private String weightUnit = "kg";
    
    /**
     * Product dimensions (length x width x height)
     */
    @Column(name = "dimensions", length = 100)
    @Size(max = 100, message = "Dimensions must not exceed 100 characters")
    private String dimensions;
    
    /**
     * Product image URL at the time of order
     */
    @Column(name = "product_image_url", length = 1000)
    @Size(max = 1000, message = "Product image URL must not exceed 1000 characters")
    private String productImageUrl;
    
    /**
     * Product variant information (size, color, etc.)
     */
    @Column(name = "variant_info", length = 1000)
    @Size(max = 1000, message = "Variant info must not exceed 1000 characters")
    private String variantInfo;
    
    /**
     * Special instructions for this item
     */
    @Column(name = "special_instructions", length = 1000)
    @Size(max = 1000, message = "Special instructions must not exceed 1000 characters")
    private String specialInstructions;
    
    /**
     * Whether this item is a gift
     */
    @Column(name = "is_gift")
    @Builder.Default
    private Boolean isGift = false;
    
    /**
     * Gift wrap type if this is a gift
     */
    @Column(name = "gift_wrap_type", length = 100)
    @Size(max = 100, message = "Gift wrap type must not exceed 100 characters")
    private String giftWrapType;
    
    /**
     * Gift message for this item
     */
    @Column(name = "gift_message", length = 500)
    @Size(max = 500, message = "Gift message must not exceed 500 characters")
    private String giftMessage;
    
    /**
     * Whether this item requires special handling
     */
    @Column(name = "requires_special_handling")
    @Builder.Default
    private Boolean requiresSpecialHandling = false;
    
    /**
     * Whether this item is fragile
     */
    @Column(name = "is_fragile")
    @Builder.Default
    private Boolean isFragile = false;
    
    /**
     * Whether this item is hazardous
     */
    @Column(name = "is_hazardous")
    @Builder.Default
    private Boolean isHazardous = false;
    
    /**
     * Expected delivery date for this item
     */
    @Column(name = "expected_delivery_date")
    private LocalDateTime expectedDeliveryDate;
    
    /**
     * Actual delivery date for this item
     */
    @Column(name = "actual_delivery_date")
    private LocalDateTime actualDeliveryDate;
    
    /**
     * Item status (PENDING, CONFIRMED, SHIPPED, DELIVERED, etc.)
     */
    @Column(name = "status", length = 30)
    @Size(max = 30, message = "Status must not exceed 30 characters")
    @Builder.Default
    private String status = "PENDING";
    
    /**
     * Soft delete timestamp
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    // ==================== Business Methods ====================
    
    /**
     * Calculate the final price after discount
     * 
     * @return final price after discount
     */
    public Money getFinalPrice() {
        if (discountAmount != null && discountAmount.isPositive()) {
            return totalPrice.subtract(discountAmount);
        }
        return totalPrice;
    }
    
    /**
     * Calculate the price including tax
     * 
     * @return price including tax
     */
    public Money getPriceIncludingTax() {
        Money finalPrice = getFinalPrice();
        if (taxAmount != null && taxAmount.isPositive()) {
            return finalPrice.add(taxAmount);
        }
        return finalPrice;
    }
    
    /**
     * Calculate discount percentage
     * 
     * @return discount percentage
     */
    public java.math.BigDecimal getDiscountPercentage() {
        if (discountAmount != null && discountAmount.isPositive() && 
            totalPrice != null && totalPrice.isPositive()) {
            return discountAmount.getAmount()
                    .divide(totalPrice.getAmount(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(java.math.BigDecimal.valueOf(100));
        }
        return java.math.BigDecimal.ZERO;
    }
    
    /**
     * Check if this item has been delivered
     * 
     * @return true if delivered
     */
    public boolean isDelivered() {
        return "DELIVERED".equals(status) && actualDeliveryDate != null;
    }
    
    /**
     * Check if this item is overdue
     * 
     * @return true if overdue
     */
    public boolean isOverdue() {
        return expectedDeliveryDate != null && 
               LocalDateTime.now().isAfter(expectedDeliveryDate) && 
               !isDelivered();
    }
    
    /**
     * Calculate total weight for this line item
     * 
     * @return total weight (quantity * unit weight)
     */
    public java.math.BigDecimal getTotalWeight() {
        if (weight != null && quantity != null) {
            return weight.multiply(java.math.BigDecimal.valueOf(quantity));
        }
        return java.math.BigDecimal.ZERO;
    }
    
    /**
     * Update the total price based on quantity and unit price
     */
    public void updateTotalPrice() {
        if (unitPrice != null && quantity != null) {
            this.totalPrice = unitPrice.multiply(quantity);
        }
    }
    
    /**
     * Mark this item as delivered
     * 
     * @param deliveryDate the delivery date
     */
    public void markAsDelivered(LocalDateTime deliveryDate) {
        this.status = "DELIVERED";
        this.actualDeliveryDate = deliveryDate != null ? deliveryDate : LocalDateTime.now();
    }
    
    /**
     * Soft delete this item
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
    
    /**
     * Check if this item is soft deleted
     * 
     * @return true if soft deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
