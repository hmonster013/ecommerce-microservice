package org.de013.shoppingcart.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * DTO for applying coupon to cart
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Schema(description = "Request to apply coupon to shopping cart")
public class ApplyCouponDto {

    @Schema(description = "Coupon code to apply", example = "SAVE20", required = true)
    @NotBlank(message = "Coupon code is required")
    @Size(min = 3, max = 50, message = "Coupon code must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Coupon code can only contain uppercase letters, numbers, underscores, and hyphens")
    @JsonProperty("coupon_code")
    private String couponCode;

    @Schema(description = "Cart ID to apply coupon to", example = "123")
    @JsonProperty("cart_id")
    private Long cartId;

    @Schema(description = "User ID (for authenticated users)", example = "user-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 36, message = "User ID must not exceed 36 characters")
    @JsonProperty("user_id")
    private String userId;

    @Schema(description = "Session ID (for guest users)", example = "sess-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 100, message = "Session ID must not exceed 100 characters")
    @JsonProperty("session_id")
    private String sessionId;

    @Schema(description = "Force apply coupon even if validation warnings exist", example = "false")
    @JsonProperty("force_apply")
    @Builder.Default
    private Boolean forceApply = false;

    @Schema(description = "Replace existing coupon if one is already applied", example = "false")
    @JsonProperty("replace_existing")
    @Builder.Default
    private Boolean replaceExisting = false;

    @Schema(description = "Validate coupon before applying", example = "true")
    @JsonProperty("validate_coupon")
    @Builder.Default
    private Boolean validateCoupon = true;

    @Schema(description = "Customer email for coupon validation", example = "customer@example.com")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    @JsonProperty("customer_email")
    private String customerEmail;

    @Schema(description = "Source of coupon application", example = "WEB", allowableValues = {"WEB", "MOBILE", "API", "ADMIN"})
    @JsonProperty("source")
    @Builder.Default
    private String source = "WEB";

    @Schema(description = "Campaign ID associated with the coupon", example = "campaign-123")
    @Size(max = 100, message = "Campaign ID must not exceed 100 characters")
    @JsonProperty("campaign_id")
    private String campaignId;

    @Schema(description = "Referrer URL where coupon was found", example = "https://example.com/promo")
    @Size(max = 1000, message = "Referrer URL must not exceed 1000 characters")
    @JsonProperty("referrer_url")
    private String referrerUrl;

    /**
     * Validate that either userId or sessionId is provided
     */
    @AssertTrue(message = "Either user ID or session ID must be provided")
    public boolean isValidUserOrSession() {
        return (userId != null && !userId.trim().isEmpty()) || 
               (sessionId != null && !sessionId.trim().isEmpty());
    }

    /**
     * Validate that cart identification is provided
     */
    @AssertTrue(message = "Either cart ID or user/session identification must be provided")
    public boolean hasValidCartIdentification() {
        return cartId != null || 
               (userId != null && !userId.trim().isEmpty()) || 
               (sessionId != null && !sessionId.trim().isEmpty());
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
     * Get normalized coupon code (uppercase, trimmed)
     */
    public String getNormalizedCouponCode() {
        return couponCode != null ? couponCode.trim().toUpperCase() : null;
    }

    /**
     * Check if coupon should be validated before applying
     */
    public boolean shouldValidateCoupon() {
        return Boolean.TRUE.equals(validateCoupon);
    }

    /**
     * Check if existing coupon should be replaced
     */
    public boolean shouldReplaceExisting() {
        return Boolean.TRUE.equals(replaceExisting);
    }

    /**
     * Check if coupon should be force applied
     */
    public boolean shouldForceApply() {
        return Boolean.TRUE.equals(forceApply);
    }
}
