package org.de013.shoppingcart.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @NotBlank(message = "{coupon.code.required}")
    @Size(min = 3, max = 50, message = "{coupon.code.size}")
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "{coupon.code.format}")
    @JsonProperty("coupon_code")
    private String couponCode;

    @Schema(description = "Cart ID to apply coupon to", example = "123")
    @JsonProperty("cart_id")
    private Long cartId;

    @Schema(description = "User ID (for authenticated users)", example = "user-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 36, message = "{user.id.size}")
    @JsonProperty("user_id")
    private String userId;

    @Schema(description = "Session ID (for guest users)", example = "sess-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 100, message = "{session.id.size}")
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
    @Email(message = "{email.invalid}")
    @Size(max = 255, message = "{email.size}")
    @JsonProperty("customer_email")
    private String customerEmail;

    @Schema(description = "Source of coupon application", example = "WEB", allowableValues = {"WEB", "MOBILE", "API", "ADMIN"})
    @JsonProperty("source")
    @Builder.Default
    private String source = "WEB";

    @Schema(description = "Campaign ID associated with the coupon", example = "campaign-123")
    @Size(max = 100, message = "{campaign.id.size}")
    @JsonProperty("campaign_id")
    private String campaignId;

    @Schema(description = "Referrer URL where coupon was found", example = "https://example.com/promo")
    @Size(max = 1000, message = "{referrer.url.size}")
    @JsonProperty("referrer_url")
    private String referrerUrl;

    /**
     * Validate that either userId or sessionId is provided
     */
    @JsonIgnore
    @AssertTrue(message = "{user.or.session.required}")
    public boolean isValidUserOrSession() {
        return (userId != null && !userId.trim().isEmpty()) ||
               (sessionId != null && !sessionId.trim().isEmpty());
    }

    /**
     * Validate that cart identification is provided
     */
    @JsonIgnore
    @AssertTrue(message = "{cart.identification.required}")
    public boolean hasValidCartIdentification() {
        return cartId != null ||
               (userId != null && !userId.trim().isEmpty()) ||
               (sessionId != null && !sessionId.trim().isEmpty());
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
    @JsonIgnore
    public boolean isGuestSession() {
        return sessionId != null && !sessionId.trim().isEmpty() &&
               (userId == null || userId.trim().isEmpty());
    }

    /**
     * Get the identifier (userId or sessionId)
     */
    @JsonIgnore
    public String getIdentifier() {
        return isAuthenticatedUser() ? userId : sessionId;
    }

    /**
     * Get normalized coupon code (uppercase, trimmed)
     */
    @JsonIgnore
    public String getNormalizedCouponCode() {
        return couponCode != null ? couponCode.trim().toUpperCase() : null;
    }

    /**
     * Check if coupon should be validated before applying
     */
    @JsonIgnore
    public boolean shouldValidateCoupon() {
        return Boolean.TRUE.equals(validateCoupon);
    }

    /**
     * Check if existing coupon should be replaced
     */
    @JsonIgnore
    public boolean shouldReplaceExisting() {
        return Boolean.TRUE.equals(replaceExisting);
    }

    /**
     * Check if coupon should be force applied
     */
    @JsonIgnore
    public boolean shouldForceApply() {
        return Boolean.TRUE.equals(forceApply);
    }
}
