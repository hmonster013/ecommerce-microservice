package org.de013.paymentservice.dto.payment;

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
    public String getEventType() {
        return type;
    }

    public String getObjectId() {
        if (data != null && data.getObject() != null) {
            return (String) data.getObject().get("id");
        }
        return null;
    }

    public String getObjectType() {
        if (data != null && data.getObject() != null) {
            return (String) data.getObject().get("object");
        }
        return null;
    }

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

    public String getCustomerId() {
        if (data != null && data.getObject() != null) {
            Map<String, Object> obj = data.getObject();
            return (String) obj.get("customer");
        }
        return null;
    }

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

    public String getStatus() {
        if (data != null && data.getObject() != null) {
            return (String) data.getObject().get("status");
        }
        return null;
    }

    public Long getAmount() {
        if (data != null && data.getObject() != null) {
            Object amount = data.getObject().get("amount");
            if (amount instanceof Number) {
                return ((Number) amount).longValue();
            }
        }
        return null;
    }

    public String getCurrency() {
        if (data != null && data.getObject() != null) {
            return (String) data.getObject().get("currency");
        }
        return null;
    }

    // Check webhook event types
    public boolean isPaymentIntentEvent() {
        return type != null && type.startsWith("payment_intent.");
    }

    public boolean isPaymentMethodEvent() {
        return type != null && type.startsWith("payment_method.");
    }

    public boolean isChargeEvent() {
        return type != null && type.startsWith("charge.");
    }

    public boolean isCustomerEvent() {
        return type != null && type.startsWith("customer.");
    }

    public boolean isRefundEvent() {
        return type != null && type.startsWith("charge.dispute") || 
               (type != null && type.contains("refund"));
    }

    // Specific event type checks
    public boolean isPaymentSucceeded() {
        return "payment_intent.succeeded".equals(type);
    }

    public boolean isPaymentFailed() {
        return "payment_intent.payment_failed".equals(type);
    }

    public boolean isPaymentRequiresAction() {
        return "payment_intent.requires_action".equals(type);
    }

    public boolean isPaymentMethodAttached() {
        return "payment_method.attached".equals(type);
    }
}
