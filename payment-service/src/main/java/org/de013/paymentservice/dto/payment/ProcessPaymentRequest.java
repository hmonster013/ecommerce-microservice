package org.de013.paymentservice.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.de013.paymentservice.entity.enums.Currency;
import org.de013.paymentservice.entity.enums.PaymentMethodType;

import java.math.BigDecimal;

/**
 * Request DTO for processing a payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPaymentRequest {

    @NotNull(message = "Order ID is required")
    @Positive(message = "Order ID must be positive")
    private Long orderId;

    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Long userId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 17, fraction = 2, message = "Amount must have at most 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private Currency currency;

    @NotNull(message = "Payment method type is required")
    private PaymentMethodType paymentMethodType;

    // Stripe-specific fields
    @Size(max = 100, message = "Stripe payment method ID must not exceed 100 characters")
    private String stripePaymentMethodId;

    @Size(max = 100, message = "Stripe customer ID must not exceed 100 characters")
    private String stripeCustomerId;

    // Payment details
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Email(message = "Receipt email must be valid")
    @Size(max = 100, message = "Receipt email must not exceed 100 characters")
    private String receiptEmail;

    // Payment options
    @Builder.Default
    private Boolean savePaymentMethod = false;

    @Builder.Default
    private Boolean setAsDefault = false;

    @Builder.Default
    private Boolean confirmPayment = true;

    // Return URLs for redirect-based payment methods
    @Size(max = 500, message = "Success URL must not exceed 500 characters")
    private String successUrl;

    @Size(max = 500, message = "Cancel URL must not exceed 500 characters")
    private String cancelUrl;

    // Additional metadata
    @Size(max = 100, message = "Payment method nickname must not exceed 100 characters")
    private String paymentMethodNickname;

    // Billing information (for new payment methods)
    private BillingAddress billingAddress;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingAddress {
        @Size(max = 100, message = "Customer name must not exceed 100 characters")
        private String customerName;

        @Size(max = 200, message = "Address line 1 must not exceed 200 characters")
        private String line1;

        @Size(max = 200, message = "Address line 2 must not exceed 200 characters")
        private String line2;

        @Size(max = 100, message = "City must not exceed 100 characters")
        private String city;

        @Size(max = 100, message = "State must not exceed 100 characters")
        private String state;

        @Size(max = 20, message = "Postal code must not exceed 20 characters")
        private String postalCode;

        @Size(min = 2, max = 2, message = "Country must be 2-character ISO code")
        private String country;
    }

    // Validation methods
    @JsonIgnore
    public boolean isUsingExistingPaymentMethod() {
        return stripePaymentMethodId != null && !stripePaymentMethodId.trim().isEmpty();
    }

    @JsonIgnore
    public boolean isNewCustomer() {
        return stripeCustomerId == null || stripeCustomerId.trim().isEmpty();
    }

    @JsonIgnore
    public boolean requiresBillingAddress() {
        return !isUsingExistingPaymentMethod() && paymentMethodType == PaymentMethodType.CARD;
    }
}
