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
     * Process webhook event
     */
    public void processWebhookEvent(StripeWebhookRequest webhookRequest) throws Exception {
        String eventType = webhookRequest.getEventType();
        log.info("Processing Stripe webhook event: {}", eventType);

        try {
            switch (eventType) {
                case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(webhookRequest);
                case "payment_intent.payment_failed" -> handlePaymentIntentFailed(webhookRequest);
                case "payment_intent.requires_action" -> handlePaymentIntentRequiresAction(webhookRequest);
                case "payment_intent.canceled" -> handlePaymentIntentCanceled(webhookRequest);
                case "payment_method.attached" -> handlePaymentMethodAttached(webhookRequest);
                case "payment_method.detached" -> handlePaymentMethodDetached(webhookRequest);
                case "customer.created" -> handleCustomerCreated(webhookRequest);
                case "customer.updated" -> handleCustomerUpdated(webhookRequest);
                case "customer.deleted" -> handleCustomerDeleted(webhookRequest);
                case "charge.succeeded" -> handleChargeSucceeded(webhookRequest);
                case "charge.failed" -> handleChargeFailed(webhookRequest);
                case "charge.dispute.created" -> handleChargeDisputeCreated(webhookRequest);
                default -> {
                    log.debug("Unhandled webhook event type: {}", eventType);
                }
            }
        } catch (Exception e) {
            log.error("Error processing webhook event: {}", eventType, e);
            throw new PaymentGatewayException("Failed to process webhook event: " + e.getMessage(), e);
        }
    }

    /**
     * Handle payment intent succeeded event
     */
    private void handlePaymentIntentSucceeded(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Payment intent succeeded: {}", paymentIntentId);
        
        // TODO: Update payment status in database
        // TODO: Send notification to user
        // TODO: Update order status
    }

    /**
     * Handle payment intent failed event
     */
    private void handlePaymentIntentFailed(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Payment intent failed: {}", paymentIntentId);
        
        // TODO: Update payment status in database
        // TODO: Send notification to user
        // TODO: Handle failed payment logic
    }

    /**
     * Handle payment intent requires action event
     */
    private void handlePaymentIntentRequiresAction(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Payment intent requires action: {}", paymentIntentId);
        
        // TODO: Update payment status in database
        // TODO: Send notification to user for required action
    }

    /**
     * Handle payment intent canceled event
     */
    private void handlePaymentIntentCanceled(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Payment intent canceled: {}", paymentIntentId);
        
        // TODO: Update payment status in database
        // TODO: Handle canceled payment logic
    }

    /**
     * Handle payment method attached event
     */
    private void handlePaymentMethodAttached(StripeWebhookRequest webhookRequest) {
        String paymentMethodId = webhookRequest.getPaymentMethodId();
        String customerId = webhookRequest.getCustomerId();
        log.info("Payment method {} attached to customer {}", paymentMethodId, customerId);
        
        // TODO: Update payment method in database
    }

    /**
     * Handle payment method detached event
     */
    private void handlePaymentMethodDetached(StripeWebhookRequest webhookRequest) {
        String paymentMethodId = webhookRequest.getPaymentMethodId();
        log.info("Payment method detached: {}", paymentMethodId);
        
        // TODO: Update payment method status in database
    }

    /**
     * Handle customer created event
     */
    private void handleCustomerCreated(StripeWebhookRequest webhookRequest) {
        String customerId = webhookRequest.getCustomerId();
        log.info("Customer created: {}", customerId);
        
        // TODO: Handle customer creation logic if needed
    }

    /**
     * Handle customer updated event
     */
    private void handleCustomerUpdated(StripeWebhookRequest webhookRequest) {
        String customerId = webhookRequest.getCustomerId();
        log.info("Customer updated: {}", customerId);
        
        // TODO: Handle customer update logic if needed
    }

    /**
     * Handle customer deleted event
     */
    private void handleCustomerDeleted(StripeWebhookRequest webhookRequest) {
        String customerId = webhookRequest.getCustomerId();
        log.info("Customer deleted: {}", customerId);
        
        // TODO: Handle customer deletion logic
    }

    /**
     * Handle charge succeeded event
     */
    private void handleChargeSucceeded(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Charge succeeded for payment intent: {}", paymentIntentId);
        
        // TODO: Create payment transaction record
        // TODO: Update payment status if needed
    }

    /**
     * Handle charge failed event
     */
    private void handleChargeFailed(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Charge failed for payment intent: {}", paymentIntentId);
        
        // TODO: Create failed transaction record
        // TODO: Handle charge failure logic
    }

    /**
     * Handle charge dispute created event
     */
    private void handleChargeDisputeCreated(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Charge dispute created for payment intent: {}", paymentIntentId);
        
        // TODO: Handle dispute creation logic
        // TODO: Send notification to admin
    }
}
