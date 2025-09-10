package org.de013.paymentservice.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for Stripe webhook requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeWebhookRequest {

    private String id;
    private String object;
    private String type;
    private Boolean livemode;
    private Long created;
    
    @JsonProperty("api_version")
    private String apiVersion;
    
    private WebhookData data;
    private String request;
    
    @JsonProperty("pending_webhooks")
    private Integer pendingWebhooks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookData {
        private Map<String, Object> object;
        
        @JsonProperty("previous_attributes")
        private Map<String, Object> previousAttributes;
    }

    // Helper methods to extract common webhook data
    @JsonIgnore
    public String getEventType() {
        return type;
    }

    @JsonIgnore
    public String getEventId() {
        return id;
    }

    @JsonIgnore
    public String getObjectId() {
        if (data != null && data.getObject() != null) {
            return (String) data.getObject().get("id");
        }
        return null;
    }

    @JsonIgnore
    public String getObjectType() {
        if (data != null && data.getObject() != null) {
            return (String) data.getObject().get("object");
        }
        return null;
    }

    @JsonIgnore
    public String getPaymentIntentId() {
        if (data != null && data.getObject() != null) {
            Map<String, Object> obj = data.getObject();
            
            // Direct payment_intent object
            if ("payment_intent".equals(obj.get("object"))) {
                return (String) obj.get("id");
            }
            
            // Payment method attached to payment intent
            if ("payment_method".equals(obj.get("object"))) {
                return (String) obj.get("payment_intent");
            }
            
            // Charge object
            if ("charge".equals(obj.get("object"))) {
                return (String) obj.get("payment_intent");
            }
        }
        return null;
    }

    @JsonIgnore
    public String getCustomerId() {
        if (data != null && data.getObject() != null) {
            Map<String, Object> obj = data.getObject();
            return (String) obj.get("customer");
        }
        return null;
    }

    @JsonIgnore
    public String getPaymentMethodId() {
        if (data != null && data.getObject() != null) {
            Map<String, Object> obj = data.getObject();
            
            // Direct payment_method object
            if ("payment_method".equals(obj.get("object"))) {
                return (String) obj.get("id");
            }
            
            // Payment intent with payment method
            if ("payment_intent".equals(obj.get("object"))) {
                return (String) obj.get("payment_method");
            }
        }
        return null;
    }

    @JsonIgnore
    public String getStatus() {
        if (data != null && data.getObject() != null) {
            return (String) data.getObject().get("status");
        }
        return null;
    }

    @JsonIgnore
    public Long getAmount() {
        if (data != null && data.getObject() != null) {
            Object amount = data.getObject().get("amount");
            if (amount instanceof Number) {
                return ((Number) amount).longValue();
            }
        }
        return null;
    }

    @JsonIgnore
    public String getCurrency() {
        if (data != null && data.getObject() != null) {
            return (String) data.getObject().get("currency");
        }
        return null;
    }

    // Check webhook event types
    @JsonIgnore
    public boolean isPaymentIntentEvent() {
        return type != null && type.startsWith("payment_intent.");
    }

    @JsonIgnore
    public boolean isPaymentMethodEvent() {
        return type != null && type.startsWith("payment_method.");
    }

    @JsonIgnore
    public boolean isChargeEvent() {
        return type != null && type.startsWith("charge.");
    }

    @JsonIgnore
    public boolean isCustomerEvent() {
        return type != null && type.startsWith("customer.");
    }

    @JsonIgnore
    public boolean isRefundEvent() {
        return type != null && type.startsWith("charge.dispute") ||
               (type != null && type.contains("refund"));
    }

    // ========== ADDITIONAL GETTERS ==========

    /**
     * Get refund ID from webhook data
     */
    @JsonIgnore
    public String getRefundId() {
        if (data != null && data.getObject() != null) {
            Map<String, Object> obj = data.getObject();

            // Direct refund object
            if ("refund".equals(obj.get("object"))) {
                return (String) obj.get("id");
            }
        }
        return null;
    }

    /**
     * Get failure reason from webhook data
     */
    @JsonIgnore
    public String getFailureReason() {
        if (data != null && data.getObject() != null) {
            Map<String, Object> obj = data.getObject();
            Object failureReason = obj.get("failure_reason");
            return failureReason != null ? failureReason.toString() : null;
        }
        return null;
    }

    // Specific event type checks
    @JsonIgnore
    public boolean isPaymentSucceeded() {
        return "payment_intent.succeeded".equals(type);
    }

    @JsonIgnore
    public boolean isPaymentFailed() {
        return "payment_intent.payment_failed".equals(type);
    }

    @JsonIgnore
    public boolean isPaymentRequiresAction() {
        return "payment_intent.requires_action".equals(type);
    }

    @JsonIgnore
    public boolean isPaymentMethodAttached() {
        return "payment_method.attached".equals(type);
    }
}
