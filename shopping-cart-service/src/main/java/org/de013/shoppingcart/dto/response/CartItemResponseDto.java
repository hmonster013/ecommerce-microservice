package org.de013.shoppingcart.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for cart item response
 */
@Builder
@Schema(description = "Cart item response")
public record CartItemResponseDto(

    @Schema(description = "Cart item ID", example = "456")
    @JsonProperty("item_id")
    Long itemId,

    @Schema(description = "Product ID", example = "prod-123e4567-e89b-12d3-a456-426614174000")
    @JsonProperty("product_id")
    String productId,

    @Schema(description = "Product SKU", example = "SKU-12345")
    @JsonProperty("product_sku")
    String productSku,

    @Schema(description = "Product name", example = "Wireless Bluetooth Headphones")
    @JsonProperty("product_name")
    String productName,

    @Schema(description = "Product description", example = "High-quality wireless headphones with noise cancellation")
    @JsonProperty("product_description")
    String productDescription,

    @Schema(description = "Product image URL", example = "https://example.com/images/headphones.jpg")
    @JsonProperty("product_image_url")
    String productImageUrl,

    @Schema(description = "Category ID", example = "cat-electronics")
    @JsonProperty("category_id")
    String categoryId,

    @Schema(description = "Category name", example = "Electronics")
    @JsonProperty("category_name")
    String categoryName,

    @Schema(description = "Quantity", example = "2")
    Integer quantity,

    @Schema(description = "Unit price", example = "49.99")
    @JsonProperty("unit_price")
    BigDecimal unitPrice,

    @Schema(description = "Original price (before discounts)", example = "59.99")
    @JsonProperty("original_price")
    BigDecimal originalPrice,

    @Schema(description = "Discount amount per unit", example = "10.00")
    @JsonProperty("discount_amount")
    BigDecimal discountAmount,

    @Schema(description = "Total price for this item", example = "99.98")
    @JsonProperty("total_price")
    BigDecimal totalPrice,

    @Schema(description = "Currency code", example = "USD")
    String currency,

    @Schema(description = "Product weight", example = "0.350")
    BigDecimal weight,

    @Schema(description = "Product dimensions", example = "20x15x8 cm")
    String dimensions,

    @Schema(description = "Product variant ID", example = "var-color-black")
    @JsonProperty("variant_id")
    String variantId,

    @Schema(description = "Variant attributes", example = "Color: Black, Size: Medium")
    @JsonProperty("variant_attributes")
    String variantAttributes,

    @Schema(description = "Special instructions", example = "Handle with care")
    @JsonProperty("special_instructions")
    String specialInstructions,

    @Schema(description = "Date when item was added to cart", example = "2024-01-01T10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("added_at")
    LocalDateTime addedAt,

    @Schema(description = "Last price check timestamp", example = "2024-01-01T15:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("last_price_check_at")
    LocalDateTime lastPriceCheckAt,

    @Schema(description = "Whether price has changed since adding to cart", example = "false")
    @JsonProperty("price_changed")
    Boolean priceChanged,

    @Schema(description = "Availability status", example = "AVAILABLE")
    @JsonProperty("availability_status")
    String availabilityStatus,

    @Schema(description = "Stock quantity available", example = "25")
    @JsonProperty("stock_quantity")
    Integer stockQuantity,

    @Schema(description = "Maximum quantity allowed per order", example = "10")
    @JsonProperty("max_quantity_per_order")
    Integer maxQuantityPerOrder,

    @Schema(description = "Whether this item is marked as a gift", example = "false")
    @JsonProperty("is_gift")
    Boolean isGift,

    @Schema(description = "Gift message", example = "Happy Birthday!")
    @JsonProperty("gift_message")
    String giftMessage,

    @Schema(description = "Gift wrap type", example = "premium")
    @JsonProperty("gift_wrap_type")
    String giftWrapType,

    @Schema(description = "Gift wrap price", example = "5.99")
    @JsonProperty("gift_wrap_price")
    BigDecimal giftWrapPrice,

    @Schema(description = "Product URL for viewing details", example = "/products/prod-123")
    @JsonProperty("product_url")
    String productUrl,

    @Schema(description = "Whether item is available for purchase", example = "true")
    @JsonProperty("is_available")
    Boolean isAvailable,

    @Schema(description = "Whether requested quantity is available", example = "true")
    @JsonProperty("is_quantity_available")
    Boolean isQuantityAvailable,

    @Schema(description = "Whether quantity exceeds maximum allowed", example = "false")
    @JsonProperty("exceeds_max_quantity")
    Boolean exceedsMaxQuantity,

    @Schema(description = "Discount percentage", example = "16.67")
    @JsonProperty("discount_percentage")
    BigDecimal discountPercentage,

    @Schema(description = "Total savings for this item", example = "20.00")
    @JsonProperty("total_savings")
    BigDecimal totalSavings,

    @Schema(description = "Estimated delivery date for this item", example = "2024-01-05T00:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @JsonProperty("estimated_delivery_date")
    LocalDateTime estimatedDeliveryDate,

    @Schema(description = "Item validation messages")
    @JsonProperty("validation_messages")
    java.util.List<String> validationMessages,

    @Schema(description = "Related products or accessories")
    @JsonProperty("related_products")
    java.util.List<String> relatedProducts,

    @Schema(description = "Item tags or labels")
    java.util.List<String> tags,

    @Schema(description = "Product brand", example = "Apple")
    @JsonProperty("product_brand")
    String productBrand
) {

    /**
     * Calculate discount percentage
     */
    @JsonIgnore
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
    @JsonIgnore
    public BigDecimal calculateTotalSavings() {
        if (discountAmount != null && quantity != null) {
            return discountAmount.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }

    /**
     * Check if item has any validation issues
     */
    @JsonIgnore
    public boolean hasValidationIssues() {
        return validationMessages != null && !validationMessages.isEmpty();
    }

    /**
     * Check if item is on sale
     */
    @JsonIgnore
    public boolean isOnSale() {
        return discountAmount != null && discountAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if item has gift options
     */
    @JsonIgnore
    public boolean hasGiftOptions() {
        return Boolean.TRUE.equals(isGift) &&
                (giftMessage != null || giftWrapType != null);
    }

    /**
     * Get effective price (unit price after discount)
     */
    @JsonIgnore
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
    @JsonIgnore
    public boolean isLowStock() {
        return stockQuantity != null && stockQuantity <= 5;
    }

    /**
     * Check if item is out of stock
     */
    @JsonIgnore
    public boolean isOutOfStock() {
        return stockQuantity != null && stockQuantity <= 0;
    }
}
