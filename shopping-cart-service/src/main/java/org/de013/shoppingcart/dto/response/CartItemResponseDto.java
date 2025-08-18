package org.de013.shoppingcart.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for cart item response
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Schema(description = "Cart item response")
public class CartItemResponseDto {

    @Schema(description = "Cart item ID", example = "456")
    @JsonProperty("item_id")
    private Long itemId;

    @Schema(description = "Product ID", example = "prod-123e4567-e89b-12d3-a456-426614174000")
    @JsonProperty("product_id")
    private String productId;

    @Schema(description = "Product SKU", example = "SKU-12345")
    @JsonProperty("product_sku")
    private String productSku;

    @Schema(description = "Product name", example = "Wireless Bluetooth Headphones")
    @JsonProperty("product_name")
    private String productName;

    @Schema(description = "Product description", example = "High-quality wireless headphones with noise cancellation")
    @JsonProperty("product_description")
    private String productDescription;

    @Schema(description = "Product image URL", example = "https://example.com/images/headphones.jpg")
    @JsonProperty("product_image_url")
    private String productImageUrl;

    @Schema(description = "Category ID", example = "cat-electronics")
    @JsonProperty("category_id")
    private String categoryId;

    @Schema(description = "Category name", example = "Electronics")
    @JsonProperty("category_name")
    private String categoryName;

    @Schema(description = "Quantity", example = "2")
    private Integer quantity;

    @Schema(description = "Unit price", example = "49.99")
    @JsonProperty("unit_price")
    private BigDecimal unitPrice;

    @Schema(description = "Original price (before discounts)", example = "59.99")
    @JsonProperty("original_price")
    private BigDecimal originalPrice;

    @Schema(description = "Discount amount per unit", example = "10.00")
    @JsonProperty("discount_amount")
    private BigDecimal discountAmount;

    @Schema(description = "Total price for this item", example = "99.98")
    @JsonProperty("total_price")
    private BigDecimal totalPrice;

    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @Schema(description = "Product weight", example = "0.350")
    private BigDecimal weight;

    @Schema(description = "Product dimensions", example = "20x15x8 cm")
    private String dimensions;

    @Schema(description = "Product variant ID", example = "var-color-black")
    @JsonProperty("variant_id")
    private String variantId;

    @Schema(description = "Variant attributes", example = "Color: Black, Size: Medium")
    @JsonProperty("variant_attributes")
    private String variantAttributes;

    @Schema(description = "Special instructions", example = "Handle with care")
    @JsonProperty("special_instructions")
    private String specialInstructions;

    @Schema(description = "Date when item was added to cart", example = "2024-01-01T10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("added_at")
    private LocalDateTime addedAt;

    @Schema(description = "Last price check timestamp", example = "2024-01-01T15:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("last_price_check_at")
    private LocalDateTime lastPriceCheckAt;

    @Schema(description = "Whether price has changed since adding to cart", example = "false")
    @JsonProperty("price_changed")
    private Boolean priceChanged;

    @Schema(description = "Availability status", example = "AVAILABLE")
    @JsonProperty("availability_status")
    private String availabilityStatus;

    @Schema(description = "Stock quantity available", example = "25")
    @JsonProperty("stock_quantity")
    private Integer stockQuantity;

    @Schema(description = "Maximum quantity allowed per order", example = "10")
    @JsonProperty("max_quantity_per_order")
    private Integer maxQuantityPerOrder;

    @Schema(description = "Whether this item is marked as a gift", example = "false")
    @JsonProperty("is_gift")
    private Boolean isGift;

    @Schema(description = "Gift message", example = "Happy Birthday!")
    @JsonProperty("gift_message")
    private String giftMessage;

    @Schema(description = "Gift wrap type", example = "premium")
    @JsonProperty("gift_wrap_type")
    private String giftWrapType;

    @Schema(description = "Gift wrap price", example = "5.99")
    @JsonProperty("gift_wrap_price")
    private BigDecimal giftWrapPrice;

    @Schema(description = "Product URL for viewing details", example = "/products/prod-123")
    @JsonProperty("product_url")
    private String productUrl;

    @Schema(description = "Whether item is available for purchase", example = "true")
    @JsonProperty("is_available")
    private Boolean isAvailable;

    @Schema(description = "Whether requested quantity is available", example = "true")
    @JsonProperty("is_quantity_available")
    private Boolean isQuantityAvailable;

    @Schema(description = "Whether quantity exceeds maximum allowed", example = "false")
    @JsonProperty("exceeds_max_quantity")
    private Boolean exceedsMaxQuantity;

    @Schema(description = "Discount percentage", example = "16.67")
    @JsonProperty("discount_percentage")
    private BigDecimal discountPercentage;

    @Schema(description = "Total savings for this item", example = "20.00")
    @JsonProperty("total_savings")
    private BigDecimal totalSavings;

    @Schema(description = "Estimated delivery date for this item", example = "2024-01-05T00:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    @Schema(description = "Item validation messages")
    @JsonProperty("validation_messages")
    private java.util.List<String> validationMessages;

    @Schema(description = "Related products or accessories")
    @JsonProperty("related_products")
    private java.util.List<String> relatedProducts;

    @Schema(description = "Item tags or labels")
    private java.util.List<String> tags;

    /**
     * Calculate discount percentage
     */
    public BigDecimal calculateDiscountPercentage() {
        if (originalPrice != null && originalPrice.compareTo(BigDecimal.ZERO) > 0 && 
            discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            return discountAmount.divide(originalPrice, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Calculate total savings (discount * quantity)
     */
    public BigDecimal calculateTotalSavings() {
        if (discountAmount != null && quantity != null) {
            return discountAmount.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Check if item has any validation issues
     */
    public boolean hasValidationIssues() {
        return validationMessages != null && !validationMessages.isEmpty();
    }

    /**
     * Check if item is on sale
     */
    public boolean isOnSale() {
        return discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if item has gift options
     */
    public boolean hasGiftOptions() {
        return Boolean.TRUE.equals(isGift) && 
               (giftMessage != null || giftWrapType != null);
    }

    /**
     * Get effective price (unit price after discount)
     */
    public BigDecimal getEffectivePrice() {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        if (discountAmount != null) {
            return unitPrice.subtract(discountAmount);
        }
        return unitPrice;
    }

    /**
     * Check if item is low in stock
     */
    public boolean isLowStock() {
        return stockQuantity != null && stockQuantity <= 5;
    }

    /**
     * Check if item is out of stock
     */
    public boolean isOutOfStock() {
        return stockQuantity != null && stockQuantity <= 0;
    }
}
