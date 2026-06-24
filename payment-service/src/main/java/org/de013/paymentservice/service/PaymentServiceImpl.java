package org.de013.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.dto.payment.PaymentResponse;
import org.de013.paymentservice.dto.payment.PaymentStatusResponse;
import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.paymentservice.dto.stripe.StripePaymentResponse;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.common.exception.ConflictException;
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
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.de013.paymentservice.entity.PaymentTransaction;
import org.de013.paymentservice.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;

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

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentMapper paymentMapper;
    private final OrderServiceClient orderServiceClient;
    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;

    // ========== PAYMENT PROCESSING ==========

    @Override
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        log.info("Processing payment for order: {}, user: {}, amount: {}",
                request.getOrderId(), request.getUserId(), request.getAmount());

        try {
            validatePaymentRequest(request);

            Payment payment = createPaymentEntity(request);
            payment = paymentRepository.save(payment);

            // Get Stripe gateway
            PaymentGateway gateway = gatewayFactory.getGateway("STRIPE");
            StripePaymentGateway stripeGateway = (StripePaymentGateway) gateway;

            // Map and process payment
            org.de013.paymentservice.dto.stripe.StripePaymentRequest stripeRequest = stripeGateway.mapToGatewayPaymentRequest(request);
            org.de013.paymentservice.dto.stripe.StripePaymentResponse stripeResponse = stripeGateway.createPaymentIntent(stripeRequest);
            stripeGateway.mapGatewayResponseToPayment(stripeResponse, payment);

            // Create transaction record
            createPaymentTransaction(payment, stripeResponse, TransactionType.CHARGE);

            // Save updated payment with stripe details
            payment = paymentRepository.save(payment);

            // If confirmPayment was true and status is succeeded, update order status and send notification
            if (payment.getStatus() == org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED) {
                updateOrderStatus(payment.getOrderId(), "PAID", payment);
                sendPaymentSuccessNotification(payment);
            } else if (payment.getStatus() == org.de013.paymentservice.entity.enums.PaymentStatus.FAILED) {
                updateOrderStatus(payment.getOrderId(), "PAYMENT_FAILED", payment);
            }

            log.info("Payment processed successfully: {}", payment.getPaymentNumber());
            return paymentMapper.toPaymentResponse(payment);

        } catch (Exception e) {
            log.error("Failed to process payment for order: {}", request.getOrderId(), e);
            throw new PaymentProcessingException("Failed to process payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResponse confirmPayment(Long paymentId, String paymentMethodId) {
        log.info("Confirming payment: {} with payment method: {}", paymentId, paymentMethodId);

        Payment payment = getPaymentEntityById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        try {
            // Validate payment can be confirmed
            if (payment.getStatus() != PaymentStatus.PENDING) {
                throw new PaymentProcessingException("Payment cannot be confirmed. Current status: " + payment.getStatus());
            }

            if (payment.getStripePaymentIntentId() == null) {
                throw new PaymentProcessingException("Payment Intent ID is missing");
            }

            // Get Stripe gateway
            PaymentGateway gateway = gatewayFactory.getGateway("STRIPE");
            StripePaymentGateway stripeGateway = (StripePaymentGateway) gateway;

            // Confirm payment intent with Stripe
            StripePaymentResponse stripeResponse = stripeGateway.confirmPaymentIntent(
                    payment.getStripePaymentIntentId(),
                    paymentMethodId
            );

            // Update payment based on Stripe response
            updatePaymentFromStripeResponse(payment, stripeResponse);

            // Create transaction record
            createPaymentTransaction(payment, stripeResponse, TransactionType.CHARGE);

            // Save updated payment
            payment = paymentRepository.save(payment);

            // Update order status if payment is successful
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                updateOrderStatus(payment.getOrderId(), "PAID", payment);
                log.info("Payment confirmed successfully: {}", payment.getPaymentNumber());
            } else if (payment.getStatus() == PaymentStatus.FAILED) {
                updateOrderStatus(payment.getOrderId(), "PAYMENT_FAILED", payment);
                log.warn("Payment confirmation failed: {}", payment.getPaymentNumber());
            }

            return paymentMapper.toPaymentResponse(payment);

        } catch (Exception e) {
            log.error("Failed to confirm payment: {}", paymentId, e);

            // Update payment status to failed
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            payment = paymentRepository.save(payment);

            throw new PaymentProcessingException("Failed to confirm payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResponse cancelPayment(Long paymentId, String reason) {
        Payment payment = getPaymentEntityById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        payment.setStatus(PaymentStatus.CANCELED);
        payment.setFailureReason(reason);

        // Create transaction record
        createPaymentTransaction(payment, null, TransactionType.CANCELLATION);

        payment = paymentRepository.save(payment);

        return paymentMapper.toPaymentResponse(payment);
    }

    @Override
    public PaymentResponse capturePayment(Long paymentId, BigDecimal amount) {
        log.info("Capturing payment: {} with amount: {}", paymentId, amount);

        Payment payment = getPaymentEntityById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        try {
            if (!canCapturePayment(paymentId)) {
                throw new PaymentProcessingException("Payment cannot be captured. Current status: " + payment.getStatus());
            }

            if (payment.getStripePaymentIntentId() == null) {
                throw new PaymentProcessingException("Payment Intent ID is missing");
            }

            // Get Stripe gateway
            PaymentGateway gateway = gatewayFactory.getGateway("STRIPE");
            StripePaymentGateway stripeGateway = (StripePaymentGateway) gateway;

            // Capture payment with Stripe
            BigDecimal captureAmount = amount != null ? amount : payment.getAmount();
            StripePaymentResponse stripeResponse = stripeGateway.capturePayment(
                    payment.getStripePaymentIntentId(),
                    captureAmount
            );

            // Update payment based on Stripe response
            updatePaymentFromStripeResponse(payment, stripeResponse);

            // Create transaction record
            createPaymentTransaction(payment, stripeResponse, TransactionType.CAPTURE);

            // Save updated payment
            payment = paymentRepository.save(payment);

            // Update order status if payment is successful
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                updateOrderStatus(payment.getOrderId(), "PAID", payment);
                sendPaymentSuccessNotification(payment);
                log.info("Payment captured successfully: {}", payment.getPaymentNumber());
            } else if (payment.getStatus() == PaymentStatus.FAILED) {
                updateOrderStatus(payment.getOrderId(), "PAYMENT_FAILED", payment);
                log.warn("Payment capture failed: {}", payment.getPaymentNumber());
            }

            return paymentMapper.toPaymentResponse(payment);

        } catch (Exception e) {
            log.error("Failed to capture payment: {}", paymentId, e);
            throw new PaymentProcessingException("Failed to capture payment: " + e.getMessage(), e);
        }
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
    public PaymentResponse updatePaymentStatus(Long paymentId, PaymentStatus status, String reason) {
        Payment payment = getPaymentEntityById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        payment.setStatus(status);
        if (reason != null) {
            payment.setFailureReason(reason);
        }
        payment = paymentRepository.save(payment);

        return paymentMapper.toPaymentResponse(payment);
    }

    @Override
    public PaymentResponse syncPaymentStatusWithStripe(Long paymentId) {
        log.info("Syncing payment status with Stripe: {}", paymentId);

        Payment payment = getPaymentEntityById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        if (payment.getStripePaymentIntentId() == null) {
            log.warn("Stripe Payment Intent ID is missing for payment: {}", paymentId);
            return paymentMapper.toPaymentResponse(payment);
        }

        try {
            // Get Stripe gateway
            PaymentGateway gateway = gatewayFactory.getGateway("STRIPE");
            StripePaymentGateway stripeGateway = (StripePaymentGateway) gateway;

            // Retrieve payment intent from Stripe
            StripePaymentResponse stripeResponse = stripeGateway.getPaymentIntent(payment.getStripePaymentIntentId());

            // Update payment based on Stripe response
            updatePaymentFromStripeResponse(payment, stripeResponse);

            // Create transaction record
            createPaymentTransaction(payment, stripeResponse, TransactionType.CHARGE);

            // Save updated payment
            payment = paymentRepository.save(payment);

            // Update order status if status changed to SUCCEEDED or FAILED
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                updateOrderStatus(payment.getOrderId(), "PAID", payment);
                sendPaymentSuccessNotification(payment);
            } else if (payment.getStatus() == PaymentStatus.FAILED) {
                updateOrderStatus(payment.getOrderId(), "PAYMENT_FAILED", payment);
            }

            log.info("Payment sync complete for payment {}. Status: {}", payment.getPaymentNumber(), payment.getStatus());
            return paymentMapper.toPaymentResponse(payment);

        } catch (Exception e) {
            log.error("Failed to sync payment status with Stripe: {}", paymentId, e);
            throw new PaymentProcessingException("Failed to sync payment status: " + e.getMessage(), e);
        }
    }

    // ========== PAYMENT VALIDATION ==========

    @Override
    public void validatePaymentRequest(ProcessPaymentRequest request) {
        if (request == null) {
            throw new PaymentProcessingException("Payment request cannot be null");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException("Payment amount must be greater than zero");
        }
        if (request.getOrderId() == null) {
            throw new PaymentProcessingException("Order ID is required");
        }
        if (request.getUserId() == null) {
            throw new PaymentProcessingException("User ID is required");
        }

        // Validate with Order Service (Stripe Amount Tampering Protection)
        validatePaymentAmount(request.getOrderId(), request.getAmount());

        // Validate with User Service (Check blocked / high risk status)
        validateUserCanMakePayment(request.getUserId());
    }

    @Override
    public void validatePaymentAmount(Long orderId, BigDecimal amount) {
        log.info("Validating payment amount {} for order {}", amount, orderId);
        try {
            ResponseEntity<org.de013.paymentservice.dto.external.OrderDto> response = orderServiceClient.getOrderById(orderId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                org.de013.paymentservice.dto.external.OrderDto order = response.getBody();
                
                // Business rule validation: Do not allow payment for an order in a non-payable state
                if ("PAID".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus())) {
                    log.error("Order {} is already paid or completed. Status: {}", orderId, order.getStatus());
                    throw new ConflictException("Order is already paid: " + orderId);
                }
                if ("CANCELLED".equals(order.getStatus())) {
                    log.error("Order {} is cancelled, cannot process payment", orderId);
                    throw new ConflictException("Order is cancelled: " + orderId);
                }

                BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount().getAmount() : BigDecimal.ZERO;
                if (orderTotal.compareTo(amount) != 0) {
                    log.error("Payment amount mismatch: request amount={}, order total={}", amount, orderTotal);
                    throw new PaymentProcessingException("Payment amount mismatch. Required: " + orderTotal + ", Provided: " + amount);
                }
                log.info("Payment amount matches order total: {}", orderTotal);
            } else {
                throw new PaymentProcessingException("Failed to validate payment: Order not found: " + orderId);
            }
        } catch (ConflictException | PaymentProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error communicating with Order Service for verification: ", e);
            throw new PaymentProcessingException("Failed to verify payment amount: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateUserCanMakePayment(String userId) {
        log.info("Validating with User Service if user can make payment: {}", userId);
        try {
            ResponseEntity<UserValidationResponse> response = userServiceClient.validateUserForPayment(userId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UserValidationResponse validation = response.getBody();
                if (!validation.isValid()) {
                    log.error("User validation failed: {}", validation.getMessage());
                    throw new PaymentProcessingException("User is not allowed to make payment: " + validation.getMessage());
                }
                if (!validation.isCanMakePayments()) {
                    log.error("User cannot make payments. Reason: {}", validation.getPaymentBlockReason());
                    throw new PaymentProcessingException("User payment authorization is disabled: " + validation.getPaymentBlockReason());
                }
                log.info("User validation successful for user: {}", userId);
            } else {
                throw new PaymentProcessingException("Failed to validate user: User not found: " + userId);
            }
        } catch (PaymentProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error communicating with User Service for verification: ", e);
            throw new PaymentProcessingException("User validation failed due to communication error: " + e.getMessage(), e);
        }
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
        return paymentRepository.save(payment);
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
            if ("PAID".equals(status)) {
                orderServiceClient.markOrderAsPaid(orderId, payment.getId(), payment.getPaymentNumber());
            } else if ("PAYMENT_FAILED".equals(status)) {
                orderServiceClient.markOrderPaymentFailed(orderId, payment.getFailureReason() != null ? payment.getFailureReason() : "Payment failed");
            } else {
                org.de013.paymentservice.dto.external.OrderStatusUpdateRequest request = org.de013.paymentservice.dto.external.OrderStatusUpdateRequest.builder()
                        .status(status)
                        .reason("Payment status updated to " + status)
                        .build();
                orderServiceClient.updateOrderStatus(orderId, request);
            }
            log.info("Successfully updated order {} status to: {}", orderId, status);
        } catch (Exception e) {
            log.warn("Failed to update order status for order: {}", orderId, e);
        }
    }

    private void updatePaymentFromStripeResponse(Payment payment, StripePaymentResponse stripeResponse) {
        log.debug("Updating payment {} from Stripe response", payment.getId());

        // Map Stripe status to our PaymentStatus
        PaymentStatus newStatus = mapStripeStatusToPaymentStatus(stripeResponse.getStatus());
        payment.setStatus(newStatus);

        // Update other fields from Stripe response
        if (stripeResponse.getClientSecret() != null) {
            // Note: We don't store client secret for security reasons
        }

        if (stripeResponse.getFailureMessage() != null) {
            payment.setFailureReason(stripeResponse.getFailureMessage());
        }

        // Update timestamps
        payment.setUpdatedAt(LocalDateTime.now());

        log.info("Payment {} status updated to: {}", payment.getPaymentNumber(), newStatus);
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
            transaction.setStripeResponse(stripeResponse.toString());
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
                    org.de013.common.dto.ApiResponse<org.de013.paymentservice.dto.external.UserDto> apiResponse = userServiceClient.getUserById(payment.getUserId()).getBody();
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
