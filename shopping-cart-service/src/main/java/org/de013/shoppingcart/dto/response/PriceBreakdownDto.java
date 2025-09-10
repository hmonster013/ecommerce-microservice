package org.de013.shoppingcart.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for detailed price breakdown
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Schema(description = "Detailed price breakdown for cart")
public class PriceBreakdownDto {

    @Schema(description = "Items subtotal", example = "99.98")
    @JsonProperty("items_subtotal")
    private BigDecimal itemsSubtotal;

    @Schema(description = "Product discounts", example = "10.00")
    @JsonProperty("product_discounts")
    private BigDecimal productDiscounts;

    @Schema(description = "Cart-level discounts", example = "5.00")
    @JsonProperty("cart_discounts")
    private BigDecimal cartDiscounts;

    @Schema(description = "Coupon discounts", example = "15.00")
    @JsonProperty("coupon_discounts")
    private BigDecimal couponDiscounts;

    @Schema(description = "Loyalty point discounts", example = "5.00")
    @JsonProperty("loyalty_discounts")
    private BigDecimal loyaltyDiscounts;

    @Schema(description = "Subtotal after discounts", example = "84.98")
    @JsonProperty("subtotal_after_discounts")
    private BigDecimal subtotalAfterDiscounts;

    @Schema(description = "Base shipping cost", example = "9.99")
    @JsonProperty("base_shipping_cost")
    private BigDecimal baseShippingCost;

    @Schema(description = "Shipping discounts", example = "0.00")
    @JsonProperty("shipping_discounts")
    private BigDecimal shippingDiscounts;

    @Schema(description = "Final shipping cost", example = "9.99")
    @JsonProperty("final_shipping_cost")
    private BigDecimal finalShippingCost;

    @Schema(description = "Tax breakdown by type")
    @JsonProperty("tax_breakdown")
    private List<TaxLineDto> taxBreakdown;

    @Schema(description = "Total tax amount", example = "6.80")
    @JsonProperty("total_tax")
    private BigDecimal totalTax;

    @Schema(description = "Additional fees breakdown")
    @JsonProperty("fees_breakdown")
    private List<FeeLineDto> feesBreakdown;

    @Schema(description = "Total fees", example = "5.99")
    @JsonProperty("total_fees")
    private BigDecimal totalFees;

    @Schema(description = "Gift wrap costs", example = "12.99")
    @JsonProperty("gift_wrap_costs")
    private BigDecimal giftWrapCosts;

    @Schema(description = "Insurance costs", example = "3.99")
    @JsonProperty("insurance_costs")
    private BigDecimal insuranceCosts;

    @Schema(description = "Final total amount", example = "123.75")
    @JsonProperty("final_total")
    private BigDecimal finalTotal;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Total savings amount", example = "30.00")
    @JsonProperty("total_savings")
    private BigDecimal totalSavings;

    @Schema(description = "Original total before any discounts", example = "153.75")
    @JsonProperty("original_total")
    private BigDecimal originalTotal;

    @Schema(description = "Savings percentage", example = "19.51")
    @JsonProperty("savings_percentage")
    private BigDecimal savingsPercentage;

    @Schema(description = "Detailed discount breakdown")
    @JsonProperty("discount_breakdown")
    private List<DiscountLineDto> discountBreakdown;

    @Schema(description = "Applied promotions")
    @JsonProperty("applied_promotions")
    private List<PromotionLineDto> appliedPromotions;

