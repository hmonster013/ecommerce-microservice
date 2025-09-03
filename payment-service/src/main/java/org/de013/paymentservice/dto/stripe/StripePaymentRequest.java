package org.de013.paymentservice.dto.stripe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for Stripe payment requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripePaymentRequest {

    private BigDecimal amount; // In smallest currency unit (cents for USD)
    private String currency;
    private String paymentMethodId;
    private String customerId;
    private Boolean confirmPayment;
    private String description;
    private String receiptEmail;
    private String returnUrl;
    private Map<String, String> metadata;

    // Payment Intent options
    private String captureMethod; // "automatic" or "manual"
    private String confirmationMethod; // "automatic" or "manual"
    private Boolean setupFutureUsage; // For saving payment method

    // Customer creation data (if new customer)
    private StripeCustomerData customerData;

    // Payment method creation data (if new payment method)
    private StripePaymentMethodData paymentMethodData;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeCustomerData {
        private String email;
        private String name;
        private String phone;
        private StripeBillingAddress address;
        private Map<String, String> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripePaymentMethodData {
        private String type; // "card", "bank_account", etc.
        private StripeCardData card;
        private StripeBillingDetails billingDetails;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeCardData {
        private String number;
        private Integer expMonth;
        private Integer expYear;
        private String cvc;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeBillingDetails {
        private String name;
        private String email;
        private String phone;
        private StripeBillingAddress address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeBillingAddress {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }

    // Helper methods
    public long getAmountInCents() {
        if (amount == null) return 0;
        return amount.multiply(new BigDecimal("100")).longValue();
    }

    public boolean isNewCustomer() {
        return customerId == null && customerData != null;
    }

    public boolean isNewPaymentMethod() {
        return paymentMethodId == null && paymentMethodData != null;
    }

    public boolean shouldSavePaymentMethod() {
        return setupFutureUsage != null && setupFutureUsage;
    }

    public boolean isAutomaticCapture() {
        return "automatic".equals(captureMethod) || captureMethod == null;
    }

    public boolean isAutomaticConfirmation() {
        return "automatic".equals(confirmationMethod) || confirmationMethod == null;
    }
}
