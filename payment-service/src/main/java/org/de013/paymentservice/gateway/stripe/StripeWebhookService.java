package org.de013.paymentservice.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.dto.payment.StripeWebhookRequest;
import org.de013.paymentservice.exception.PaymentGatewayException;
import org.springframework.stereotype.Service;

/**
 * Service for handling Stripe webhooks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final PaymentGatewayConfig config;
    private final ObjectMapper objectMapper;

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String payload, String signature, String secret) throws Exception {
        try {
            String webhookSecret = secret != null ? secret : config.getGateways().getStripe().getWebhookSecret();
            Event event = Webhook.constructEvent(payload, signature, webhookSecret);
            return event != null;
        } catch (SignatureVerificationException e) {
            log.warn("Webhook signature verification failed", e);
            return false;
        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            throw new PaymentGatewayException("Failed to verify webhook signature: " + e.getMessage(), e);
        }
    }

    /**
     * Parse webhook payload
     */
    public StripeWebhookRequest parseWebhookPayload(String payload) throws Exception {
        try {
            return objectMapper.readValue(payload, StripeWebhookRequest.class);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            throw new PaymentGatewayException("Failed to parse webhook payload: " + e.getMessage(), e);
        }
    }



    /**
     * Get webhook endpoint secret
     */
    public String getWebhookSecret() {
        return config.getGateways().getStripe().getWebhookSecret();
    }

    /**
     * Validate webhook event type
     */
    public boolean isValidEventType(String eventType) {
        return eventType != null && (
                eventType.startsWith("payment_intent.") ||
                        eventType.startsWith("payment_method.") ||
                        eventType.startsWith("customer.") ||
                        eventType.startsWith("charge.") ||
                        eventType.contains("refund")
        );
    }
}
