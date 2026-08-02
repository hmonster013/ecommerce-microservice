package org.de013.shoppingcart.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * DTO for cart checkout preparation
 */
@Builder
@Schema(description = "Request to prepare cart for checkout")
public record CartCheckoutDto(

    @Schema(description = "Cart ID to checkout", example = "123")
    @JsonProperty("cart_id")
    Long cartId,

    @Schema(description = "User ID (for authenticated users)", example = "user-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 36, message = "User ID must not exceed 36 characters")
    @JsonProperty("user_id")
    String userId,

    @Schema(description = "Session ID (for guest users)", example = "sess-123e4567-e89b-12d3-a456-426614174000")
    @Size(max = 100, message = "Session ID must not exceed 100 characters")
    @JsonProperty("session_id")
    String sessionId,

    @Schema(description = "Shipping address information")
    @Valid
    @JsonProperty("shipping_address")
    AddressDto shippingAddress,

    @Schema(description = "Billing address information")
    @Valid
    @JsonProperty("billing_address")
    AddressDto billingAddress,

    @Schema(description = "Selected shipping method", example = "STANDARD")
    @NotBlank(message = "Shipping method is required")
    @Size(max = 100, message = "Shipping method must not exceed 100 characters")
    @JsonProperty("shipping_method")
    String shippingMethod,

    @Schema(description = "Expected shipping cost", example = "9.99")
    @DecimalMin(value = "0.00", message = "Shipping cost must be non-negative")
    @Digits(integer = 10, fraction = 2, message = "Shipping cost must have at most 10 integer digits and 2 decimal places")
    @JsonProperty("shipping_cost")
    BigDecimal shippingCost,

    @Schema(description = "Tax rate to apply", example = "0.08")
    @DecimalMin(value = "0.00", message = "Tax rate must be non-negative")
    @DecimalMax(value = "1.00", message = "Tax rate must not exceed 100%")
    @Digits(integer = 1, fraction = 4, message = "Tax rate must have at most 1 integer digit and 4 decimal places")
    @JsonProperty("tax_rate")
    BigDecimal taxRate,

    @Schema(description = "Customer email for order confirmation", example = "customer@example.com")
    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    @JsonProperty("customer_email")
    String customerEmail,

    @Schema(description = "Customer phone number", example = "+1234567890")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    @JsonProperty("customer_phone")
    String customerPhone,

    @Schema(description = "Special delivery instructions", example = "Leave at front door")
    @Size(max = 1000, message = "Delivery instructions must not exceed 1000 characters")
    @JsonProperty("delivery_instructions")
    String deliveryInstructions,

    @Schema(description = "Validate cart before checkout", example = "true")
    @JsonProperty("validate_cart")
    Boolean validateCart,

    @Schema(description = "Validate product availability", example = "true")
    @JsonProperty("validate_availability")
    Boolean validateAvailability,

    @Schema(description = "Validate pricing", example = "true")
    @JsonProperty("validate_pricing")
    Boolean validatePricing,

    @Schema(description = "Use same address for billing and shipping", example = "false")
    @JsonProperty("same_billing_address")
    Boolean sameBillingAddress,

    @Schema(description = "Loyalty points to use", example = "100")
    @Min(value = 0, message = "Loyalty points must be non-negative")
    @JsonProperty("loyalty_points_to_use")
    Integer loyaltyPointsToUse,

    @Schema(description = "Gift card code to apply", example = "GC123456789")
    @Size(max = 50, message = "Gift card code must not exceed 50 characters")
    @JsonProperty("gift_card_code")
    String giftCardCode,

    @Schema(description = "Marketing consent for promotional emails", example = "false")
    @JsonProperty("marketing_consent")
    Boolean marketingConsent
) {
    public CartCheckoutDto {
        if (validateCart == null) {
            validateCart = true;
        }
        if (validateAvailability == null) {
            validateAvailability = true;
        }
        if (validatePricing == null) {
            validatePricing = true;
        }
        if (sameBillingAddress == null) {
            sameBillingAddress = false;
        }
        if (marketingConsent == null) {
            marketingConsent = false;
        }
    }

    /**
     * Nested DTO for address information
     */
    @Builder
    @Schema(description = "Address information")
    public record AddressDto(

        @Schema(description = "First name", example = "John", required = true)
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        @JsonProperty("first_name")
        String firstName,

        @Schema(description = "Last name", example = "Doe", required = true)
        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        @JsonProperty("last_name")
        String lastName,

        @Schema(description = "Street address line 1", example = "123 Main St", required = true)
        @NotBlank(message = "Street address is required")
        @Size(max = 255, message = "Street address must not exceed 255 characters")
        @JsonProperty("street_address")
        String streetAddress,

        @Schema(description = "Street address line 2", example = "Apt 4B")
        @Size(max = 255, message = "Street address line 2 must not exceed 255 characters")
        @JsonProperty("street_address_2")
        String streetAddress2,

        @Schema(description = "City", example = "New York", required = true)
        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Schema(description = "State/Province", example = "NY", required = true)
        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must not exceed 100 characters")
        String state,

        @Schema(description = "Postal/ZIP code", example = "10001", required = true)
        @NotBlank(message = "Postal code is required")
        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        @JsonProperty("postal_code")
        String postalCode,

        @Schema(description = "Country code", example = "US", required = true)
        @NotBlank(message = "Country is required")
        @Size(min = 2, max = 3, message = "Country code must be 2-3 characters")
        String country,

        @Schema(description = "Phone number", example = "+1234567890")
        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        String phone
    ) {
    }

    /**
     * Validate that either userId or sessionId is provided
     */
    @JsonIgnore
    @AssertTrue(message = "Either user ID or session ID must be provided")
    public boolean isValidUserOrSession() {
        return (userId != null && !userId.trim().isEmpty()) ||
                (sessionId != null && !sessionId.trim().isEmpty());
    }

    /**
     * Validate that cart identification is provided
     */
    @JsonIgnore
    @AssertTrue(message = "Either cart ID or user/session identification must be provided")
    public boolean hasValidCartIdentification() {
        return cartId != null ||
                (userId != null && !userId.trim().isEmpty()) ||
                (sessionId != null && !sessionId.trim().isEmpty());
    }

    /**
     * Validate billing address when not using same address
     */
    @JsonIgnore
    @AssertTrue(message = "Billing address is required when not using same billing address")
    public boolean isValidBillingAddress() {
        if (Boolean.FALSE.equals(sameBillingAddress)) {
            return billingAddress != null;
        }
        return true;
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
     * Get effective billing address
     */
    @JsonIgnore
    public AddressDto getEffectiveBillingAddress() {
        return Boolean.TRUE.equals(sameBillingAddress) ? shippingAddress : billingAddress;
    }
}
