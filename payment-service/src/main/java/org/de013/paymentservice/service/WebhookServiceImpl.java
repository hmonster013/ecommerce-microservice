package org.de013.paymentservice.service;

import jakarta.persistence.EntityManager;

import jakarta.persistence.PersistenceContext;

import org.de013.paymentservice.gateway.stripe.StripeRetryVerifier;

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
import org.de013.paymentservice.exception.InvalidStripeWebhookException;
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
    private final PaymentRetryAttempts retryAttempts;
    private final StripeRetryVerifier retryVerifier;
    private final PaymentOutbox outbox;
    private final PaymentNotificationOutbox notificationOutbox;

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
    @PersistenceContext
    private EntityManager entityManager;

    // ========== WEBHOOK PROCESSING ==========

    @Override
    public void processStripeWebhook(String payload, String signature) {
        log.info("Processing Stripe webhook");

        try {
            // Verify signature
            if (!verifyWebhookSignature(payload, signature, getWebhookSecret())) {
                log.warn("Invalid webhook signature");
                throw new InvalidStripeWebhookException(null);
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

        } catch (InvalidStripeWebhookException e) {
            throw e;
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
        } catch (InvalidStripeWebhookException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Failed to verify webhook signature", e);
        }
    }

    @Override
    public StripeWebhookRequest parseWebhookPayload(String payload) {
        try {
            return stripeWebhookService.parseWebhookPayload(payload);
        } catch (InvalidStripeWebhookException e) {
            throw e;
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
                case "refund.updated", "refund.failed" -> handleRefundUpdated(webhookRequest);
                default -> {
                    if (eventType.startsWith("charge.") || eventType.contains("refund")) {
                        String id = webhookRequest.getEventId();
                        if (id == null || id.isBlank() || id.length() > 255 || eventType.length() > 80)
                            throw new PaymentGatewayException("Invalid financial event identity");
                        // ponytail: unsupported financial semantics are globally unresolved; classify only when explicitly supported.
                        entityManager.createNativeQuery("INSERT INTO stripe_financial_observation(event_id,event_type) VALUES (:id,:type) ON CONFLICT (event_id) DO NOTHING")
                                .setParameter("id", id).setParameter("type", eventType).executeUpdate();
                    } else log.debug("Unhandled webhook event type: {}", eventType);
                }
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
        entityManager.refresh(payment);
        if (!orderId.equals(payment.getOrderId()) || !id.equals(payment.getStripePaymentIntentId()))
            throw new PaymentGatewayException("Payment intent correlation changed");
        if (isHostedCheckout(payment)) return true;
        if (!"STRIPE".equals(payment.getGatewayName())) {
            throw new PaymentGatewayException("Payment intent provider is not correlated");
        }
        if (event.getAccount() != null) {
            recordFinancialObservation(event, orderId);
            guard.hold(orderId);
            return true;
        }
        if (retryAttempts.exists(payment.getId()) && ("payment_intent.payment_failed".equals(event.getEventType())
                || "payment_intent.requires_action".equals(event.getEventType())
                || "payment_intent.succeeded".equals(event.getEventType()))) {
            try {
                if (retryAttempts.hosted(payment.getId())) throw new PaymentGatewayException("Retry payment correlation changed");
                var current = retryVerifier.current(payment);
                if (current == null) throw new PaymentGatewayException("Retry provider identity cannot be verified");
                String expected = switch (event.getEventType()) {
                    case "payment_intent.payment_failed" -> "requires_payment_method";
                    case "payment_intent.requires_action" -> "requires_action";
                    default -> "succeeded";
                };
                if (!expected.equals(current.getStatus())) {
                    if ("payment_intent.succeeded".equals(event.getEventType()))
                        throw new PaymentGatewayException("Retry provider success cannot be verified");
                    return true;
                }
            } catch (com.stripe.exception.StripeException e) {
                throw new PaymentGatewayException("Retry provider state is unavailable", e);
            }
        }
        recordFinancialObservation(event, orderId);
        PaymentStatus before = payment.getStatus();
        boolean wasBlocking = payment.isBlocking();
        boolean wasReleased = payment.isVerifiedRelease();
        switch (event.getEventType()) {
            case "payment_intent.canceled" -> {
                if (payment.getStatus() != PaymentStatus.SUCCEEDED && !payment.isVerifiedRelease()) {
                    outbox.transition(payment, PaymentStatus.CANCELED, held);
                    payment.setBlocking(false);
                    payment.setVerifiedRelease(true);
                }
            }
            case "payment_intent.succeeded" -> {
                boolean alreadySucceeded = payment.getStatus() == PaymentStatus.SUCCEEDED;
                outbox.transition(payment, PaymentStatus.SUCCEEDED, held || payment.isVerifiedRelease());
                guard.recordSuccess(orderId);
                if (payment.isVerifiedRelease()) {
                    guard.hold(orderId);
                } else if (!held && !alreadySucceeded) {
                    if (!outbox.enabled()) updateOrderStatus(orderId, "PAID", payment);
                    if (notificationOutbox.enabled()) notificationOutbox.enqueue(payment);
                    else sendPaymentSuccessNotification(payment);
                }
            }
            case "payment_intent.payment_failed" -> {
                if (!payment.isVerifiedRelease() && payment.getStatus() != PaymentStatus.SUCCEEDED) {
                    outbox.transition(payment, PaymentStatus.FAILED, held);
                    payment.setFailureReason(event.getFailureReason() != null ? event.getFailureReason() : "Payment failed via webhook");
                    if (!held) updateOrderStatus(orderId, "PAYMENT_FAILED", payment);
                }
            }
            case "payment_intent.requires_action" -> {
                if (!payment.isVerifiedRelease() && payment.getStatus() != PaymentStatus.SUCCEEDED) {
                    outbox.transition(payment, PaymentStatus.REQUIRES_ACTION, held);
                }
            }
            default -> log.debug("Unhandled webhook event type: {}", event.getEventType());
        }
        if (before != payment.getStatus() || wasBlocking != payment.isBlocking() || wasReleased != payment.isVerifiedRelease())
            guard.financialChange(orderId);
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
        recordFinancialObservation(webhookRequest);
    }

    @Override
    public void handleChargeFailed(StripeWebhookRequest webhookRequest) {
        recordFinancialObservation(webhookRequest);
    }

    @Override
    public void handleChargeDisputeCreated(StripeWebhookRequest webhookRequest) {
        recordFinancialObservation(webhookRequest);
    }

    private void recordFinancialObservation(StripeWebhookRequest event) {
        recordFinancialObservation(event, null);
    }

    private void recordFinancialObservation(StripeWebhookRequest event, Long affectedOrder) {
        if (event.getEventId() == null || event.getEventId().isBlank() || event.getEventId().length() > 255
                || event.getEventType() == null || event.getEventType().length() > 80)
            throw new PaymentGatewayException("Invalid financial event identity");
        String object = event.getObjectId();
        String intent = event.getPaymentIntentId();
        String charge = "charge".equals(event.getObjectType()) ? object : null;
        if ("payment_intent".equals(event.getObjectType()) && event.getData() != null && event.getData().getObject() != null
                && event.getData().getObject().get("latest_charge") instanceof String id) charge = id;
        if ("dispute".equals(event.getObjectType()) && event.getData() != null && event.getData().getObject() != null
                && event.getData().getObject().get("charge") instanceof String id) charge = id;
        if ("refund".equals(event.getObjectType()) && event.getData() != null && event.getData().getObject() != null
                && event.getData().getObject().get("charge") instanceof String id) charge = id;
        if (intent == null && event.getData() != null && event.getData().getObject() != null
                && event.getData().getObject().get("payment_intent") instanceof String id) intent = id;
        if ((object != null && object.length() > 255) || (intent != null && intent.length() > 255)
                || (charge != null && charge.length() > 255)
                || (event.getStatus() != null && event.getStatus().length() > 80)
                || (event.getCurrency() != null && event.getCurrency().length() > 16)
                || (event.getAccount() != null && event.getAccount().length() > 255))
            throw new PaymentGatewayException("Invalid financial event facts");
        Long orderId;
        if ("refund".equals(event.getObjectType())) {
            Long paymentId = object == null ? null : refundRepository.findPaymentIdByStripeRefundId(object).orElse(null);
            orderId = paymentId == null ? null : paymentRepository.findOrderIdByPaymentId(paymentId).orElse(null);
        } else {
            orderId = intent == null || event.getAccount() != null ? null : paymentRepository.findOrderIdByStripeIntent(intent).orElse(null);
        }
        if (orderId != null) guard.lock(orderId);
        boolean complete = (orderId != null || affectedOrder != null) && object != null && charge != null && intent != null && event.getStatus() != null
                && event.getAmount() != null && event.getCurrency() != null && event.getLivemode() != null;
        String kind = "refund".equals(event.getObjectType()) ? "refund.observed" : event.getEventType();
        boolean existing = complete && Boolean.TRUE.equals(entityManager.createNativeQuery("SELECT EXISTS (SELECT 1 FROM stripe_financial_observation WHERE order_id IS NOT DISTINCT FROM :orderId AND account_id IS NOT DISTINCT FROM :account AND event_type = :type AND object_id = :object AND intent_id = :intent AND charge_id = :charge AND status = :status AND amount = :amount AND currency = :currency AND livemode = :mode)")
                .setParameter("orderId", orderId).setParameter("type", kind).setParameter("object", object)
                .setParameter("account", event.getAccount())
                .setParameter("intent", intent).setParameter("charge", charge).setParameter("status", event.getStatus())
                .setParameter("amount", event.getAmount()).setParameter("currency", event.getCurrency()).setParameter("mode", event.getLivemode())
                .getSingleResult());
        int inserted = entityManager.createNativeQuery("INSERT INTO stripe_financial_observation(event_id,event_type,object_id,intent_id,charge_id,status,amount,currency,livemode,account_id,order_id) VALUES (:event,:type,:object,:intent,:charge,:status,:amount,:currency,:mode,:account,:orderId) ON CONFLICT (event_id) DO NOTHING")
                .setParameter("event", event.getEventId()).setParameter("type", kind).setParameter("object", object)
                .setParameter("intent", intent).setParameter("charge", charge).setParameter("status", event.getStatus())
                .setParameter("amount", event.getAmount()).setParameter("currency", event.getCurrency()).setParameter("mode", event.getLivemode())
                .setParameter("account", event.getAccount()).setParameter("orderId", orderId).executeUpdate();
        Long changedOrder = affectedOrder != null ? affectedOrder : orderId;
        if (inserted == 1 && changedOrder != null && !existing) guard.financialChange(changedOrder);
    }

    // ========== REFUND EVENTS ==========

    @Override
    public void handleRefundCreated(StripeWebhookRequest webhookRequest) {
        handleRefundUpdated(webhookRequest);
    }

    @Override
    public void handleRefundUpdated(StripeWebhookRequest webhookRequest) {
        String refundId = webhookRequest.getRefundId();
        log.info("Handling refund updated: {}", refundId);

        if (refundId == null || refundId.isBlank()) { recordFinancialObservation(webhookRequest); return; }
        var paymentId = refundRepository.findPaymentIdByStripeRefundId(refundId);
        if (paymentId.isEmpty()) { recordFinancialObservation(webhookRequest); return; }
        lockRefundWebhook(refundId);
        Refund refund = refundRepository.findByStripeRefundId(refundId).orElseThrow();
        verifyRefundOrder(refund);
        Payment payment = paymentRepository.findById(paymentId.get()).orElseThrow();
        var object = webhookRequest.getData() == null ? null : webhookRequest.getData().getObject();
        String charge = object != null && object.get("charge") instanceof String s ? s : null;
        String intent = object != null && object.get("payment_intent") instanceof String s ? s : null;
        Long amount = null;
        try { amount = refund.getAmount().movePointRight(2).longValueExact(); } catch (ArithmeticException ignored) { }
        boolean valid = webhookRequest.getAccount() == null && webhookRequest.getLivemode() != null
                && webhookRequest.getLivemode().equals(payment.getStripeIntentLivemode()) && payment.isStripeIntentCreatedByService()
                && amount != null && amount.equals(webhookRequest.getAmount()) && refund.getCurrency() != null
                && refund.getCurrency().equalsIgnoreCase(webhookRequest.getCurrency())
                && charge != null && charge.equals(refund.getStripeChargeId())
                && intent != null && intent.equals(refund.getStripePaymentIntentId()) && intent.equals(payment.getStripePaymentIntentId())
                && webhookRequest.getStatus() != null;
        recordFinancialObservation(webhookRequest);
        if (!valid) { guard.hold(refund.getOrderId()); return; }
        RefundStatus next = switch (webhookRequest.getStatus()) {
            case "succeeded" -> RefundStatus.SUCCEEDED;
            case "pending", "requires_action" -> RefundStatus.PROCESSING;
            case "failed" -> RefundStatus.FAILED;
            case "canceled" -> RefundStatus.CANCELED;
            default -> null;
        };
        if (next == null || ("refund.failed".equals(webhookRequest.getEventType()) && next != RefundStatus.FAILED)) {
            guard.hold(refund.getOrderId()); return;
        }
        if (refund.getStatus() == RefundStatus.SUCCEEDED && next != RefundStatus.SUCCEEDED) { guard.hold(refund.getOrderId()); return; }
        if (refund.getStatus() != next) { refund.setStatus(next); refundRepository.save(refund); }
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

    private void verifyRefundOrder(Refund refund) {
        Long orderId = paymentRepository.findOrderIdByPaymentId(refund.getPaymentId())
                .orElseThrow(() -> new PaymentGatewayException("Refund payment is not correlated"));
        if (!orderId.equals(refund.getOrderId())) throw new PaymentGatewayException("Refund order is not correlated");
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
            org.springframework.http.ResponseEntity<Void> response;
            if ("PAID".equals(status)) {
                response = orderServiceClient.markOrderAsPaid(orderId, payment.getId(), payment.getPaymentNumber());
            } else if ("PAYMENT_FAILED".equals(status)) {
                response = orderServiceClient.markOrderPaymentFailed(orderId, payment.getFailureReason() != null ? payment.getFailureReason() : "Payment failed");
            } else {
                org.de013.paymentservice.dto.external.OrderStatusUpdateRequest request = new org.de013.paymentservice.dto.external.OrderStatusUpdateRequest(
                        status,
                        "Payment status updated to " + status
                );
                response = orderServiceClient.updateOrderStatus(orderId, request);
            }
            if (response.getStatusCode().is2xxSuccessful()) log.info("Successfully updated order {} status to: {}", orderId, status);
            else log.warn("Failed to update order {} status to {}: HTTP {}", orderId, status, response.getStatusCode().value());
        } catch (Exception e) {
            log.warn("Failed to update order {} status to {}", orderId, status, e);
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
