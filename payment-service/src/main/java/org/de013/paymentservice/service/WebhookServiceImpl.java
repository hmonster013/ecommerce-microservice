package org.de013.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.dto.payment.StripeWebhookRequest;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.PaymentMethod;
import org.de013.paymentservice.entity.Refund;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.entity.enums.RefundStatus;
import org.de013.paymentservice.exception.PaymentGatewayException;
import org.de013.paymentservice.gateway.PaymentGatewayFactory;
import org.de013.paymentservice.gateway.stripe.StripeWebhookService;
import org.de013.paymentservice.repository.PaymentMethodRepository;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementation of WebhookService for webhook processing operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WebhookServiceImpl implements WebhookService {

    private final PaymentGatewayFactory gatewayFactory;
    private final StripeWebhookService stripeWebhookService;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final RefundRepository refundRepository;

    // ========== WEBHOOK PROCESSING ==========

    @Override
    public void processStripeWebhook(String payload, String signature) {
        log.info("Processing Stripe webhook");

        try {
            // Verify signature
            if (!verifyWebhookSignature(payload, signature, getWebhookSecret())) {
                log.warn("Invalid webhook signature");
                throw new PaymentGatewayException("Invalid webhook signature");
            }

            // Parse payload
            StripeWebhookRequest webhookRequest = parseWebhookPayload(payload);
            
            // Log event for debugging
            logWebhookEvent(webhookRequest);

            // Validate event
            if (!shouldProcessEvent(webhookRequest)) {
                log.debug("Skipping webhook event: {}", webhookRequest.getEventType());
                return;
            }

            // Process event
            processWebhookEvent(webhookRequest);

            log.info("Webhook processed successfully: {}", webhookRequest.getEventType());

        } catch (Exception e) {
            log.error("Failed to process webhook", e);
            handleWebhookError(payload, signature, e);
            throw new PaymentGatewayException("Failed to process webhook: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature, String secret) {
        try {
            return stripeWebhookService.verifyWebhookSignature(payload, signature, secret);
        } catch (Exception e) {
            log.error("Failed to verify webhook signature", e);
            return false;
        }
    }

    @Override
    public StripeWebhookRequest parseWebhookPayload(String payload) {
        try {
            return stripeWebhookService.parseWebhookPayload(payload);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            throw new PaymentGatewayException("Failed to parse webhook payload: " + e.getMessage(), e);
        }
    }

    @Override
    public void processWebhookEvent(StripeWebhookRequest webhookRequest) {
        String eventType = webhookRequest.getEventType();
        log.debug("Processing webhook event: {}", eventType);

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
                case "refund.created" -> handleRefundCreated(webhookRequest);
                case "refund.updated" -> handleRefundUpdated(webhookRequest);
                default -> log.debug("Unhandled webhook event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing webhook event: {}", eventType, e);
            throw new PaymentGatewayException("Failed to process webhook event: " + e.getMessage(), e);
        }
    }

    // ========== PAYMENT INTENT EVENTS ==========

    @Override
    public void handlePaymentIntentSucceeded(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Handling payment intent succeeded: {}", paymentIntentId);

        Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentIntentId(paymentIntentId);
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            payment.setStatus(PaymentStatus.SUCCEEDED);
            paymentRepository.save(payment);

            // Update order status
            updateOrderStatus(payment.getOrderId(), "PAID");
            
            log.info("Payment status updated to SUCCEEDED: {}", payment.getPaymentNumber());
        } else {
            log.warn("Payment not found for payment intent: {}", paymentIntentId);
        }
    }

    @Override
    public void handlePaymentIntentFailed(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Handling payment intent failed: {}", paymentIntentId);

        Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentIntentId(paymentIntentId);
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(webhookRequest.getFailureReason() != null ?
                    webhookRequest.getFailureReason() : "Payment failed via webhook");
            paymentRepository.save(payment);

            // Update order status
            updateOrderStatus(payment.getOrderId(), "PAYMENT_FAILED");
            
            log.info("Payment status updated to FAILED: {}", payment.getPaymentNumber());
        } else {
            log.warn("Payment not found for payment intent: {}", paymentIntentId);
        }
    }

    @Override
    public void handlePaymentIntentRequiresAction(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Handling payment intent requires action: {}", paymentIntentId);

        Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentIntentId(paymentIntentId);
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            payment.setStatus(PaymentStatus.REQUIRES_ACTION);
            paymentRepository.save(payment);
            
            log.info("Payment status updated to REQUIRES_ACTION: {}", payment.getPaymentNumber());
        } else {
            log.warn("Payment not found for payment intent: {}", paymentIntentId);
        }
    }

    @Override
    public void handlePaymentIntentCanceled(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Handling payment intent canceled: {}", paymentIntentId);

        Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentIntentId(paymentIntentId);
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            payment.setStatus(PaymentStatus.CANCELED);
            paymentRepository.save(payment);

            // Update order status
            updateOrderStatus(payment.getOrderId(), "PAYMENT_CANCELED");
            
            log.info("Payment status updated to CANCELED: {}", payment.getPaymentNumber());
        } else {
            log.warn("Payment not found for payment intent: {}", paymentIntentId);
        }
    }

    // ========== PAYMENT METHOD EVENTS ==========

    @Override
    public void handlePaymentMethodAttached(StripeWebhookRequest webhookRequest) {
        String paymentMethodId = webhookRequest.getPaymentMethodId();
        String customerId = webhookRequest.getCustomerId();
        log.info("Handling payment method attached: {} to customer {}", paymentMethodId, customerId);

        Optional<PaymentMethod> paymentMethodOpt = paymentMethodRepository.findByStripePaymentMethodId(paymentMethodId);
        if (paymentMethodOpt.isPresent()) {
            PaymentMethod paymentMethod = paymentMethodOpt.get();
            paymentMethod.setStripeCustomerId(customerId);
            paymentMethodRepository.save(paymentMethod);
            
            log.info("Payment method updated with customer ID: {}", paymentMethod.getId());
        } else {
            log.debug("Payment method not found in local database: {}", paymentMethodId);
        }
    }

    @Override
    public void handlePaymentMethodDetached(StripeWebhookRequest webhookRequest) {
        String paymentMethodId = webhookRequest.getPaymentMethodId();
        log.info("Handling payment method detached: {}", paymentMethodId);

        Optional<PaymentMethod> paymentMethodOpt = paymentMethodRepository.findByStripePaymentMethodId(paymentMethodId);
        if (paymentMethodOpt.isPresent()) {
            PaymentMethod paymentMethod = paymentMethodOpt.get();
            paymentMethod.setStripeCustomerId(null);
            paymentMethod.setIsActive(false);
            paymentMethodRepository.save(paymentMethod);
            
            log.info("Payment method detached and deactivated: {}", paymentMethod.getId());
        } else {
            log.debug("Payment method not found in local database: {}", paymentMethodId);
        }
    }

    // ========== CUSTOMER EVENTS ==========

    @Override
    public void handleCustomerCreated(StripeWebhookRequest webhookRequest) {
        String customerId = webhookRequest.getCustomerId();
        log.info("Handling customer created: {}", customerId);
        // TODO: Handle customer creation if needed
    }

    @Override
    public void handleCustomerUpdated(StripeWebhookRequest webhookRequest) {
        String customerId = webhookRequest.getCustomerId();
        log.info("Handling customer updated: {}", customerId);
        // TODO: Handle customer update if needed
    }

    @Override
    public void handleCustomerDeleted(StripeWebhookRequest webhookRequest) {
        String customerId = webhookRequest.getCustomerId();
        log.info("Handling customer deleted: {}", customerId);
        // TODO: Handle customer deletion if needed
    }

    // ========== CHARGE EVENTS ==========

    @Override
    public void handleChargeSucceeded(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Handling charge succeeded for payment intent: {}", paymentIntentId);
        // Payment intent succeeded event will handle the main logic
    }

    @Override
    public void handleChargeFailed(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Handling charge failed for payment intent: {}", paymentIntentId);
        // Payment intent failed event will handle the main logic
    }

    @Override
    public void handleChargeDisputeCreated(StripeWebhookRequest webhookRequest) {
        String paymentIntentId = webhookRequest.getPaymentIntentId();
        log.info("Handling charge dispute created for payment intent: {}", paymentIntentId);
        // TODO: Handle dispute creation logic
    }

    // ========== REFUND EVENTS ==========

    @Override
    public void handleRefundCreated(StripeWebhookRequest webhookRequest) {
        String refundId = webhookRequest.getRefundId();
        log.info("Handling refund created: {}", refundId);

        Optional<Refund> refundOpt = refundRepository.findByStripeRefundId(refundId);
        if (refundOpt.isPresent()) {
            Refund refund = refundOpt.get();
            refund.setStatus(RefundStatus.SUCCEEDED);
            refundRepository.save(refund);

            log.info("Refund status updated to SUCCEEDED: {}", refund.getRefundNumber());
        } else {
            log.warn("Refund not found for Stripe refund: {}", refundId);
        }
    }

    @Override
    public void handleRefundUpdated(StripeWebhookRequest webhookRequest) {
        String refundId = webhookRequest.getRefundId();
        log.info("Handling refund updated: {}", refundId);

        Optional<Refund> refundOpt = refundRepository.findByStripeRefundId(refundId);
        if (refundOpt.isPresent()) {
            Refund refund = refundOpt.get();
            // Update refund status based on webhook data
            // TODO: Map Stripe refund status to internal status
            refundRepository.save(refund);
            
            log.info("Refund updated: {}", refund.getRefundNumber());
        } else {
            log.warn("Refund not found for Stripe refund: {}", refundId);
        }
    }

    // ========== WEBHOOK VALIDATION ==========

    @Override
    public boolean isValidEventType(String eventType) {
        return stripeWebhookService.isValidEventType(eventType);
    }

    @Override
    public boolean shouldProcessEvent(StripeWebhookRequest webhookRequest) {
        if (webhookRequest == null || webhookRequest.getEventType() == null) {
            return false;
        }

        return isValidEventType(webhookRequest.getEventType());
    }

    @Override
    public String getWebhookSecret() {
        return stripeWebhookService.getWebhookSecret();
    }

    // ========== ERROR HANDLING ==========

    @Override
    public void handleWebhookError(String payload, String signature, Exception error) {
        log.error("Webhook processing error - Payload length: {}, Signature: {}, Error: {}", 
                payload != null ? payload.length() : 0, signature, error.getMessage());
        
        // TODO: Implement error handling logic
        // - Store failed webhook for retry
        // - Send alert to monitoring system
        // - Log to error tracking system
    }

    @Override
    public void logWebhookEvent(StripeWebhookRequest webhookRequest) {
        log.debug("Webhook event - Type: {}, ID: {}",
                webhookRequest.getEventType(),
                webhookRequest.getEventId());
    }

    // ========== HELPER METHODS ==========

    private void updateOrderStatus(Long orderId, String status) {
        try {
            // TODO: Call order service to update status
            log.info("Updating order {} status to: {}", orderId, status);
        } catch (Exception e) {
            log.warn("Failed to update order status for order: {}", orderId, e);
        }
    }
}
