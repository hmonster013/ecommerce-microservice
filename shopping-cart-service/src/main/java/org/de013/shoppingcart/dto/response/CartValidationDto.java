package org.de013.shoppingcart.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for cart validation response
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Schema(description = "Cart validation status and messages")
public class CartValidationDto {

    @Schema(description = "Overall validation status", example = "VALID", allowableValues = {"VALID", "WARNING", "ERROR"})
    @JsonProperty("validation_status")
    private String validationStatus;

    @Schema(description = "Whether cart is valid for checkout", example = "true")
    @JsonProperty("is_valid")
    private Boolean isValid;

    @Schema(description = "Whether cart has warnings", example = "false")
    @JsonProperty("has_warnings")
    private Boolean hasWarnings;

    @Schema(description = "Whether cart has errors", example = "false")
    @JsonProperty("has_errors")
    private Boolean hasErrors;

    @Schema(description = "Validation timestamp", example = "2024-01-01T15:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("validation_timestamp")
    private LocalDateTime validationTimestamp;

    @Schema(description = "General validation messages")
    @JsonProperty("general_messages")
    private List<ValidationMessageDto> generalMessages;

    @Schema(description = "Item-specific validation messages")
    @JsonProperty("item_messages")
    private List<ItemValidationDto> itemMessages;

    @Schema(description = "Pricing validation results")
    @JsonProperty("pricing_validation")
    private PricingValidationDto pricingValidation;

    @Schema(description = "Inventory validation results")
    @JsonProperty("inventory_validation")
    private InventoryValidationDto inventoryValidation;

    @Schema(description = "Shipping validation results")
    @JsonProperty("shipping_validation")
    private ShippingValidationDto shippingValidation;

    @Schema(description = "Coupon validation results")
    @JsonProperty("coupon_validation")
    private CouponValidationDto couponValidation;

    @Schema(description = "Total number of validation issues", example = "0")
    @JsonProperty("total_issues")
    private Integer totalIssues;

    @Schema(description = "Number of errors", example = "0")
    @JsonProperty("error_count")
    private Integer errorCount;

    @Schema(description = "Number of warnings", example = "0")
    @JsonProperty("warning_count")
    private Integer warningCount;

    @Schema(description = "Validation score (0-100)", example = "95")
    @JsonProperty("validation_score")
    private Integer validationScore;

    /**
     * Base validation message DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Validation message")
    public static class ValidationMessageDto {

        @Schema(description = "Message type", example = "ERROR", allowableValues = {"ERROR", "WARNING", "INFO"})
        private String type;

        @Schema(description = "Message code", example = "CART_EXPIRED")
        private String code;

        @Schema(description = "Human-readable message", example = "Cart has expired")
        private String message;

        @Schema(description = "Field that caused the validation issue", example = "expires_at")
        private String field;

        @Schema(description = "Suggested action to resolve the issue", example = "Please refresh your cart")
        @JsonProperty("suggested_action")
        private String suggestedAction;
    }

    /**
     * Item-specific validation DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Item validation results")
    public static class ItemValidationDto {

        @Schema(description = "Cart item ID", example = "456")
        @JsonProperty("item_id")
        private Long itemId;

        @Schema(description = "Product ID", example = "prod-123")
        @JsonProperty("product_id")
        private String productId;

        @Schema(description = "Product name", example = "Wireless Headphones")
        @JsonProperty("product_name")
        private String productName;

        @Schema(description = "Item validation status", example = "VALID")
        @JsonProperty("validation_status")
        private String validationStatus;

        @Schema(description = "Validation messages for this item")
        private List<ValidationMessageDto> messages;
    }

    /**
     * Pricing validation DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Pricing validation results")
    public static class PricingValidationDto {

        @Schema(description = "Whether pricing is valid", example = "true")
        @JsonProperty("is_valid")
        private Boolean isValid;

        @Schema(description = "Number of items with price changes", example = "0")
        @JsonProperty("price_changes_count")
        private Integer priceChangesCount;

        @Schema(description = "Items with price changes")
        @JsonProperty("price_changes")
        private List<PriceChangeDto> priceChanges;

        @Schema(description = "Pricing validation messages")
        private List<ValidationMessageDto> messages;

        /**
         * Price change DTO
         */
        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @ToString
        @EqualsAndHashCode
        @Schema(description = "Price change information")
        public static class PriceChangeDto {

            @Schema(description = "Item ID", example = "456")
            @JsonProperty("item_id")
            private Long itemId;

            @Schema(description = "Product name", example = "Wireless Headphones")
            @JsonProperty("product_name")
            private String productName;

            @Schema(description = "Old price", example = "49.99")
            @JsonProperty("old_price")
            private java.math.BigDecimal oldPrice;

            @Schema(description = "New price", example = "44.99")
            @JsonProperty("new_price")
            private java.math.BigDecimal newPrice;

            @Schema(description = "Price change amount", example = "-5.00")
            @JsonProperty("price_change")
            private java.math.BigDecimal priceChange;

            @Schema(description = "Whether price increased", example = "false")
            @JsonProperty("price_increased")
            private Boolean priceIncreased;
        }
    }

