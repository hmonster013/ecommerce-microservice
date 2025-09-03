package org.de013.paymentservice.dto.paymentmethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a payment method
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaymentMethodRequest {

    @Size(max = 50, message = "Nickname must not exceed 50 characters")
    private String nickname;

    private Boolean setAsDefault;

    private Boolean isActive;

    // Updated billing information
    @Valid
    private BillingAddress billingAddress;

    // Card expiry update (for display purposes)
    private CardExpiryUpdate cardExpiryUpdate;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardExpiryUpdate {
        private Integer expiryMonth;
        private Integer expiryYear;
    }

    // Helper methods
    public boolean hasNicknameUpdate() {
        return nickname != null;
    }

    public boolean hasDefaultUpdate() {
        return setAsDefault != null;
    }

    public boolean hasActiveUpdate() {
        return isActive != null;
    }

    public boolean hasBillingAddressUpdate() {
        return billingAddress != null;
    }

    public boolean hasCardExpiryUpdate() {
        return cardExpiryUpdate != null && 
               cardExpiryUpdate.getExpiryMonth() != null && 
               cardExpiryUpdate.getExpiryYear() != null;
    }

    public boolean hasAnyUpdate() {
        return hasNicknameUpdate() || 
               hasDefaultUpdate() || 
               hasActiveUpdate() || 
               hasBillingAddressUpdate() || 
               hasCardExpiryUpdate();
    }
}
