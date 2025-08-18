package org.de013.shoppingcart.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

/**
 * DTO for removing items from cart
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Schema(description = "Request to remove items from shopping cart")
public class RemoveFromCartDto {

    @Schema(description = "Cart item ID to remove", example = "123")
    @JsonProperty("item_id")
    private Long itemId;

    @Schema(description = "Product ID to remove (alternative to item_id)", example = "prod-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 36, message = "Product ID must not exceed 36 characters")
    @JsonProperty("product_id")
    private String productId;

    @Schema(description = "Product variant ID (used with product_id)", example = "var-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 36, message = "Variant ID must not exceed 36 characters")
    @JsonProperty("variant_id")
    private String variantId;

    @Schema(description = "List of item IDs to remove (for bulk removal)", example = "[123, 456, 789]")
    @JsonProperty("item_ids")
    private List<Long> itemIds;

    @Schema(description = "User ID (for authenticated users)", example = "user-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 36, message = "User ID must not exceed 36 characters")
    @JsonProperty("user_id")
    private String userId;

    @Schema(description = "Session ID (for guest users)", example = "sess-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 100, message = "Session ID must not exceed 100 characters")
    @JsonProperty("session_id")
    private String sessionId;

    @Schema(description = "Removal type", example = "SINGLE", allowableValues = {"SINGLE", "BULK", "ALL", "BY_PRODUCT"})
    @JsonProperty("removal_type")
    @Builder.Default
    private String removalType = "SINGLE";

    @Schema(description = "Reason for removal", example = "CUSTOMER_REQUEST", allowableValues = {
        "CUSTOMER_REQUEST", "OUT_OF_STOCK", "PRICE_CHANGE", "EXPIRED", "SYSTEM_CLEANUP"
    })
    @JsonProperty("removal_reason")
    private String removalReason;

    @Schema(description = "Confirm removal of all items", example = "false")
    @JsonProperty("confirm_remove_all")
    @Builder.Default
    private Boolean confirmRemoveAll = false;

    @Schema(description = "Save removed items to wishlist", example = "false")
    @JsonProperty("save_to_wishlist")
    @Builder.Default
    private Boolean saveToWishlist = false;

    /**
     * Validate that either userId or sessionId is provided
     */
    @AssertTrue(message = "Either user ID or session ID must be provided")
    public boolean isValidUserOrSession() {
        return (userId != null && !userId.trim().isEmpty()) || 
               (sessionId != null && !sessionId.trim().isEmpty());
    }

    /**
     * Validate removal criteria
     */
    @AssertTrue(message = "Either item ID, product ID, or item IDs list must be provided")
    public boolean hasValidRemovalCriteria() {
        return itemId != null || 
               (productId != null && !productId.trim().isEmpty()) ||
               (itemIds != null && !itemIds.isEmpty()) ||
               "ALL".equals(removalType);
    }

    /**
     * Validate bulk removal
     */
    @AssertTrue(message = "Item IDs list is required for bulk removal")
    public boolean isValidBulkRemoval() {
        if ("BULK".equals(removalType)) {
            return itemIds != null && !itemIds.isEmpty();
        }
        return true;
    }

    /**
     * Validate remove all confirmation
     */
    @AssertTrue(message = "Confirmation is required to remove all items")
    public boolean isValidRemoveAllConfirmation() {
        if ("ALL".equals(removalType)) {
            return Boolean.TRUE.equals(confirmRemoveAll);
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

    /**
     * Check if this is a single item removal
     */
    public boolean isSingleRemoval() {
        return "SINGLE".equals(removalType) && itemId != null;
    }

    /**
     * Check if this is a bulk removal
     */
    public boolean isBulkRemoval() {
        return "BULK".equals(removalType) && itemIds != null && !itemIds.isEmpty();
    }

    /**
     * Check if this is remove all items
     */
    public boolean isRemoveAll() {
        return "ALL".equals(removalType);
    }

    /**
     * Check if this is removal by product
     */
    public boolean isByProduct() {
        return "BY_PRODUCT".equals(removalType) && productId != null;
    }
}
