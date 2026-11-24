package org.de013.paymentservice.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.google.gson.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.de013.paymentservice.exception.InvalidStripeWebhookException;
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
            if (webhookSecret == null || webhookSecret.isBlank()) throw new IllegalStateException("Stripe webhook secret is missing");
            try {
                Webhook.Signature.verifyHeader(payload, signature, webhookSecret, 300L);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                throw new InvalidStripeWebhookException(e);
            }
            Event event = Webhook.constructEvent(payload, signature, webhookSecret);
            return event != null;
        } catch (InvalidStripeWebhookException e) {
            throw e;
        } catch (SignatureVerificationException e) {
            throw new InvalidStripeWebhookException(e);
        } catch (JsonParseException e) {
            throw new InvalidStripeWebhookException(e);
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
            return objectMapper.readerFor(StripeWebhookRequest.class)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(payload);
        } catch (JsonProcessingException e) {
            throw new InvalidStripeWebhookException(e);
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
