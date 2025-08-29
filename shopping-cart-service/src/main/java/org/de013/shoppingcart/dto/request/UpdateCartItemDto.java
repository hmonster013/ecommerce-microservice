package org.de013.shoppingcart.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for updating cart items
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Schema(description = "Request to update cart item")
public class UpdateCartItemDto {

    @Schema(description = "Cart item ID to update", example = "123")
    @NotNull(message = "{item.id.required}")
    @Positive(message = "{item.id.positive}")
    @JsonProperty("item_id")
    private Long itemId;

    @Schema(description = "New quantity for the item", example = "3", minimum = "1", maximum = "99")
    @Min(value = 1, message = "{quantity.min}")
    @Max(value = 99, message = "{quantity.max}")
    private Integer quantity;

    @Schema(description = "Updated unit price", example = "34.99")
    @DecimalMin(value = "0.01", message = "{price.positive}")
    @Digits(integer = 10, fraction = 2, message = "{price.format}")
    @JsonProperty("unit_price")
    private BigDecimal unitPrice;

    @Schema(description = "Updated special instructions", example = "Please handle with care")
    @Size(max = 500, message = "{instructions.size}")
    @JsonProperty("special_instructions")
    private String specialInstructions;

    @Schema(description = "Update gift status", example = "true")
    @JsonProperty("is_gift")
    private Boolean isGift;

    @Schema(description = "Updated gift message", example = "Congratulations!")
    @Size(max = 500, message = "{gift.message.size}")
    @JsonProperty("gift_message")
    private String giftMessage;

    @Schema(description = "Updated gift wrap type", example = "luxury", allowableValues = {"basic", "premium", "luxury"})
    @Size(max = 50, message = "{gift.wrap.size}")
    @JsonProperty("gift_wrap_type")
    private String giftWrapType;

    @Schema(description = "User ID (for authentication)", example = "user-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 36, message = "{user.id.size}")
    @JsonProperty("user_id")
    private String userId;

    @Schema(description = "Session ID (for guest users)", example = "sess-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 100, message = "{session.id.size}")
    @JsonProperty("session_id")
    private String sessionId;

    @Schema(description = "Validate price against current product price", example = "true")
    @JsonProperty("validate_price")
    @Builder.Default
    private Boolean validatePrice = true;

    @Schema(description = "Update operation type", example = "QUANTITY", allowableValues = {"QUANTITY", "PRICE", "GIFT_OPTIONS", "INSTRUCTIONS", "ALL"})
    @JsonProperty("update_type")
    @Builder.Default
    private String updateType = "ALL";

    /**
     * Validate that either userId or sessionId is provided
     */
    @AssertTrue(message = "{user.or.session.required}")
    public boolean isValidUserOrSession() {
        return (userId != null && !userId.trim().isEmpty()) || 
               (sessionId != null && !sessionId.trim().isEmpty());
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
        return quantity != null || unitPrice != null || specialInstructions != null ||
               isGift != null || giftMessage != null || giftWrapType != null;
    }

    /**
     * Check if this is for an authenticated user
     */
    @JsonIgnore
    public boolean isAuthenticatedUser() {
        return userId != null && !userId.trim().isEmpty();
    }

    /**
     * Check if this is for a guest session
     */
    public boolean isGuestSession() {
        return sessionId != null && !sessionId.trim().isEmpty() && 
               (userId == null || userId.trim().isEmpty());
    }

    /**
     * Get the identifier (userId or sessionId)
     */
    public String getIdentifier() {
        return isAuthenticatedUser() ? userId : sessionId;
    }

    /**
     * Check if quantity is being updated
     */
    public boolean isQuantityUpdate() {
        return quantity != null;
    }

    /**
     * Check if price is being updated
     */
    public boolean isPriceUpdate() {
        return unitPrice != null;
    }

    /**
     * Check if gift options are being updated
     */
    public boolean isGiftOptionsUpdate() {
        return isGift != null || giftMessage != null || giftWrapType != null;
    }
}
