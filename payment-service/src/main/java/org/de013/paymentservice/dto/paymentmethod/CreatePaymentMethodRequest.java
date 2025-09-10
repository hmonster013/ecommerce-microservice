package org.de013.paymentservice.dto.paymentmethod;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.de013.paymentservice.entity.enums.PaymentMethodType;

/**
 * Request DTO for creating a payment method
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentMethodRequest {

    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Long userId;

    @NotNull(message = "Payment method type is required")
    private PaymentMethodType type;

    // Stripe-specific fields
    @Size(max = 100, message = "Stripe payment method ID must not exceed 100 characters")
    private String stripePaymentMethodId;

    @Size(max = 100, message = "Stripe customer ID must not exceed 100 characters")
    private String stripeCustomerId;

    // Payment method options
    @Builder.Default
    private Boolean setAsDefault = false;

    @Size(max = 50, message = "Nickname must not exceed 50 characters")
    private String nickname;

    // Billing information
    @Valid
    private BillingAddress billingAddress;

    // Digital wallet information
    @Size(max = 30, message = "Wallet type must not exceed 30 characters")
    private String walletType;

    @Size(max = 100, message = "Wallet ID must not exceed 100 characters")
    private String walletId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingAddress {
        @NotBlank(message = "Customer name is required")
        @Size(max = 100, message = "Customer name must not exceed 100 characters")
        private String customerName;

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 200, message = "Address line 1 must not exceed 200 characters")
        private String line1;

        @Size(max = 200, message = "Address line 2 must not exceed 200 characters")
        private String line2;

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        private String city;

        @Size(max = 100, message = "State must not exceed 100 characters")
        private String state;

        @NotBlank(message = "Postal code is required")
        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        private String postalCode;

        @NotBlank(message = "Country is required")
        @Size(min = 2, max = 2, message = "Country must be 2-character ISO code")
        private String country;
    }

    // Validation methods
    @JsonIgnore
    public boolean isCardPaymentMethod() {
        return type == PaymentMethodType.CARD;
    }

    @JsonIgnore
    public boolean isWalletPaymentMethod() {
        return type == PaymentMethodType.WALLET;
    }

    @JsonIgnore
    public boolean hasStripePaymentMethod() {
        return stripePaymentMethodId != null && !stripePaymentMethodId.trim().isEmpty();
    }

    @JsonIgnore
    public boolean requiresBillingAddress() {
        return isCardPaymentMethod() && billingAddress != null;
    }

    @JsonIgnore
    public boolean isNewCustomer() {
        return stripeCustomerId == null || stripeCustomerId.trim().isEmpty();
    }
}
