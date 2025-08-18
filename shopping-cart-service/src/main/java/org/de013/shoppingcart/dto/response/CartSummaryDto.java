package org.de013.shoppingcart.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for cart summary response
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Schema(description = "Cart summary with detailed pricing breakdown")
public class CartSummaryDto {

    @Schema(description = "Cart subtotal (before taxes and fees)", example = "99.98")
    private BigDecimal subtotal;

    @Schema(description = "Tax rate applied", example = "0.08")
    @JsonProperty("tax_rate")
    private BigDecimal taxRate;

    @Schema(description = "Tax amount", example = "8.00")
    @JsonProperty("tax_amount")
    private BigDecimal taxAmount;

    @Schema(description = "Shipping cost", example = "9.99")
    @JsonProperty("shipping_cost")
    private BigDecimal shippingCost;

    @Schema(description = "Shipping method", example = "Standard Shipping")
    @JsonProperty("shipping_method")
    private String shippingMethod;

    @Schema(description = "Estimated shipping days", example = "5")
    @JsonProperty("shipping_estimated_days")
    private Integer shippingEstimatedDays;

    @Schema(description = "Total discount amount", example = "15.00")
    @JsonProperty("discount_amount")
    private BigDecimal discountAmount;

    @Schema(description = "Discount type", example = "PERCENTAGE")
    @JsonProperty("discount_type")
    private String discountType;

    @Schema(description = "Applied discount code", example = "SAVE20")
    @JsonProperty("discount_code")
    private String discountCode;

    @Schema(description = "Discount description", example = "20% off all items")
    @JsonProperty("discount_description")
    private String discountDescription;

    @Schema(description = "Loyalty points earned from this order", example = "100")
    @JsonProperty("loyalty_points_earned")
    private Integer loyaltyPointsEarned;

    @Schema(description = "Loyalty points used for discount", example = "50")
    @JsonProperty("loyalty_points_used")
    private Integer loyaltyPointsUsed;

    @Schema(description = "Discount amount from loyalty points", example = "5.00")
    @JsonProperty("loyalty_discount_amount")
    private BigDecimal loyaltyDiscountAmount;

    @Schema(description = "Gift wrap cost", example = "12.99")
    @JsonProperty("gift_wrap_cost")
    private BigDecimal giftWrapCost;

    @Schema(description = "Handling fee", example = "2.99")
    @JsonProperty("handling_fee")
    private BigDecimal handlingFee;

    @Schema(description = "Insurance cost", example = "3.99")
    @JsonProperty("insurance_cost")
    private BigDecimal insuranceCost;

    @Schema(description = "Final total amount", example = "119.95")
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Number of unique items", example = "3")
    @JsonProperty("item_count")
    private Integer itemCount;

    @Schema(description = "Total quantity of all items", example = "5")
    @JsonProperty("total_quantity")
    private Integer totalQuantity;

    @Schema(description = "Total weight of all items", example = "2.450")
    @JsonProperty("total_weight")
    private BigDecimal totalWeight;

