package org.de013.shoppingcart.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Builder;


/**
 * DTO for updating cart items
 */
@Builder
@Schema(description = "Request to update cart item")
public record UpdateCartItemDto(


    @Schema(description = "New quantity for the item", example = "3", minimum = "1", maximum = "99")
    @Min(value = 1, message = "{quantity.min}")
    @Max(value = 99, message = "{quantity.max}")
    Integer quantity,


    @Schema(description = "Updated special instructions", example = "Please handle with care")
    @Size(max = 500, message = "{instructions.size}")
    @JsonProperty("special_instructions")
    String specialInstructions,

    @Schema(description = "Update gift status", example = "true")
    @JsonProperty("is_gift")
    Boolean isGift,

    @Schema(description = "Updated gift message", example = "Congratulations!")
    @Size(max = 500, message = "{gift.message.size}")
    @JsonProperty("gift_message")
    String giftMessage,

    @Schema(description = "Updated gift wrap type", example = "luxury", allowableValues = {"basic", "premium", "luxury"})
    @Size(max = 50, message = "{gift.wrap.size}")
    @JsonProperty("gift_wrap_type")
    String giftWrapType,


    @Schema(description = "Validate price against current product price", example = "true")
    @JsonProperty("validate_price")
    Boolean validatePrice,

    @Schema(description = "Update operation type", example = "QUANTITY", allowableValues = {"QUANTITY", "GIFT_OPTIONS", "INSTRUCTIONS", "ALL"})
    @JsonProperty("update_type")
    String updateType,

    @Schema(description = "Refresh price from Product Catalog", example = "false")
    @JsonProperty("refresh_price")
    Boolean refreshPrice
) {
    public UpdateCartItemDto {
        if (validatePrice == null) {
            validatePrice = true;
        }
        if (updateType == null) {
            updateType = "ALL";
        }
        if (refreshPrice == null) {
            refreshPrice = false;
        }
    }



    /**
     * Validate gift message when gift status is true
     */
    @JsonIgnore
    @AssertTrue(message = "{gift.message.required}")
    public boolean isValidGiftMessage() {
        if (Boolean.TRUE.equals(isGift)) {
            return giftMessage != null && !giftMessage.trim().isEmpty();
        }
        return true;
    }

    /**
     * Validate that at least one field is being updated
     */
    @JsonIgnore
    @AssertTrue(message = "{update.field.required}")
    public boolean hasUpdateFields() {
        return quantity != null || specialInstructions != null ||
                isGift != null || giftMessage != null || giftWrapType != null;
    }


    /**
     * Check if quantity is being updated
     */
    @JsonIgnore
    public boolean isQuantityUpdate() {
        return quantity != null;
    }

    /**
     * Check if price is being updated
     * Note: Price updates are no longer supported from client for security reasons
     */
    @JsonIgnore
    public boolean isPriceUpdate() {
        return false; // Always false - prices are managed by Product Catalog Service
    }

    /**
     * Check if gift options are being updated
     */
    @JsonIgnore
    public boolean isGiftOptionsUpdate() {
        return isGift != null || giftMessage != null || giftWrapType != null;
    }

    /**
     * Check if price should be refreshed from Product Catalog
     */
    @JsonIgnore
    public boolean isRefreshPrice() {
        return Boolean.TRUE.equals(refreshPrice);
    }
}
