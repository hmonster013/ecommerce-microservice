package org.de013.paymentservice.service;

import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.paymentservice.dto.payment.PaymentResponse;
import org.de013.paymentservice.dto.payment.PaymentStatusResponse;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for payment processing operations
 */
public interface PaymentService {

    // ========== PAYMENT PROCESSING ==========

    /**
     * Process a new payment
     */
    PaymentResponse processPayment(ProcessPaymentRequest request);

    /**
     * Confirm a payment that requires action
     */
    PaymentResponse confirmPayment(Long paymentId, String paymentMethodId);

    /**
     * Cancel a payment
     */
    PaymentResponse cancelPayment(Long paymentId, String reason);

    /**
     * Capture an authorized payment
     */
    PaymentResponse capturePayment(Long paymentId, BigDecimal amount);

    // ========== PAYMENT RETRIEVAL ==========

    /**
     * Get payment by ID
     */
    Optional<PaymentResponse> getPaymentById(Long paymentId);

    /**
     * Get payment by payment number
     */
    Optional<PaymentResponse> getPaymentByNumber(String paymentNumber);

    /**
     * Get payment by Stripe payment intent ID
     */
    Optional<PaymentResponse> getPaymentByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Get payments by order ID
     */
    List<PaymentResponse> getPaymentsByOrderId(Long orderId);

    /**
     * Get payments by user ID
     */
    Page<PaymentResponse> getPaymentsByUserId(Long userId, Pageable pageable);

    /**
     * Get payments by user ID and status
     */
    List<PaymentResponse> getPaymentsByUserIdAndStatus(Long userId, PaymentStatus status);

    // ========== PAYMENT STATUS ==========

    /**
     * Get payment status
     */
    PaymentStatusResponse getPaymentStatus(Long paymentId);

    /**
     * Get payment status by payment number
     */
    PaymentStatusResponse getPaymentStatusByNumber(String paymentNumber);

    /**
     * Update payment status
     */
    PaymentResponse updatePaymentStatus(Long paymentId, PaymentStatus status, String reason);

    /**
     * Sync payment status with Stripe
     */
    PaymentResponse syncPaymentStatusWithStripe(Long paymentId);

    // ========== PAYMENT VALIDATION ==========

    /**
     * Validate payment request
     */
    void validatePaymentRequest(ProcessPaymentRequest request);

    /**
     * Validate payment amount against order
     */
    void validatePaymentAmount(Long orderId, BigDecimal amount);

    /**
     * Validate user can make payment
     */
    void validateUserCanMakePayment(Long userId);

    /**
     * Check if payment can be canceled
     */
    boolean canCancelPayment(Long paymentId);

    /**
     * Check if payment can be captured
     */
    boolean canCapturePayment(Long paymentId);

    /**
     * Check if payment can be refunded
     */
    boolean canRefundPayment(Long paymentId);

    // ========== PAYMENT SEARCH ==========

    /**
     * Search payments with criteria
     */
    Page<PaymentResponse> searchPayments(
            String paymentNumber,
            Long userId,
            Long orderId,
            PaymentStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Get successful payments by user
     */
    List<PaymentResponse> getSuccessfulPaymentsByUserId(Long userId);

    /**
     * Get failed payments by user
     */
    List<PaymentResponse> getFailedPaymentsByUserId(Long userId);

    /**
     * Get pending payments by user
     */
    List<PaymentResponse> getPendingPaymentsByUserId(Long userId);

    // ========== PAYMENT STATISTICS ==========

    /**
     * Get payment statistics by user ID
     */
    PaymentStatistics getPaymentStatisticsByUserId(Long userId);

    /**
     * Get payment statistics by date range
     */
    PaymentStatistics getPaymentStatisticsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Get total payment amount by user
     */
    BigDecimal getTotalPaymentAmountByUserId(Long userId);

    /**
     * Get payment count by status
     */
    Long getPaymentCountByStatus(PaymentStatus status);

    // ========== UTILITY METHODS ==========

    /**
     * Generate unique payment number
     */
    String generatePaymentNumber();

    /**
     * Check if payment number exists
     */
    boolean paymentNumberExists(String paymentNumber);

    /**
     * Get payment entity by ID (for internal use)
     */
    Optional<Payment> getPaymentEntityById(Long paymentId);

    /**
     * Save payment entity (for internal use)
     */
    Payment savePaymentEntity(Payment payment);

    // ========== PAYMENT STATISTICS DTO ==========

    /**
     * Payment statistics data transfer object
     */
    record PaymentStatistics(
            Long totalPayments,
            Long successfulPayments,
            Long failedPayments,
            Long pendingPayments,
            BigDecimal totalAmount,
            BigDecimal successfulAmount,
            BigDecimal averageAmount,
            LocalDateTime firstPaymentDate,
            LocalDateTime lastPaymentDate
    ) {}
}
