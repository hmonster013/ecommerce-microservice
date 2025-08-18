package org.de013.shoppingcart.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for adding items to cart
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Schema(description = "Request to add item to shopping cart")
public class AddToCartDto {

    @Schema(description = "Product ID to add to cart", example = "prod-123e4567-e89b-12d3-a456-426614174000")
    @NotBlank(message = "Product ID is required")
    @Size(max = 36, message = "Product ID must not exceed 36 characters")
    @JsonProperty("product_id")
    private String productId;

    @Schema(description = "Quantity of the product to add", example = "2", minimum = "1", maximum = "99")
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 99, message = "Quantity must not exceed 99")
    private Integer quantity;

    @Schema(description = "Product variant ID (optional)", example = "var-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 36, message = "Variant ID must not exceed 36 characters")
    @JsonProperty("variant_id")
    private String variantId;

    @Schema(description = "Unit price of the product", example = "29.99")
    @DecimalMin(value = "0.01", message = "Unit price must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Unit price must have at most 10 integer digits and 2 decimal places")
    @JsonProperty("unit_price")
    private BigDecimal unitPrice;

    @Schema(description = "Special instructions for this item", example = "Please wrap as gift")
    @Size(max = 500, message = "Special instructions must not exceed 500 characters")
    @JsonProperty("special_instructions")
    private String specialInstructions;

    @Schema(description = "Whether this item is a gift", example = "false")
    @JsonProperty("is_gift")
    @Builder.Default
    private Boolean isGift = false;

    @Schema(description = "Gift message (required if is_gift is true)", example = "Happy Birthday!")
    @Size(max = 500, message = "Gift message must not exceed 500 characters")
    @JsonProperty("gift_message")
    private String giftMessage;

    @Schema(description = "Gift wrap type", example = "premium", allowableValues = {"basic", "premium", "luxury"})
    @Size(max = 50, message = "Gift wrap type must not exceed 50 characters")
    @JsonProperty("gift_wrap_type")
    private String giftWrapType;

    @Schema(description = "User ID (for authenticated users)", example = "user-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 36, message = "User ID must not exceed 36 characters")
    @JsonProperty("user_id")
    private String userId;

    @Schema(description = "Session ID (for guest users)", example = "sess-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 100, message = "Session ID must not exceed 100 characters")
    @JsonProperty("session_id")
    private String sessionId;

    @Schema(description = "Force create new cart if none exists", example = "true")
    @JsonProperty("force_create_cart")
    @Builder.Default
    private Boolean forceCreateCart = false;

    @Schema(description = "Replace existing item if same product+variant exists", example = "false")
    @JsonProperty("replace_existing")
    @Builder.Default
    private Boolean replaceExisting = false;

    /**
     * Validate that either userId or sessionId is provided
     */
    @AssertTrue(message = "Either user ID or session ID must be provided")
    public boolean isValidUserOrSession() {
        return (userId != null && !userId.trim().isEmpty()) || 
               (sessionId != null && !sessionId.trim().isEmpty());
    }

    /**
     * Validate gift message is provided when item is marked as gift
     */
    @AssertTrue(message = "Gift message is required when item is marked as gift")
    public boolean isValidGiftMessage() {
        if (Boolean.TRUE.equals(isGift)) {
            return giftMessage != null && !giftMessage.trim().isEmpty();
        }
        return true;
    }

    /**
     * Check if this is for an authenticated user
     */
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
}