    /**
     * Inventory validation DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Inventory validation results")
    public static class InventoryValidationDto {

        @Schema(description = "Whether inventory is sufficient", example = "true")
        @JsonProperty("is_sufficient")
        private Boolean isSufficient;

        @Schema(description = "Number of out-of-stock items", example = "0")
        @JsonProperty("out_of_stock_count")
        private Integer outOfStockCount;

        @Schema(description = "Number of low-stock items", example = "1")
        @JsonProperty("low_stock_count")
        private Integer lowStockCount;

        @Schema(description = "Out-of-stock items")
        @JsonProperty("out_of_stock_items")
        private List<String> outOfStockItems;

        @Schema(description = "Low-stock items")
        @JsonProperty("low_stock_items")
        private List<String> lowStockItems;

        @Schema(description = "Inventory validation messages")
        private List<ValidationMessageDto> messages;
    }

    /**
     * Shipping validation DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Shipping validation results")
    public static class ShippingValidationDto {

        @Schema(description = "Whether shipping is available", example = "true")
        @JsonProperty("is_available")
        private Boolean isAvailable;

        @Schema(description = "Number of unshippable items", example = "0")
        @JsonProperty("unshippable_count")
        private Integer unshippableCount;

        @Schema(description = "Unshippable items")
        @JsonProperty("unshippable_items")
        private List<String> unshippableItems;

        @Schema(description = "Shipping validation messages")
        private List<ValidationMessageDto> messages;
    }

    /**
     * Coupon validation DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Coupon validation results")
    public static class CouponValidationDto {

        @Schema(description = "Applied coupon code", example = "SAVE20")
        @JsonProperty("coupon_code")
        private String couponCode;

        @Schema(description = "Whether coupon is valid", example = "true")
        @JsonProperty("is_valid")
        private Boolean isValid;

        @Schema(description = "Coupon discount amount", example = "20.00")
        @JsonProperty("discount_amount")
        private java.math.BigDecimal discountAmount;

        @Schema(description = "Coupon validation messages")
        private List<ValidationMessageDto> messages;
    }

    /**
     * Check if validation passed without errors
     */
    public boolean isValidationPassed() {
        return Boolean.TRUE.equals(isValid) && !Boolean.TRUE.equals(hasErrors);
    }

    /**
     * Check if validation has any issues
     */
    public boolean hasAnyIssues() {
        return Boolean.TRUE.equals(hasErrors) || Boolean.TRUE.equals(hasWarnings);
    }

    /**
     * Get validation summary
     */
    public String getValidationSummary() {
        if (Boolean.TRUE.equals(isValid) && !hasAnyIssues()) {
            return "Cart is valid and ready for checkout";
        } else if (Boolean.TRUE.equals(hasErrors)) {
            return String.format("Cart has %d error(s) that must be resolved", errorCount);
        } else if (Boolean.TRUE.equals(hasWarnings)) {
            return String.format("Cart has %d warning(s) but can proceed to checkout", warningCount);
        }
        return "Cart validation status unknown";
    }
}
