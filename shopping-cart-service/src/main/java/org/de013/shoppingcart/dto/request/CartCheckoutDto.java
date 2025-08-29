package org.de013.shoppingcart.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO for cart checkout preparation
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode
@Schema(description = "Request to prepare cart for checkout")
public class CartCheckoutDto {

    @Schema(description = "Cart ID to checkout", example = "123")
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

    @Schema(description = "Shipping address information")
    @Valid
    @JsonProperty("shipping_address")
    private AddressDto shippingAddress;

    @Schema(description = "Billing address information")
    @Valid
    @JsonProperty("billing_address")
    private AddressDto billingAddress;

    @Schema(description = "Selected shipping method", example = "STANDARD")
    @NotBlank(message = "Shipping method is required")
    @Size(max = 100, message = "Shipping method must not exceed 100 characters")
    @JsonProperty("shipping_method")
    private String shippingMethod;

    @Schema(description = "Expected shipping cost", example = "9.99")
    @DecimalMin(value = "0.00", message = "Shipping cost must be non-negative")
    @Digits(integer = 10, fraction = 2, message = "Shipping cost must have at most 10 integer digits and 2 decimal places")
    @JsonProperty("shipping_cost")
    private BigDecimal shippingCost;

    @Schema(description = "Tax rate to apply", example = "0.08")
    @DecimalMin(value = "0.00", message = "Tax rate must be non-negative")
    @DecimalMax(value = "1.00", message = "Tax rate must not exceed 100%")
    @Digits(integer = 1, fraction = 4, message = "Tax rate must have at most 1 integer digit and 4 decimal places")
    @JsonProperty("tax_rate")
    private BigDecimal taxRate;

    @Schema(description = "Customer email for order confirmation", example = "customer@example.com")
    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    @JsonProperty("customer_email")
    private String customerEmail;

    @Schema(description = "Customer phone number", example = "+1234567890")
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    @JsonProperty("customer_phone")
    private String customerPhone;

    @Schema(description = "Special delivery instructions", example = "Leave at front door")
    @Size(max = 1000, message = "Delivery instructions must not exceed 1000 characters")
    @JsonProperty("delivery_instructions")
    private String deliveryInstructions;

    @Schema(description = "Validate cart before checkout", example = "true")
    @JsonProperty("validate_cart")
    @Builder.Default
    private Boolean validateCart = true;

    @Schema(description = "Validate product availability", example = "true")
    @JsonProperty("validate_availability")
    @Builder.Default
    private Boolean validateAvailability = true;

    @Schema(description = "Validate pricing", example = "true")
    @JsonProperty("validate_pricing")
    @Builder.Default
    private Boolean validatePricing = true;

    @Schema(description = "Use same address for billing and shipping", example = "false")
    @JsonProperty("same_billing_address")
    @Builder.Default
    private Boolean sameBillingAddress = false;

    @Schema(description = "Loyalty points to use", example = "100")
    @Min(value = 0, message = "Loyalty points must be non-negative")
    @JsonProperty("loyalty_points_to_use")
    private Integer loyaltyPointsToUse;

    @Schema(description = "Gift card code to apply", example = "GC123456789")
    @Size(max = 50, message = "Gift card code must not exceed 50 characters")
    @JsonProperty("gift_card_code")
    private String giftCardCode;

    @Schema(description = "Marketing consent for promotional emails", example = "false")
    @JsonProperty("marketing_consent")
    @Builder.Default
    private Boolean marketingConsent = false;

    /**
     * Nested DTO for address information
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    @EqualsAndHashCode
    @Schema(description = "Address information")
    public static class AddressDto {

        @Schema(description = "First name", example = "John", required = true)
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        @JsonProperty("first_name")
        private String firstName;

        @Schema(description = "Last name", example = "Doe", required = true)
        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        @JsonProperty("last_name")
        private String lastName;

        @Schema(description = "Street address line 1", example = "123 Main St", required = true)
        @NotBlank(message = "Street address is required")
        @Size(max = 255, message = "Street address must not exceed 255 characters")
        @JsonProperty("street_address")
        private String streetAddress;

        @Schema(description = "Street address line 2", example = "Apt 4B")
        @Size(max = 255, message = "Street address line 2 must not exceed 255 characters")
        @JsonProperty("street_address_2")
        private String streetAddress2;

        @Schema(description = "City", example = "New York", required = true)
        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        private String city;

        @Schema(description = "State/Province", example = "NY", required = true)
        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must not exceed 100 characters")
        private String state;

        @Schema(description = "Postal/ZIP code", example = "10001", required = true)
        @NotBlank(message = "Postal code is required")
        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        @JsonProperty("postal_code")
        private String postalCode;

        @Schema(description = "Country code", example = "US", required = true)
        @NotBlank(message = "Country is required")
        @Size(min = 2, max = 3, message = "Country code must be 2-3 characters")
        private String country;

        @Schema(description = "Phone number", example = "+1234567890")
        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        private String phone;
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
    public AddressDto getEffectiveBillingAddress() {
        return Boolean.TRUE.equals(sameBillingAddress) ? shippingAddress : billingAddress;
    }
}
