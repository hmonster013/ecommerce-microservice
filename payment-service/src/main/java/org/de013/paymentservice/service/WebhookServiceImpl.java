package org.de013.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.ApiResult;
import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.client.NotificationServiceClient;
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
import org.de013.paymentservice.entity.ProcessedStripeEvent;
import org.de013.paymentservice.repository.ProcessedStripeEventRepository;
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
    private final PaymentOutbox outbox;

    private final PaymentGatewayFactory gatewayFactory;
    private final StripeWebhookService stripeWebhookService;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final RefundRepository refundRepository;
    private final OrderServiceClient orderServiceClient;
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final PaymentOrderGuard guard;

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

            // Check if this event was already processed (Idempotency)
            String eventId = webhookRequest.getEventId();
            if (eventId != null && processedStripeEventRepository.existsById(eventId)) {
                log.info("Stripe event {} was already processed, skipping (Idempotency).", eventId);
                return;
            }

            // Validate event
            if (!shouldProcessEvent(webhookRequest)) {
                log.debug("Skipping webhook event: {}", webhookRequest.getEventType());
                return;
            }

            // Process event
            if (webhookRequest.getEventType().startsWith("payment_intent.")) {
                if (!processVerifiedIntent(webhookRequest)) return;
            } else {
                processWebhookEvent(webhookRequest);
            }

            // Mark event as processed in the database
            if (eventId != null) {
                processedStripeEventRepository.save(
                        ProcessedStripeEvent.builder()
                                .eventId(eventId)
                                .processedAt(java.time.LocalDateTime.now())
                                .build()
                );
            }

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
                case "payment_intent.succeeded", "payment_intent.payment_failed", "payment_intent.requires_action", "payment_intent.canceled" ->
                        throw new SecurityException("Intent events require a verified webhook");
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

    private boolean isHostedCheckout(Payment payment) {
        // A failed card attempt is not a failed Checkout Session. The signed hosted webhook/read-back owns this flow.
        return "STRIPE".equals(payment.getGatewayName()) && payment.getGatewayTxnRef() != null
                && payment.getGatewayTxnRef().startsWith("cs_");
    }

    // This method is reachable only after verification of the original payload.
    private boolean processVerifiedIntent(StripeWebhookRequest event) {
        String id = event.getObjectId();
        if (!"payment_intent".equals(event.getObjectType()) || id == null || id.isBlank()
                || event.getEventId() == null || event.getEventId().isBlank()) {
            throw new PaymentGatewayException("Invalid payment intent event");
        }
        if ("payment_intent.canceled".equals(event.getEventType()) && !"canceled".equals(event.getStatus())) {
            throw new PaymentGatewayException("Invalid canceled payment intent status");
        }
        if ("payment_intent.succeeded".equals(event.getEventType()) && !"succeeded".equals(event.getStatus())) {
            throw new PaymentGatewayException("Invalid succeeded payment intent status");
        }
        Long orderId = paymentRepository.findOrderIdByStripeIntent(id)
                .orElseThrow(() -> new PaymentGatewayException("Payment intent is not correlated"));
        boolean held = guard.lock(orderId);
        if (processedStripeEventRepository.existsById(event.getEventId())) return false;
        Payment payment = paymentRepository.findByStripePaymentIntentId(id)
                .filter(p -> orderId.equals(p.getOrderId()) && id.equals(p.getStripePaymentIntentId()))
                .orElseThrow(() -> new PaymentGatewayException("Payment intent correlation changed"));
        if (isHostedCheckout(payment)) return true;
        if (!"STRIPE".equals(payment.getGatewayName())) {
            throw new PaymentGatewayException("Payment intent provider is not correlated");
        }
        switch (event.getEventType()) {
            case "payment_intent.canceled" -> {
                if (payment.getStatus() != PaymentStatus.SUCCEEDED && !payment.isVerifiedRelease()) {
                    payment.setStatus(PaymentStatus.CANCELED);
                    payment.setBlocking(false);
                    payment.setVerifiedRelease(true);
                }
            }
            case "payment_intent.succeeded" -> {
                boolean alreadySucceeded = payment.getStatus() == PaymentStatus.SUCCEEDED;
                payment.setStatus(PaymentStatus.SUCCEEDED);
                if (payment.isVerifiedRelease()) {
                    guard.hold(orderId);
                } else if (!held && !alreadySucceeded) {
                    if (outbox.enabled()) outbox.enqueue(payment);
                    else updateOrderStatus(orderId, "PAID", payment);
                    sendPaymentSuccessNotification(payment);
                }
            }
            case "payment_intent.payment_failed" -> {
                if (!payment.isVerifiedRelease() && payment.getStatus() != PaymentStatus.SUCCEEDED) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setFailureReason(event.getFailureReason() != null ? event.getFailureReason() : "Payment failed via webhook");
                    if (!held) updateOrderStatus(orderId, "PAYMENT_FAILED", payment);
                }
            }
            case "payment_intent.requires_action" -> {
                if (!payment.isVerifiedRelease() && payment.getStatus() != PaymentStatus.SUCCEEDED) {
                    payment.setStatus(PaymentStatus.REQUIRES_ACTION);
                }
            }
            default -> log.debug("Unhandled webhook event type: {}", event.getEventType());
        }
        return true;
    }

    @Override
    public void handlePaymentIntentSucceeded(StripeWebhookRequest event) { throw new SecurityException("Verified webhook required"); }
    @Override
    public void handlePaymentIntentFailed(StripeWebhookRequest event) { throw new SecurityException("Verified webhook required"); }
    @Override
    public void handlePaymentIntentRequiresAction(StripeWebhookRequest event) { throw new SecurityException("Verified webhook required"); }
    @Override
    public void handlePaymentIntentCanceled(StripeWebhookRequest event) { throw new SecurityException("Verified webhook required"); }

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
        log.info("Successfully handled customer creation for customer: {}", customerId);
    }

    @Override
    public void handleCustomerUpdated(StripeWebhookRequest webhookRequest) {
        String customerId = webhookRequest.getCustomerId();
        log.info("Successfully handled customer update for customer: {}", customerId);
    }

    @Override
    public void handleCustomerDeleted(StripeWebhookRequest webhookRequest) {
        String customerId = webhookRequest.getCustomerId();
        log.info("Successfully handled customer deletion for customer: {}", customerId);
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
        log.warn("CRITICAL: Stripe charge dispute created for payment intent: {}. Please audit customer payment details.", paymentIntentId);
    }

    // ========== REFUND EVENTS ==========

    @Override
    public void handleRefundCreated(StripeWebhookRequest webhookRequest) {
        String refundId = webhookRequest.getRefundId();
        log.info("Handling refund created: {}", refundId);

        lockRefundWebhook(refundId);
        Optional<Refund> refundOpt = refundRepository.findByStripeRefundId(refundId);
        if (refundOpt.isPresent()) {
            Refund refund = refundOpt.get();
            refund.setStatus(RefundStatus.SUCCEEDED);
            refundRepository.save(refund);

            log.info("Refund status updated to SUCCEEDED: {}", refund.getRefundNumber());
        } else {
            throw new PaymentGatewayException("Refund is not correlated");
        }
    }

    @Override
    public void handleRefundUpdated(StripeWebhookRequest webhookRequest) {
        String refundId = webhookRequest.getRefundId();
        log.info("Handling refund updated: {}", refundId);

        lockRefundWebhook(refundId);
        Optional<Refund> refundOpt = refundRepository.findByStripeRefundId(refundId);
        if (refundOpt.isPresent()) {
            Refund refund = refundOpt.get();
            // Update refund status based on webhook data
            if (refund.getStatus() == RefundStatus.SUCCEEDED) {
                return;
            } else if (webhookRequest.getFailureReason() != null) {
                refund.setStatus(RefundStatus.FAILED);
            } else {
                refund.setStatus(RefundStatus.SUCCEEDED);
            }
            refundRepository.save(refund);

            log.info("Refund updated: {}", refund.getRefundNumber());
        } else {
            throw new PaymentGatewayException("Refund is not correlated");
        }
    }

    private void lockRefundWebhook(String refundId) {
        if (refundId == null || refundId.isBlank()) throw new PaymentGatewayException("Invalid refund event");
        Long paymentId = refundRepository.findPaymentIdByStripeRefundId(refundId)
                .orElseThrow(() -> new PaymentGatewayException("Refund is not correlated"));
        Long orderId = paymentRepository.findOrderIdByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentGatewayException("Refund payment is not correlated"));
        guard.lock(orderId);
        paymentRepository.findById(paymentId).filter(p -> orderId.equals(p.getOrderId()))
                .orElseThrow(() -> new PaymentGatewayException("Refund payment correlation changed"));
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
        String eventType = "UNKNOWN";
        String eventId = "UNKNOWN";

        try {
            if (payload != null) {
                StripeWebhookRequest webhookRequest = stripeWebhookService.parseWebhookPayload(payload);
                if (webhookRequest != null) {
                    eventType = webhookRequest.getEventType() != null ? webhookRequest.getEventType() : "UNKNOWN";
                    eventId = webhookRequest.getEventId() != null ? webhookRequest.getEventId() : "UNKNOWN";
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse payload during webhook error handling: {}", e.getMessage());
        }

        log.error("WEBHOOK_ALERT - Webhook processing failed! " +
                        "Payload length: {}, Signature present: {}, EventType: {}, EventId: {}, " +
                        "Error Class: {}, Error Message: {}",
                payload != null ? payload.length() : 0,
                signature != null && !signature.isBlank(),
                eventType,
                eventId,
                error.getClass().getName(),
                error.getMessage());

        // NOTE (deferred): A durable retry store (e.g. database table or dead-letter queue)
        // is recommended as a future enhancement to ensure guaranteed processing of failed webhooks.
    }

    @Override
    public void logWebhookEvent(StripeWebhookRequest webhookRequest) {
        log.debug("Webhook event - Type: {}, ID: {}",
                webhookRequest.getEventType(),
                webhookRequest.getEventId());
    }

    // ========== HELPER METHODS ==========

    private void updateOrderStatus(Long orderId, String status, Payment payment) {
        try {
            log.info("Updating order {} status to: {}", orderId, status);
            if ("PAID".equals(status)) {
                orderServiceClient.markOrderAsPaid(orderId, payment.getId(), payment.getPaymentNumber());
            } else if ("PAYMENT_FAILED".equals(status)) {
                orderServiceClient.markOrderPaymentFailed(orderId, payment.getFailureReason() != null ? payment.getFailureReason() : "Payment failed");
            } else {
                org.de013.paymentservice.dto.external.OrderStatusUpdateRequest request = new org.de013.paymentservice.dto.external.OrderStatusUpdateRequest(
                        status,
                        "Payment status updated to " + status
                );
                orderServiceClient.updateOrderStatus(orderId, request);
            }
            log.info("Successfully updated order {} status to: {}", orderId, status);
        } catch (Exception e) {
            log.warn("Failed to update order status for order: {}", orderId, e);
        }
    }

    private void sendPaymentSuccessNotification(Payment payment) {
        try {
            String recipientEmail = payment.getReceiptEmail();
            if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                // Fetch email from User Service
                try {
                    ApiResult<org.de013.paymentservice.dto.external.UserDto> apiResponse = userServiceClient.getUserById(payment.getUserId()).getBody();
                    if (apiResponse != null && apiResponse.getData() != null) {
                        org.de013.paymentservice.dto.external.UserDto userDto = apiResponse.getData();
                        if (userDto.getEmail() != null) {
                            recipientEmail = userDto.getEmail();
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch user email from User Service for user: {}", payment.getUserId(), e);
                }
            }

            if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                log.warn("Cannot send email notification: recipient email is missing");
                return;
            }

            log.info("Sending payment success email notification to: {}", recipientEmail);
            java.util.Map<String, Object> emailRequest = new java.util.HashMap<>();
            emailRequest.put("userId", payment.getUserId());
            emailRequest.put("to", recipientEmail);
            emailRequest.put("subject", "Thanh toán thành công cho Đơn hàng #" + payment.getOrderId());
            emailRequest.put("message", String.format(
                    "Xin chào,\n\nGiao dịch thanh toán của bạn cho Đơn hàng #%d đã được xử lý THÀNH CÔNG.\n" +
                    "Mã giao dịch: %s\n" +
                    "Số tiền: %s %s\n" +
                    "Phương thức thanh toán: %s\n" +
                    "Thời gian: %s\n\n" +
                    "Cảm ơn bạn đã mua sắm tại cửa hàng của chúng tôi!\n" +
                    "Trân trọng,\nGlobal Travel Buddy & Local Service Team",
                    payment.getOrderId(),
                    payment.getPaymentNumber(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getMethod(),
                    java.time.LocalDateTime.now().toString()
            ));

            notificationServiceClient.sendEmail(emailRequest);
            log.info("Successfully sent payment success email notification to: {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send email notification for payment: {}", payment.getPaymentNumber(), e);
        }
    }
}
