package org.de013.paymentservice.dto.stripe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for Stripe customer requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeCustomerRequest {

    private String email;
    private String name;
    private String phone;
    private String description;
    private StripeAddress address;
    private StripeShipping shipping;
    private Map<String, String> metadata;

    // Payment method to attach
    private String paymentMethodId;

    // Invoice settings
    private StripeInvoiceSettings invoiceSettings;

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

    // Helper methods
    @JsonIgnore
    public boolean hasAddress() {
        return address != null && address.getLine1() != null;
    }

    @JsonIgnore
    public boolean hasShipping() {
        return shipping != null && shipping.getAddress() != null;
    }

    @JsonIgnore
    public boolean hasPaymentMethod() {
        return paymentMethodId != null && !paymentMethodId.trim().isEmpty();
    }

    @JsonIgnore
    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }
}
