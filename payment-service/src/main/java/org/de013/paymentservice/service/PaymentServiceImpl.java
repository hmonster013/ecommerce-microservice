package org.de013.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.paymentservice.dto.payment.PaymentResponse;
import org.de013.paymentservice.dto.payment.PaymentStatusResponse;

import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.exception.PaymentNotFoundException;
import org.de013.paymentservice.exception.PaymentProcessingException;
import org.de013.paymentservice.gateway.PaymentGatewayFactory;
import org.de013.paymentservice.mapper.PaymentMapper;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.util.PaymentNumberGenerator;
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

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final PaymentMapper paymentMapper;

    // ========== PAYMENT PROCESSING ==========

    @Override
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        log.info("Processing payment for order: {}, user: {}, amount: {}", 
                request.getOrderId(), request.getUserId(), request.getAmount());

        try {
            validatePaymentRequest(request);
            
            Payment payment = createPaymentEntity(request);
            payment = paymentRepository.save(payment);

            // TODO: Process with Stripe gateway
            log.info("Payment processed successfully: {}", payment.getPaymentNumber());
            return paymentMapper.toPaymentResponse(payment);

        } catch (Exception e) {
            log.error("Failed to process payment for order: {}", request.getOrderId(), e);
            throw new PaymentProcessingException("Failed to process payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResponse confirmPayment(Long paymentId, String paymentMethodId) {
        Payment payment = getPaymentEntityById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        // TODO: Implement confirmation logic
        return paymentMapper.toPaymentResponse(payment);
    }

    @Override
    public PaymentResponse cancelPayment(Long paymentId, String reason) {
        Payment payment = getPaymentEntityById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        
        payment.setStatus(PaymentStatus.CANCELED);
        payment.setFailureReason(reason);
        payment = paymentRepository.save(payment);
        
        return paymentMapper.toPaymentResponse(payment);
    }

    @Override
    public PaymentResponse capturePayment(Long paymentId, BigDecimal amount) {
        Payment payment = getPaymentEntityById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        // TODO: Implement capture logic
        return paymentMapper.toPaymentResponse(payment);
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
    public Page<PaymentResponse> getPaymentsByUserId(Long userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable)
                .map(paymentMapper::toPaymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByUserIdAndStatus(Long userId, PaymentStatus status) {
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
        Payment payment = getPaymentEntityById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        // TODO: Implement Stripe sync
        return paymentMapper.toPaymentResponse(payment);
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
    }

    @Override
    public void validatePaymentAmount(Long orderId, BigDecimal amount) {
        // TODO: Validate with Order Service
    }

    @Override
    public void validateUserCanMakePayment(Long userId) {
        // TODO: Validate with User Service
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
            String paymentNumber, Long userId, Long orderId, PaymentStatus status,
            BigDecimal minAmount, BigDecimal maxAmount, LocalDateTime startDate, LocalDateTime endDate,
            Pageable pageable) {

        return paymentRepository.searchPayments(
                paymentNumber, userId, orderId, status, minAmount, maxAmount, startDate, endDate, pageable)
                .map(paymentMapper::toPaymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getSuccessfulPaymentsByUserId(Long userId) {
        return paymentRepository.findSuccessfulPaymentsByUserId(userId).stream()
                .map(paymentMapper::toPaymentResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getFailedPaymentsByUserId(Long userId) {
        return paymentRepository.findFailedPaymentsByUserId(userId).stream()
                .map(paymentMapper::toPaymentResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPendingPaymentsByUserId(Long userId) {
        return paymentRepository.findPendingPaymentsByUserId(userId).stream()
                .map(paymentMapper::toPaymentResponse)
                .toList();
    }

    // ========== PAYMENT STATISTICS ==========

    @Override
    @Transactional(readOnly = true)
    public PaymentStatistics getPaymentStatisticsByUserId(Long userId) {
        // TODO: Implement statistics calculation
        return new PaymentService.PaymentStatistics(
                0L, 0L, 0L, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentStatistics getPaymentStatisticsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        // TODO: Implement statistics calculation
        return new PaymentService.PaymentStatistics(
                0L, 0L, 0L, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalPaymentAmountByUserId(Long userId) {
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

    private void updateOrderStatus(Long orderId, String status) {
        try {
            log.info("Updating order {} status to: {}", orderId, status);
            // TODO: Call order service to update status
        } catch (Exception e) {
            log.warn("Failed to update order status for order: {}", orderId, e);
        }
    }

    private void createPaymentTransaction(Payment payment, Object stripeResponse, Object transactionType) {
        // TODO: Implement payment transaction creation
        log.debug("Creating payment transaction for payment: {}", payment.getId());
    }
}
