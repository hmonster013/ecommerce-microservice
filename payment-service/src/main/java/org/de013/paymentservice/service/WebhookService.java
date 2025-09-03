package org.de013.paymentservice.service;

import org.de013.paymentservice.dto.payment.StripeWebhookRequest;

/**
 * Service interface for webhook processing operations
 */
public interface WebhookService {

    // ========== WEBHOOK PROCESSING ==========

    /**
     * Process incoming webhook from Stripe
     */
    void processStripeWebhook(String payload, String signature);

    /**
     * Verify webhook signature
     */
    boolean verifyWebhookSignature(String payload, String signature, String secret);

    /**
     * Parse webhook payload
     */
    StripeWebhookRequest parseWebhookPayload(String payload);

    /**
     * Process webhook event
     */
    void processWebhookEvent(StripeWebhookRequest webhookRequest);

    // ========== PAYMENT INTENT EVENTS ==========

    /**
     * Handle payment intent succeeded event
     */
    void handlePaymentIntentSucceeded(StripeWebhookRequest webhookRequest);

    /**
     * Handle payment intent failed event
     */
    void handlePaymentIntentFailed(StripeWebhookRequest webhookRequest);

    /**
     * Handle payment intent requires action event
     */
    void handlePaymentIntentRequiresAction(StripeWebhookRequest webhookRequest);

    /**
     * Handle payment intent canceled event
     */
    void handlePaymentIntentCanceled(StripeWebhookRequest webhookRequest);

    // ========== PAYMENT METHOD EVENTS ==========

    /**
     * Handle payment method attached event
     */
    void handlePaymentMethodAttached(StripeWebhookRequest webhookRequest);

    /**
     * Handle payment method detached event
     */
    void handlePaymentMethodDetached(StripeWebhookRequest webhookRequest);

    // ========== CUSTOMER EVENTS ==========

    /**
     * Handle customer created event
     */
    void handleCustomerCreated(StripeWebhookRequest webhookRequest);

    /**
     * Handle customer updated event
     */
    void handleCustomerUpdated(StripeWebhookRequest webhookRequest);

    /**
     * Handle customer deleted event
     */
    void handleCustomerDeleted(StripeWebhookRequest webhookRequest);

    // ========== CHARGE EVENTS ==========

    /**
     * Handle charge succeeded event
     */
    void handleChargeSucceeded(StripeWebhookRequest webhookRequest);

    /**
     * Handle charge failed event
     */
    void handleChargeFailed(StripeWebhookRequest webhookRequest);

    /**
     * Handle charge dispute created event
     */
    void handleChargeDisputeCreated(StripeWebhookRequest webhookRequest);

    // ========== REFUND EVENTS ==========

    /**
     * Handle refund created event
     */
    void handleRefundCreated(StripeWebhookRequest webhookRequest);

    /**
     * Handle refund updated event
     */
    void handleRefundUpdated(StripeWebhookRequest webhookRequest);

    // ========== WEBHOOK VALIDATION ==========

    /**
     * Validate webhook event type
     */
    boolean isValidEventType(String eventType);

    /**
     * Check if webhook event should be processed
     */
    boolean shouldProcessEvent(StripeWebhookRequest webhookRequest);

    /**
     * Get webhook endpoint secret
     */
    String getWebhookSecret();

    // ========== ERROR HANDLING ==========

    /**
     * Handle webhook processing error
     */
    void handleWebhookError(String payload, String signature, Exception error);

    /**
     * Log webhook event for debugging
     */
    void logWebhookEvent(StripeWebhookRequest webhookRequest);
}
