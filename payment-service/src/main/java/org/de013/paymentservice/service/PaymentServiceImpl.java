package org.de013.paymentservice.service;

import jakarta.persistence.EntityManager;

import jakarta.persistence.PersistenceContext;

import java.util.concurrent.atomic.AtomicBoolean;

import org.de013.paymentservice.gateway.stripe.StripeRetryVerifier;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.ApiResult;
import org.de013.paymentservice.dto.payment.PaymentResponse;
import org.de013.paymentservice.dto.payment.PaymentStatusResponse;
import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.paymentservice.dto.stripe.StripePaymentResponse;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.exception.PaymentNotFoundException;
import org.de013.paymentservice.exception.PaymentProcessingException;
import org.de013.paymentservice.gateway.PaymentGateway;
import org.de013.paymentservice.gateway.PaymentGatewayFactory;
import org.de013.paymentservice.gateway.stripe.StripePaymentGateway;
import org.de013.paymentservice.mapper.PaymentMapper;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.client.NotificationServiceClient;
import org.de013.paymentservice.util.PaymentNumberGenerator;
import org.de013.paymentservice.entity.PaymentTransaction;
import org.de013.paymentservice.entity.enums.TransactionType;
import org.de013.paymentservice.service.validation.PaymentRequestValidator;
import org.de013.paymentservice.service.vnpay.VnpayService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of PaymentService for payment processing operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRetryAttempts retryAttempts;
    private final StripeRetryVerifier retryVerifier;
    private final PaymentOutbox outbox;
    private final PaymentNotificationOutbox notificationOutbox;

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentMapper paymentMapper;
    private final OrderServiceClient orderServiceClient;
    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final PaymentRequestValidator paymentRequestValidator;
    private final VnpayService vnpayService;
    private final PaymentReservation reservation;
    private final PaymentOrderGuard guard;
    @PersistenceContext
    private EntityManager entityManager;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    // ========== PAYMENT PROCESSING ==========

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        String provider = request.getProvider() != null ? request.getProvider() : "STRIPE";
        if ("VNPAY".equalsIgnoreCase(provider)) return vnpayService.createCheckout(request);
        validatePaymentRequest(request);
        Payment reserved = reservation.reserve(createPaymentEntity(request));
        return locked(reserved.getId(), (payment, held) -> {
            requireNotHeld(held);
            paymentRequestValidator.validatePaymentAmount(payment.getOrderId(), payment.getAmount(), payment.getCurrency(), payment.getUserId());
            try {
                StripePaymentGateway gateway = (StripePaymentGateway) gatewayFactory.getGateway("STRIPE");
                StripePaymentResponse response = gateway.createPaymentIntent(gateway.mapToGatewayPaymentRequest(request));
                if (response.isCreatedByStripe() && response.getPaymentIntentId() != null
                        && !response.getPaymentIntentId().isBlank() && response.getLivemode() != null) {
                    payment.setStripeIntentCreatedByService(true);
                    payment.setStripeIntentLivemode(response.getLivemode());
                }
                return applyProviderResponse(payment, response, TransactionType.CHARGE, held, true);
            } catch (ResponseStatusException e) { throw e; }
            catch (Exception e) { throw new PaymentProcessingException("Failed to process payment", e); }
        });
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PaymentResponse confirmPayment(Long paymentId, String paymentMethodId) {
        return confirmPaymentInternal(paymentId, paymentMethodId, null);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PaymentResponse confirmPaymentForOwner(Long paymentId, String paymentMethodId, String actor, String idempotencyKey) {
        if (actor == null || actor.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        if (paymentMethodId == null || !paymentMethodId.matches("[A-Za-z0-9_]{1,255}")
                || idempotencyKey != null && !idempotencyKey.matches("[A-Za-z0-9._:-]{1,128}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid confirmation request");
        var retry = new AtomicBoolean();
        var replay = new AtomicBoolean();
        PaymentResponse snapshot = locked(paymentId, (payment, held) -> {
            requireOwner(payment, actor);
            if (idempotencyKey != null && retryAttempts.existing(paymentId, idempotencyKey, paymentMethodId,
                    payment.getAmount(), payment.getCurrency().name()) != null) {
                replay.set(true);
            } else if (payment.getStatus() == PaymentStatus.FAILED) {
                if (idempotencyKey == null)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required for retry");
                requireRetryAdmission(payment, held);
                retry.set(true);
            }
            return paymentMapper.toPaymentResponse(payment);
        });
        if (replay.get()) return retryReadBack(paymentId, actor);
        if (!retry.get()) return confirmPaymentInternal(paymentId, paymentMethodId, actor);
        var attempt = retryAttempts.claim(paymentId, idempotencyKey, paymentMethodId, snapshot.getAmount(), snapshot.getCurrency().name());
        if (!attempt.created()) return retryReadBack(paymentId, actor);
        var mutationStarted = new AtomicBoolean();
        try {
            return locked(paymentId, (payment, held) -> {
                requireOwner(payment, actor);
                requireRetryAdmission(payment, held);
                if (payment.getAmount().compareTo(snapshot.getAmount()) != 0 || payment.getCurrency() != snapshot.getCurrency())
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Retry payment changed");
                try {
                    if (!retryVerifier.eligible(payment))
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider does not permit retry");
                    requireRetryAdmission(payment, held);
                    mutationStarted.set(true);
                    var gateway = (StripePaymentGateway) gatewayFactory.getGateway("STRIPE");
                    var intent = gateway.confirmPaymentIntent(payment.getStripePaymentIntentId(), paymentMethodId, attempt.providerKey().toString());
                    if (!retryVerifier.matches(payment, intent)) throw new PaymentProcessingException("Retry provider identity cannot be verified");
                    PaymentResponse result = applyProviderResponse(payment, gateway.retryReadBack(intent), TransactionType.CHARGE, held, false);
                    if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.CANCELED)
                        retryAttempts.completed(attempt.id());
                    return result;
                } catch (ResponseStatusException e) { throw e; }
                catch (Exception e) { throw new PaymentProcessingException("Retry outcome requires reconciliation", e); }
            });
        } catch (RuntimeException e) {
            if (!mutationStarted.get()) retryAttempts.rejected(attempt.id());
            throw e;
        }
    }

    private PaymentResponse confirmPaymentInternal(Long paymentId, String paymentMethodId, String actor) {
        return locked(paymentId, (payment, held) -> {
            if (actor != null && !actor.equals(payment.getUserId()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment owner required");
            requireNotHeld(held);
            if (retryAttempts.active(paymentId))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Retry outcome requires reconciliation");
            if (payment.isVerifiedRelease() || payment.getStatus() != PaymentStatus.PENDING)
                throw new PaymentProcessingException("Payment cannot be confirmed. Current status: " + payment.getStatus());
            requireIntent(payment);
            paymentRequestValidator.validatePaymentAmount(payment.getOrderId(), payment.getAmount(), payment.getCurrency(), payment.getUserId());
            try {
                StripePaymentGateway gateway = (StripePaymentGateway) gatewayFactory.getGateway("STRIPE");
                return applyProviderResponse(payment, gateway.confirmPaymentIntent(payment.getStripePaymentIntentId(), paymentMethodId), TransactionType.CHARGE, held, false);
            } catch (ResponseStatusException e) { throw e; }
            catch (Exception e) { throw new PaymentProcessingException("Failed to confirm payment", e); }
        });
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PaymentResponse cancelPayment(Long paymentId, String reason) {
        return locked(paymentId, (payment, held) -> {
            if (!payment.isVerifiedRelease() && payment.getStatus() != PaymentStatus.SUCCEEDED) {
                outbox.transition(payment, PaymentStatus.CANCELED, held);
                payment.setFailureReason(reason);
                createPaymentTransaction(payment, null, TransactionType.CANCELLATION);
                paymentRepository.save(payment);
            }
            return paymentMapper.toPaymentResponse(payment);
        });
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PaymentResponse capturePayment(Long paymentId, BigDecimal amount) {
        return locked(paymentId, (payment, held) -> {
            requireNotHeld(held);
            if (payment.isVerifiedRelease() || payment.getStatus() != PaymentStatus.REQUIRES_CONFIRMATION)
                throw new PaymentProcessingException("Payment cannot be captured. Current status: " + payment.getStatus());
            requireIntent(payment);
            paymentRequestValidator.validatePaymentAmount(payment.getOrderId(), payment.getAmount(), payment.getCurrency(), payment.getUserId());
            try {
                StripePaymentGateway gateway = (StripePaymentGateway) gatewayFactory.getGateway("STRIPE");
                return applyProviderResponse(payment, gateway.capturePayment(payment.getStripePaymentIntentId(), amount != null ? amount : payment.getAmount()), TransactionType.CAPTURE, held, true);
            } catch (ResponseStatusException e) { throw e; }
            catch (Exception e) { throw new PaymentProcessingException("Failed to capture payment", e); }
        });
    }

    // ========== PAYMENT RETRIEVAL ==========

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentResponse> getPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(paymentMapper::toPaymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentResponse> getPaymentByNumber(String paymentNumber) {
        return paymentRepository.findByPaymentNumber(paymentNumber)
                .map(paymentMapper::toPaymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentResponse> getPaymentByStripePaymentIntentId(String stripePaymentIntentId) {
        return paymentRepository.findByStripePaymentIntentId(stripePaymentIntentId)
                .map(paymentMapper::toPaymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId).stream()
                .map(paymentMapper::toPaymentResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByUserId(String userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable)
                .map(paymentMapper::toPaymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByUserIdAndStatus(String userId, PaymentStatus status) {
        return paymentRepository.findByUserIdAndStatus(userId, status).stream()
                .map(paymentMapper::toPaymentResponse)
                .toList();
    }

    // ========== PAYMENT STATUS ==========

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(Long paymentId) {
        Payment payment = getPaymentEntityById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        return paymentMapper.toPaymentStatusResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatusByNumber(String paymentNumber) {
        Payment payment = paymentRepository.findByPaymentNumber(paymentNumber)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentNumber));
        return paymentMapper.toPaymentStatusResponse(payment);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PaymentResponse updatePaymentStatus(Long paymentId, PaymentStatus status, String reason) {
        return locked(paymentId, (payment, held) -> {
            if (!payment.isVerifiedRelease() && payment.getStatus() != PaymentStatus.SUCCEEDED) {
                outbox.transition(payment, status, held);
                if (status == PaymentStatus.SUCCEEDED) guard.recordSuccess(payment.getOrderId());
                if (reason != null) payment.setFailureReason(reason);
                paymentRepository.save(payment);
            }
            return paymentMapper.toPaymentResponse(payment);
        });
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public PaymentResponse syncPaymentStatusWithStripe(Long paymentId) {
        return locked(paymentId, (payment, held) -> {
            if (payment.getStripePaymentIntentId() == null) return paymentMapper.toPaymentResponse(payment);
            try {
                StripePaymentGateway gateway = (StripePaymentGateway) gatewayFactory.getGateway("STRIPE");
                StripePaymentResponse response;
                if (retryAttempts.exists(paymentId)) {
                    var intent = retryVerifier.current(payment);
                    if (intent == null) throw new PaymentProcessingException("Retry provider identity cannot be verified");
                    response = gateway.retryReadBack(intent);
                } else {
                    response = gateway.getPaymentIntent(payment.getStripePaymentIntentId());
                }
                return applyProviderResponse(payment, response, TransactionType.CHARGE, held, true);
            } catch (ResponseStatusException e) { throw e; }
            catch (Exception e) { throw new PaymentProcessingException("Failed to sync payment status", e); }
        });
    }

    // ========== PAYMENT VALIDATION ==========

    @Override
    public void validatePaymentRequest(ProcessPaymentRequest request) {
        paymentRequestValidator.validatePaymentRequest(request);
    }

    @Override
    public void validatePaymentAmount(Long orderId, BigDecimal amount) {
        paymentRequestValidator.validatePaymentAmount(orderId, amount);
    }

    @Override
    public void validateUserCanMakePayment(String userId) {
        paymentRequestValidator.validateUserCanMakePayment(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canCancelPayment(Long paymentId) {
        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            return false;
        }
        Payment payment = paymentOpt.get();
        return payment.getStatus() == PaymentStatus.PENDING ||
                payment.getStatus() == PaymentStatus.REQUIRES_ACTION;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canCapturePayment(Long paymentId) {
        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            return false;
        }
        Payment payment = paymentOpt.get();
        return payment.getStatus() == PaymentStatus.REQUIRES_CONFIRMATION;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRefundPayment(Long paymentId) {
        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            return false;
        }
        Payment payment = paymentOpt.get();
        return payment.getStatus() == PaymentStatus.SUCCEEDED && !payment.isFullyRefunded();
    }

    // ========== PAYMENT SEARCH ==========

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> searchPayments(
            String paymentNumber, String userId, Long orderId, PaymentStatus status,
            BigDecimal minAmount, BigDecimal maxAmount, LocalDateTime startDate, LocalDateTime endDate,
            Pageable pageable) {

        return paymentRepository.searchPayments(
                        paymentNumber, userId, orderId, status, minAmount, maxAmount, startDate, endDate, pageable)
                .map(paymentMapper::toPaymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getSuccessfulPaymentsByUserId(String userId) {
        return paymentRepository.findSuccessfulPaymentsByUserId(userId).stream()
                .map(paymentMapper::toPaymentResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getFailedPaymentsByUserId(String userId) {
        return paymentRepository.findFailedPaymentsByUserId(userId).stream()
                .map(paymentMapper::toPaymentResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPendingPaymentsByUserId(String userId) {
        return paymentRepository.findPendingPaymentsByUserId(userId).stream()
                .map(paymentMapper::toPaymentResponse)
                .toList();
    }

    // ========== PAYMENT STATISTICS ==========

    private PaymentService.PaymentStatistics calculateStatistics(List<Payment> payments, LocalDateTime defaultStart, LocalDateTime defaultEnd) {
        if (payments == null || payments.isEmpty()) {
            return new PaymentService.PaymentStatistics(
                    0L, 0L, 0L, 0L,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    defaultStart, defaultEnd);
        }

        long totalPayments = payments.size();
        long successfulPayments = 0;
        long failedPayments = 0;
        long pendingPayments = 0;

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal successfulAmount = BigDecimal.ZERO;

        LocalDateTime firstPaymentDate = null;
        LocalDateTime lastPaymentDate = null;

        for (Payment p : payments) {
            BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            totalAmount = totalAmount.add(amt);

            if (p.getStatus() == PaymentStatus.SUCCEEDED) {
                successfulPayments++;
                successfulAmount = successfulAmount.add(amt);
            } else if (p.getStatus() == PaymentStatus.FAILED) {
                failedPayments++;
            } else if (p.getStatus() != PaymentStatus.CANCELED) {
                pendingPayments++;
            }

            LocalDateTime created = p.getCreatedAt();
            if (created != null) {
                if (firstPaymentDate == null || created.isBefore(firstPaymentDate)) {
                    firstPaymentDate = created;
                }
                if (lastPaymentDate == null || created.isAfter(lastPaymentDate)) {
                    lastPaymentDate = created;
                }
            }
        }

        BigDecimal averageAmount = totalPayments > 0 
                ? totalAmount.divide(BigDecimal.valueOf(totalPayments), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new PaymentService.PaymentStatistics(
                totalPayments,
                successfulPayments,
                failedPayments,
                pendingPayments,
                totalAmount,
                successfulAmount,
                averageAmount,
                firstPaymentDate != null ? firstPaymentDate : defaultStart,
                lastPaymentDate != null ? lastPaymentDate : defaultEnd
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatistics getPaymentStatisticsByUserId(String userId) {
        log.info("Calculating payment statistics for user: {}", userId);
        List<Payment> payments = paymentRepository.findByUserId(userId);
        return calculateStatistics(payments, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatistics getPaymentStatisticsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Calculating payment statistics for date range: {} to {}", startDate, endDate);
        List<Payment> payments = paymentRepository.findByCreatedAtBetween(startDate, endDate);
        return calculateStatistics(payments, startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalPaymentAmountByUserId(String userId) {
        return paymentRepository.sumSuccessfulPaymentsByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getPaymentCountByStatus(PaymentStatus status) {
        return paymentRepository.countByStatus(status);
    }

    // ========== UTILITY METHODS ==========

    @Override
    public String generatePaymentNumber() {
        String paymentNumber;
        do {
            paymentNumber = PaymentNumberGenerator.generatePaymentNumber();
        } while (paymentNumberExists(paymentNumber));
        return paymentNumber;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean paymentNumberExists(String paymentNumber) {
        return paymentRepository.existsByPaymentNumber(paymentNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentEntityById(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }

    @Override
    public Payment savePaymentEntity(Payment payment) {
        throw new UnsupportedOperationException("Unrestricted payment entity saves are not supported");
    }

    private PaymentResponse locked(Long paymentId, java.util.function.BiFunction<Payment, Boolean, PaymentResponse> action) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            Long orderId = paymentRepository.findOrderIdByPaymentId(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
            boolean held = guard.lock(orderId);
            Payment payment = paymentRepository.findById(paymentId)
                    .filter(p -> orderId.equals(p.getOrderId()))
                    .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
            entityManager.refresh(payment);
            if (!orderId.equals(payment.getOrderId())) throw new PaymentNotFoundException("Payment correlation changed: " + paymentId);
            return action.apply(payment, held);
        });
    }

    private PaymentResponse retryReadBack(Long paymentId, String actor) {
        return locked(paymentId, (payment, held) -> {
            requireOwner(payment, actor);
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) return paymentMapper.toPaymentResponse(payment);
            if (retryAttempts.hosted(paymentId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment cannot be reconciled as a retry");
            try {
                var current = retryVerifier.current(payment);
                if (current == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider identity cannot be verified");
                // A failed/action/processing read does not prove which attempt completed; keep its durable fence.
                if (!"succeeded".equals(current.getStatus()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Retry outcome requires reconciliation");
                var gateway = (StripePaymentGateway) gatewayFactory.getGateway("STRIPE");
                PaymentResponse response = applyProviderResponse(payment, gateway.retryReadBack(current), TransactionType.CHARGE, held, false);
                return response;
            } catch (ResponseStatusException e) { throw e; }
            catch (Exception e) { throw new PaymentProcessingException("Retry outcome requires reconciliation", e); }
        });
    }

    private void requireOwner(Payment payment, String actor) {
        if (!actor.equals(payment.getUserId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment owner required");
    }

    private void requireRetryAdmission(Payment payment, boolean held) {
        requireNotHeld(held);
        if (payment.getStatus() != PaymentStatus.FAILED || !payment.isBlocking() || payment.isVerifiedRelease()
                || !"STRIPE".equals(payment.getGatewayName()) || retryAttempts.hosted(payment.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment cannot be retried");
        requireIntent(payment);
        var response = orderServiceClient.getOrderById(payment.getOrderId());
        var order = response == null ? null : response.getBody();
        String currency = order == null || order.getTotalAmount() == null ? null : order.getTotalAmount().getCurrency();
        if (currency == null && order != null) currency = order.getCurrency();
        if (response == null || !response.getStatusCode().is2xxSuccessful() || order == null
                || !Boolean.TRUE.equals(order.getPaymentRetryEligible()) || !Boolean.TRUE.equals(order.getInventoryReady())
                || !"PENDING".equals(order.getStatus()) || !payment.getOrderId().equals(order.getId())
                || !payment.getUserId().equals(order.getUserId()) || order.getTotalAmount() == null
                || order.getTotalAmount().getAmount() == null || payment.getAmount().compareTo(order.getTotalAmount().getAmount()) != 0
                || payment.getCurrency() == null || !payment.getCurrency().name().equals(currency))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order does not permit retry");
    }

    private void requireNotHeld(boolean held) {
        if (held) throw new ResponseStatusException(HttpStatus.CONFLICT, "Order requires payment reconciliation");
    }

    private void requireIntent(Payment payment) {
        if (payment.getStripePaymentIntentId() == null || payment.getStripePaymentIntentId().isBlank())
            throw new PaymentProcessingException("Payment Intent ID is missing");
    }

    private PaymentResponse applyProviderResponse(Payment payment, StripePaymentResponse response, TransactionType type, boolean held, boolean notify) {
        String intent = response.getPaymentIntentId();
        if (intent != null && payment.getStripePaymentIntentId() != null && !intent.equals(payment.getStripePaymentIntentId()))
            throw new PaymentProcessingException("Payment intent mismatch");
        if (intent != null && payment.getStripePaymentIntentId() == null) payment.setStripePaymentIntentId(intent);
        if (response.getCustomerId() != null) payment.setStripeCustomerId(response.getCustomerId());
        if (response.getPaymentMethodId() != null) payment.setStripePaymentMethodId(response.getPaymentMethodId());
        PaymentStatus previous = payment.getStatus();
        PaymentStatus next = mapStripeStatusToPaymentStatus(response.getStatus());
        if (next == PaymentStatus.SUCCEEDED) {
            outbox.transition(payment, next, held || payment.isVerifiedRelease());
            guard.recordSuccess(payment.getOrderId());
            if (payment.isVerifiedRelease()) guard.hold(payment.getOrderId());
        } else if (!payment.isVerifiedRelease() && previous != PaymentStatus.SUCCEEDED) {
            outbox.transition(payment, next, held);
            if (response.getFailureMessage() != null) payment.setFailureReason(response.getFailureMessage());
        }
        createPaymentTransaction(payment, response, type);
        paymentRepository.save(payment);
        if (!held && !payment.isVerifiedRelease() && previous != payment.getStatus()) {
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                if (!outbox.enabled()) updateOrderStatus(payment.getOrderId(), "PAID", payment);
                if (notificationOutbox.enabled()) notificationOutbox.enqueue(payment);
                else if (notify) sendPaymentSuccessNotification(payment);
            } else if (payment.getStatus() == PaymentStatus.FAILED) {
                updateOrderStatus(payment.getOrderId(), "PAYMENT_FAILED", payment);
            }
        }
        return paymentMapper.toPaymentResponse(payment);
    }

    // ========== HELPER METHODS ==========

    private Payment createPaymentEntity(ProcessPaymentRequest request) {
        return Payment.builder()
                .paymentNumber(generatePaymentNumber())
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentStatus.PENDING)
                .gatewayName("STRIPE")
                .method(request.getPaymentMethodType())
                .description(request.getDescription())
                .receiptEmail(request.getReceiptEmail())
                .createdBy("SYSTEM")
                .updatedBy("SYSTEM")
                .build();
    }

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

    private PaymentStatus mapStripeStatusToPaymentStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return PaymentStatus.PENDING;
        }

        return switch (stripeStatus.toLowerCase()) {
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "requires_payment_method" -> PaymentStatus.REQUIRES_PAYMENT_METHOD;
            case "requires_confirmation" -> PaymentStatus.REQUIRES_CONFIRMATION;
            case "requires_action" -> PaymentStatus.REQUIRES_ACTION;
            case "processing" -> PaymentStatus.PROCESSING;
            case "canceled" -> PaymentStatus.CANCELED;
            case "failed" -> PaymentStatus.FAILED;
            default -> {
                log.warn("Unknown Stripe status: {}, defaulting to PENDING", stripeStatus);
                yield PaymentStatus.PENDING;
            }
        };
    }

    private void createPaymentTransaction(Payment payment, StripePaymentResponse stripeResponse, TransactionType transactionType) {
        log.info("Creating payment transaction of type {} for payment: {}", transactionType, payment.getId());

        PaymentTransaction transaction = PaymentTransaction.builder()
                .payment(payment)
                .type(transactionType)
                .amount(payment.getAmount())
                .currency(payment.getCurrency() != null ? payment.getCurrency().name() : null)
                .status(payment.getStatus())
                .description(payment.getDescription() != null ? payment.getDescription() : (transactionType + " transaction"))
                .createdBy("SYSTEM")
                .updatedBy("SYSTEM")
                .build();

        if (stripeResponse != null) {
            transaction.setStripePaymentIntentId(stripeResponse.getPaymentIntentId());
            // Do not persist response objects that can contain client secrets.
            transaction.setGatewayTransactionId(stripeResponse.getPaymentIntentId());
            if (stripeResponse.getFailureMessage() != null) {
                transaction.setFailureReason(stripeResponse.getFailureMessage());
            }
        }

        payment.addTransaction(transaction);
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
                    java.time.LocalDateTime.now()
            ));

            notificationServiceClient.sendEmail(emailRequest);
            log.info("Successfully sent payment success email notification to: {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send email notification for payment: {}", payment.getPaymentNumber(), e);
        }
    }
}
