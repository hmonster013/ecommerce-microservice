package org.de013.paymentservice.dto.stripe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for Stripe customer responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeCustomerResponse {

    private String customerId;
    private String email;
    private String name;
    private String phone;
    private String description;
    private StripeAddress address;
    private StripeShipping shipping;
    private Map<String, String> metadata;
    private Long created;
    private Boolean livemode;

    // Customer balance and currency
    private Long balance;
    private String currency;

    // Default payment method
    private String defaultPaymentMethodId;

    // Invoice settings
    private StripeInvoiceSettings invoiceSettings;

    // Payment methods (if requested)
    private List<StripePaymentMethodSummary> paymentMethods;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeShipping {
        private String name;
        private String phone;
        private StripeAddress address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeInvoiceSettings {
        private String defaultPaymentMethod;
        private String footer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripePaymentMethodSummary {
        private String paymentMethodId;
        private String type;
        private StripeCardSummary card;
        private Long created;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeCardSummary {
        private String brand;
        private String country;
        private Integer expMonth;
        private Integer expYear;
        private String funding;
        private String last4;
    }

    // Helper methods
    public boolean hasAddress() {
        return address != null && address.getLine1() != null;
    }

    public boolean hasShipping() {
        return shipping != null && shipping.getAddress() != null;
    }

    public boolean hasDefaultPaymentMethod() {
        return defaultPaymentMethodId != null && !defaultPaymentMethodId.trim().isEmpty();
    }

    public boolean hasPaymentMethods() {
        return paymentMethods != null && !paymentMethods.isEmpty();
    }

    public boolean hasBalance() {
        return balance != null && balance != 0;
    }

    public boolean hasPositiveBalance() {
        return balance != null && balance > 0;
    }

    public boolean hasNegativeBalance() {
        return balance != null && balance < 0;
    }

    public int getPaymentMethodCount() {
        return paymentMethods != null ? paymentMethods.size() : 0;
    }

    public String getDisplayName() {
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        if (email != null && !email.trim().isEmpty()) {
            return email;
        }
        return customerId;
    }

    public String getFullAddress() {
        if (!hasAddress()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        if (address.getLine1() != null) {
            sb.append(address.getLine1());
        }
        if (address.getLine2() != null) {
            sb.append(", ").append(address.getLine2());
        }
        if (address.getCity() != null) {
            sb.append(", ").append(address.getCity());
        }
        if (address.getState() != null) {
            sb.append(", ").append(address.getState());
        }
        if (address.getPostalCode() != null) {
            sb.append(" ").append(address.getPostalCode());
        }
        if (address.getCountry() != null) {
            sb.append(", ").append(address.getCountry());
        }

        return sb.toString();
    }
}