    /**
     * Tax line item DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Tax line item")
    public static class TaxLineDto {

        @Schema(description = "Tax type", example = "Sales Tax")
        @JsonProperty("tax_type")
        private String taxType;

        @Schema(description = "Tax jurisdiction", example = "New York State")
        private String jurisdiction;

        @Schema(description = "Tax rate", example = "0.08")
        @JsonProperty("tax_rate")
        private BigDecimal taxRate;

        @Schema(description = "Taxable amount", example = "84.98")
        @JsonProperty("taxable_amount")
        private BigDecimal taxableAmount;

        @Schema(description = "Tax amount", example = "6.80")
        @JsonProperty("tax_amount")
        private BigDecimal taxAmount;

        @Schema(description = "Tax description", example = "NY State Sales Tax")
        private String description;
    }

    /**
     * Fee line item DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Fee line item")
    public static class FeeLineDto {

        @Schema(description = "Fee type", example = "HANDLING")
        @JsonProperty("fee_type")
        private String feeType;

        @Schema(description = "Fee name", example = "Handling Fee")
        @JsonProperty("fee_name")
        private String feeName;

        @Schema(description = "Fee amount", example = "2.99")
        @JsonProperty("fee_amount")
        private BigDecimal feeAmount;

        @Schema(description = "Fee description", example = "Order processing and handling")
        private String description;

        @Schema(description = "Whether fee is optional", example = "false")
        @JsonProperty("is_optional")
        private Boolean isOptional;
    }

    /**
     * Discount line item DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Discount line item")
    public static class DiscountLineDto {

        @Schema(description = "Discount type", example = "COUPON")
        @JsonProperty("discount_type")
        private String discountType;

        @Schema(description = "Discount name", example = "20% Off Coupon")
        @JsonProperty("discount_name")
        private String discountName;

        @Schema(description = "Discount code", example = "SAVE20")
        @JsonProperty("discount_code")
        private String discountCode;

        @Schema(description = "Discount amount", example = "15.00")
        @JsonProperty("discount_amount")
        private BigDecimal discountAmount;

        @Schema(description = "Discount description", example = "20% off all items")
        private String description;

        @Schema(description = "Items affected by this discount")
        @JsonProperty("affected_items")
        private List<String> affectedItems;
    }

    /**
     * Promotion line item DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Promotion line item")
    public static class PromotionLineDto {

        @Schema(description = "Promotion ID", example = "promo-123")
        @JsonProperty("promotion_id")
        private String promotionId;

        @Schema(description = "Promotion name", example = "Holiday Sale")
        @JsonProperty("promotion_name")
        private String promotionName;

        @Schema(description = "Promotion type", example = "PERCENTAGE_OFF")
        @JsonProperty("promotion_type")
        private String promotionType;

        @Schema(description = "Promotion value", example = "20.00")
        @JsonProperty("promotion_value")
        private BigDecimal promotionValue;

        @Schema(description = "Promotion description", example = "20% off holiday items")
        private String description;

        @Schema(description = "Promotion savings", example = "15.00")
        @JsonProperty("promotion_savings")
        private BigDecimal promotionSavings;
    }

    /**
     * Calculate total discounts
     */
    @JsonIgnore
    public BigDecimal getTotalDiscounts() {
        BigDecimal total = BigDecimal.ZERO;
        
        if (productDiscounts != null) total = total.add(productDiscounts);
        if (cartDiscounts != null) total = total.add(cartDiscounts);
        if (couponDiscounts != null) total = total.add(couponDiscounts);
        if (loyaltyDiscounts != null) total = total.add(loyaltyDiscounts);
        if (shippingDiscounts != null) total = total.add(shippingDiscounts);
        
        return total;
    }

    /**
     * Calculate savings percentage
     */
    @JsonIgnore
    public BigDecimal calculateSavingsPercentage() {
        if (originalTotal != null && originalTotal.compareTo(BigDecimal.ZERO) > 0 && 
            totalSavings != null && totalSavings.compareTo(BigDecimal.ZERO) > 0) {
            return totalSavings.divide(originalTotal, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Check if there are any discounts applied
     */
    @JsonIgnore
    public boolean hasDiscounts() {
        return getTotalDiscounts().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if there are any fees
     */
    @JsonIgnore
    public boolean hasFees() {
        return totalFees != null && totalFees.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Get net amount (subtotal after discounts, before taxes and fees)
     */
    @JsonIgnore
    public BigDecimal getNetAmount() {
        return subtotalAfterDiscounts != null ? subtotalAfterDiscounts : BigDecimal.ZERO;
    }
}
