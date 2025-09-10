package org.de013.paymentservice.dto.stripe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for Stripe payment responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripePaymentResponse {

    private String paymentIntentId;
    private String status;
    private String clientSecret;
    private BigDecimal amount;
    private String currency;
    private String customerId;
    private String paymentMethodId;
    private String description;
    private String receiptUrl;
    private Map<String, String> metadata;

    // Payment Intent details
    private String captureMethod;
    private String confirmationMethod;
    private Long created;
    private Boolean livemode;

    // Charges information
    private StripeChargeInfo latestCharge;

    // Next action (for 3D Secure, etc.)
    private StripeNextAction nextAction;

    // Error information
    private StripeErrorInfo error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeChargeInfo {
        private String chargeId;
        private String status;
        private BigDecimal amount;
        private String currency;
        private String receiptUrl;
        private String failureCode;
        private String failureMessage;
        private StripePaymentMethodDetails paymentMethodDetails;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripePaymentMethodDetails {
        private String type;
        private StripeCardDetails card;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeCardDetails {
        private String brand;
        private String country;
        private Integer expMonth;
        private Integer expYear;
        private String funding;
        private String last4;
        private String network;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeNextAction {
        private String type;
        private StripeRedirectToUrl redirectToUrl;
        private StripeUseStripeSdk useStripeSdk;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeRedirectToUrl {
        private String returnUrl;
        private String url;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeUseStripeSdk {
        private String type;
        private String stripeJs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StripeErrorInfo {
        private String code;
        private String message;
        private String type;
        private String param;
        private String declineCode;
    }

    // Helper methods
    @JsonIgnore
    public boolean isSuccessful() {
        return "succeeded".equals(status);
    }

    @JsonIgnore
    public boolean requiresAction() {
        return "requires_action".equals(status);
    }

    @JsonIgnore
    public boolean requiresConfirmation() {
        return "requires_confirmation".equals(status);
    }

    @JsonIgnore
    public boolean requiresPaymentMethod() {
        return "requires_payment_method".equals(status);
    }

    @JsonIgnore
    public boolean isFailed() {
        return "failed".equals(status) || error != null;
    }

    @JsonIgnore
    public boolean isPending() {
        return "processing".equals(status) || "requires_capture".equals(status);
    }

    @JsonIgnore
    public boolean hasNextAction() {
        return nextAction != null;
    }

    @JsonIgnore
    public boolean isRedirectRequired() {
        return hasNextAction() && "redirect_to_url".equals(nextAction.getType());
    }

    @JsonIgnore
    public boolean isStripeJsRequired() {
        return hasNextAction() && "use_stripe_sdk".equals(nextAction.getType());
    }

    @JsonIgnore
    public String getFailureMessage() {
        if (error != null && error.getMessage() != null) {
            return error.getMessage();
        }
        if (latestCharge != null && latestCharge.getFailureMessage() != null) {
            return latestCharge.getFailureMessage();
        }
        return null;
    }

    @JsonIgnore
    public String getFailureCode() {
        if (error != null && error.getCode() != null) {
            return error.getCode();
        }
        if (latestCharge != null && latestCharge.getFailureCode() != null) {
            return latestCharge.getFailureCode();
        }
        return null;
    }

    @JsonIgnore
    public BigDecimal getAmountInDollars() {
        if (amount == null) return BigDecimal.ZERO;
        return amount.divide(new BigDecimal("100"));
    }
}