    @Schema(description = "Estimated delivery date", example = "2024-01-05T00:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    @Schema(description = "Calculation timestamp", example = "2024-01-01T15:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("calculation_timestamp")
    private LocalDateTime calculationTimestamp;

    @Schema(description = "Applied pricing rules", example = "BULK_DISCOUNT,FIRST_TIME_BUYER")
    @JsonProperty("pricing_rules_applied")
    private String pricingRulesApplied;

    @Schema(description = "Applied promotion codes", example = "SAVE20,FREESHIP")
    @JsonProperty("promotion_codes_applied")
    private String promotionCodesApplied;

    @Schema(description = "Whether eligible for free shipping", example = "false")
    @JsonProperty("is_free_shipping_eligible")
    private Boolean isFreeShippingEligible;

    @Schema(description = "Free shipping threshold", example = "100.00")
    @JsonProperty("free_shipping_threshold")
    private BigDecimal freeShippingThreshold;

    @Schema(description = "Amount needed to qualify for free shipping", example = "0.02")
    @JsonProperty("amount_needed_for_free_shipping")
    private BigDecimal amountNeededForFreeShipping;

    @Schema(description = "Total savings amount", example = "25.00")
    @JsonProperty("savings_amount")
    private BigDecimal savingsAmount;

    @Schema(description = "Original total before discounts", example = "144.95")
    @JsonProperty("original_total")
    private BigDecimal originalTotal;

    @Schema(description = "Detailed price breakdown")
    @JsonProperty("price_breakdown")
    private PriceBreakdownDto priceBreakdown;

    @Schema(description = "Available payment methods")
    @JsonProperty("available_payment_methods")
    private List<PaymentMethodDto> availablePaymentMethods;

    @Schema(description = "Tax breakdown by jurisdiction")
    @JsonProperty("tax_breakdown")
    private List<TaxBreakdownDto> taxBreakdown;

    /**
     * Nested DTO for payment method information
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Available payment method")
    public static class PaymentMethodDto {

        @Schema(description = "Payment method ID", example = "credit_card")
        @JsonProperty("method_id")
        private String methodId;

        @Schema(description = "Payment method name", example = "Credit Card")
        private String name;

        @Schema(description = "Payment method description", example = "Visa, MasterCard, American Express")
        private String description;

        @Schema(description = "Processing fee", example = "2.99")
        @JsonProperty("processing_fee")
        private BigDecimal processingFee;

        @Schema(description = "Whether this method is recommended", example = "true")
        @JsonProperty("is_recommended")
        private Boolean isRecommended;

        @Schema(description = "Supported currencies")
        @JsonProperty("supported_currencies")
        private List<String> supportedCurrencies;
    }

    /**
     * Nested DTO for tax breakdown
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Tax breakdown by jurisdiction")
    public static class TaxBreakdownDto {

        @Schema(description = "Tax jurisdiction", example = "New York State")
        private String jurisdiction;

        @Schema(description = "Tax type", example = "Sales Tax")
        @JsonProperty("tax_type")
        private String taxType;

        @Schema(description = "Tax rate", example = "0.08")
        @JsonProperty("tax_rate")
        private BigDecimal taxRate;

        @Schema(description = "Tax amount", example = "8.00")
        @JsonProperty("tax_amount")
        private BigDecimal taxAmount;

        @Schema(description = "Taxable amount", example = "99.98")
        @JsonProperty("taxable_amount")
        private BigDecimal taxableAmount;
    }

    /**
     * Get total discount amount (including loyalty discount)
     */
    public BigDecimal getTotalDiscountAmount() {
        BigDecimal total = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        if (loyaltyDiscountAmount != null) {
            total = total.add(loyaltyDiscountAmount);
        }
        return total;
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

    /**
     * Check if cart qualifies for free shipping
     */
    public boolean qualifiesForFreeShipping() {
        return Boolean.TRUE.equals(isFreeShippingEligible);
    }

    /**
     * Check if cart has any discounts applied
     */
    public boolean hasDiscounts() {
        return getTotalDiscountAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Get effective shipping cost (considering free shipping)
     */
    public BigDecimal getEffectiveShippingCost() {
        if (qualifiesForFreeShipping()) {
            return BigDecimal.ZERO;
        }
        return shippingCost != null ? shippingCost : BigDecimal.ZERO;
    }

    /**
     * Calculate total fees (handling + insurance)
     */
    public BigDecimal getTotalFees() {
        BigDecimal total = BigDecimal.ZERO;
        if (handlingFee != null) {
            total = total.add(handlingFee);
        }
        if (insuranceCost != null) {
            total = total.add(insuranceCost);
        }
        if (giftWrapCost != null) {
            total = total.add(giftWrapCost);
        }
        return total;
    }
}
