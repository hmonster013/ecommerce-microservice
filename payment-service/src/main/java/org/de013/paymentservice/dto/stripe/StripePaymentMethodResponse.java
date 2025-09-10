package org.de013.paymentservice.dto.stripe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for Stripe payment method responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripePaymentMethodResponse {

    private String paymentMethodId;
    private String type;
    private String customerId;
    private Long created;
    private Boolean livemode;
    private StripeCard card;
    private StripeBankAccount bankAccount;
    private StripeBillingDetails billingDetails;
    private Map<String, String> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeCard {
        private String brand;
        private String country;
        private Integer expMonth;
        private Integer expYear;
        private String funding;
        private String last4;
        private String network;
        private StripeChecks checks;
        private String wallet;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeChecks {
        private String addressLine1Check;
        private String addressPostalCodeCheck;
        private String cvcCheck;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeBankAccount {
        private String accountHolderType;
        private String bankName;
        private String country;
        private String currency;
        private String fingerprint;
        private String last4;
        private String routingNumber;
        private String status;
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
    @JsonIgnore
    public boolean isCardPaymentMethod() {
        return "card".equals(type);
    }

    @JsonIgnore
    public boolean isBankAccountPaymentMethod() {
        return "us_bank_account".equals(type) || "sepa_debit".equals(type);
    }

    @JsonIgnore
    public boolean hasCustomer() {
        return customerId != null && !customerId.trim().isEmpty();
    }

    @JsonIgnore
    public boolean hasBillingDetails() {
        return billingDetails != null;
    }

    @JsonIgnore
    public String getDisplayName() {
        if (isCardPaymentMethod() && card != null) {
            String brand = card.getBrand() != null ? card.getBrand().toUpperCase() : "CARD";
            String last4 = card.getLast4() != null ? card.getLast4() : "****";
            return brand + " •••• " + last4;
        }

        if (isBankAccountPaymentMethod() && bankAccount != null) {
            String bankName = bankAccount.getBankName() != null ? bankAccount.getBankName() : "BANK";
            String last4 = bankAccount.getLast4() != null ? bankAccount.getLast4() : "****";
            return bankName + " •••• " + last4;
        }

        return type != null ? type.toUpperCase() : "PAYMENT METHOD";
    }

    @JsonIgnore
    public String getMaskedNumber() {
        if (isCardPaymentMethod() && card != null && card.getLast4() != null) {
            return "**** **** **** " + card.getLast4();
        }

        if (isBankAccountPaymentMethod() && bankAccount != null && bankAccount.getLast4() != null) {
            return "****" + bankAccount.getLast4();
        }

        return "****";
    }

    @JsonIgnore
    public boolean isExpired() {
        if (!isCardPaymentMethod() || card == null) {
            return false;
        }

        if (card.getExpMonth() == null || card.getExpYear() == null) {
            return false;
        }

        java.time.LocalDate now = java.time.LocalDate.now();
        return now.getYear() > card.getExpYear() || 
               (now.getYear() == card.getExpYear() && now.getMonthValue() > card.getExpMonth());
    }

    @JsonIgnore
    public String getExpiryString() {
        if (isCardPaymentMethod() && card != null && 
            card.getExpMonth() != null && card.getExpYear() != null) {
            return String.format("%02d/%d", card.getExpMonth(), card.getExpYear());
        }
        return null;
    }

    @JsonIgnore
    public boolean hasValidChecks() {
        if (!isCardPaymentMethod() || card == null || card.getChecks() == null) {
            return true; // Assume valid if no checks available
        }

        StripeChecks checks = card.getChecks();
        return !"fail".equals(checks.getCvcCheck()) &&
               !"fail".equals(checks.getAddressLine1Check()) &&
               !"fail".equals(checks.getAddressPostalCodeCheck());
    }

    @JsonIgnore
    public String getFullBillingAddress() {
        if (!hasBillingDetails() || billingDetails.getAddress() == null) {
            return null;
        }

        StripeAddress addr = billingDetails.getAddress();
        StringBuilder sb = new StringBuilder();
        
        if (addr.getLine1() != null) sb.append(addr.getLine1());
        if (addr.getLine2() != null) sb.append(", ").append(addr.getLine2());
        if (addr.getCity() != null) sb.append(", ").append(addr.getCity());
        if (addr.getState() != null) sb.append(", ").append(addr.getState());
        if (addr.getPostalCode() != null) sb.append(" ").append(addr.getPostalCode());
        if (addr.getCountry() != null) sb.append(", ").append(addr.getCountry());

        return sb.toString();
    }
}
