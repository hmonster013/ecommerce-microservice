package org.de013.shoppingcart.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CartSummary Entity
 * Represents a summary/snapshot of cart calculations and pricing
 */
@Entity
@Table(name = "cart_summaries", indexes = {
    @Index(name = "idx_cart_summary_cart_id", columnList = "cart_id"),
    @Index(name = "idx_cart_summary_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"cart"})
@EqualsAndHashCode(callSuper = true, exclude = {"cart"})
public class CartSummary extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cart_summary_cart"))
    private Cart cart;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_rate", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "shipping_cost", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "shipping_method", length = 100)
    private String shippingMethod;

    @Column(name = "shipping_estimated_days")
    private Integer shippingEstimatedDays;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_type", length = 20)
    private String discountType; // PERCENTAGE, FIXED_AMOUNT, FREE_SHIPPING

    @Column(name = "discount_code", length = 50)
    private String discountCode;

    @Column(name = "discount_description", length = 255)
    private String discountDescription;

    @Column(name = "loyalty_points_earned")
    private Integer loyaltyPointsEarned;

    @Column(name = "loyalty_points_used")
    private Integer loyaltyPointsUsed;

    @Column(name = "loyalty_discount_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal loyaltyDiscountAmount = BigDecimal.ZERO;

    @Column(name = "gift_wrap_cost", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal giftWrapCost = BigDecimal.ZERO;

    @Column(name = "handling_fee", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal handlingFee = BigDecimal.ZERO;

    @Column(name = "insurance_cost", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal insuranceCost = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "item_count", nullable = false)
    @Builder.Default
    private Integer itemCount = 0;

    @Column(name = "total_quantity", nullable = false)
    @Builder.Default
    private Integer totalQuantity = 0;

    @Column(name = "total_weight", precision = 10, scale = 3)
    private BigDecimal totalWeight;

    @Column(name = "estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    @Column(name = "calculation_timestamp", nullable = false)
    private LocalDateTime calculationTimestamp;

    @Column(name = "pricing_rules_applied", length = 1000)
    private String pricingRulesApplied;

    @Column(name = "promotion_codes_applied", length = 500)
    private String promotionCodesApplied;

    @Column(name = "is_free_shipping_eligible", nullable = false)
    @Builder.Default
    private Boolean isFreeShippingEligible = false;

    @Column(name = "free_shipping_threshold", precision = 19, scale = 2)
    private BigDecimal freeShippingThreshold;

    @Column(name = "amount_needed_for_free_shipping", precision = 19, scale = 2)
    private BigDecimal amountNeededForFreeShipping;

    @Column(name = "savings_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal savingsAmount = BigDecimal.ZERO;

    @Column(name = "original_total", precision = 19, scale = 2)
    private BigDecimal originalTotal;

    /**
     * Calculate total amount from all components
     */
    public void calculateTotal() {
        this.totalAmount = subtotal
                .add(taxAmount)
                .add(shippingCost)
                .add(giftWrapCost)
                .add(handlingFee)
                .add(insuranceCost)
                .subtract(discountAmount)
                .subtract(loyaltyDiscountAmount);

        // Ensure total is not negative
        if (this.totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            this.totalAmount = BigDecimal.ZERO;
        }

        this.calculationTimestamp = LocalDateTime.now();
    }

    /**
     * Calculate savings amount
     */
    public void calculateSavings() {
        if (originalTotal != null) {
            this.savingsAmount = originalTotal.subtract(totalAmount);
            if (this.savingsAmount.compareTo(BigDecimal.ZERO) < 0) {
                this.savingsAmount = BigDecimal.ZERO;
            }
        }
    }

    /**
     * Check if eligible for free shipping
     */
    public void checkFreeShippingEligibility() {
        if (freeShippingThreshold != null) {
            this.isFreeShippingEligible = subtotal.compareTo(freeShippingThreshold) >= 0;
            
            if (!isFreeShippingEligible) {
                this.amountNeededForFreeShipping = freeShippingThreshold.subtract(subtotal);
            } else {
                this.amountNeededForFreeShipping = BigDecimal.ZERO;
            }
        }
    }

    /**
     * Get total discount amount (including loyalty discount)
     */
    public BigDecimal getTotalDiscountAmount() {
        return discountAmount.add(loyaltyDiscountAmount);
    }

    /**
     * Get discount percentage based on original total
     */
    public BigDecimal getDiscountPercentage() {
        if (originalTotal != null && originalTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalDiscount = getTotalDiscountAmount();
            return totalDiscount.divide(originalTotal, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }

    @PrePersist
    @PreUpdate
    protected void onSave() {
        calculateTotal();
        calculateSavings();
        checkFreeShippingEligibility();
    }
}
