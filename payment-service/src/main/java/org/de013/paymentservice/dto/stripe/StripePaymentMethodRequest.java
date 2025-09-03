package org.de013.paymentservice.dto.stripe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for Stripe payment method requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripePaymentMethodRequest {

    private String type; // "card", "bank_account", etc.
    private String customerId; // To attach to customer
    private StripeCard card;
    private StripeBankAccount bankAccount;
    private StripeBillingDetails billingDetails;
    private Map<String, String> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeCard {
        private String number;
        private Integer expMonth;
        private Integer expYear;
        private String cvc;
        private String token; // If using Stripe Elements
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeBankAccount {
        private String country;
        private String currency;
        private String accountHolderType; // "individual" or "company"
        private String accountNumber;
        private String routingNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeBillingDetails {
        private String name;
        private String email;
        private String phone;
        private StripeAddress address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeAddress {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }

    // Helper methods
    public boolean isCardPaymentMethod() {
        return "card".equals(type);
    }

    public boolean isBankAccountPaymentMethod() {
        return "us_bank_account".equals(type) || "sepa_debit".equals(type);
    }

    public boolean hasCustomer() {
        return customerId != null && !customerId.trim().isEmpty();
    }

    public boolean hasBillingDetails() {
        return billingDetails != null;
    }

    public boolean hasCardDetails() {
        return card != null && (card.getNumber() != null || card.getToken() != null);
    }

    public boolean hasBankAccountDetails() {
        return bankAccount != null && bankAccount.getAccountNumber() != null;
    }

    public boolean isUsingToken() {
        return card != null && card.getToken() != null;
    }

    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }
}
