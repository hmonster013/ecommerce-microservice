package org.de013.paymentservice.service;

import org.de013.paymentservice.dto.refund.RefundRequest;
import org.de013.paymentservice.dto.refund.RefundResponse;
import org.de013.paymentservice.entity.Refund;
import org.de013.paymentservice.entity.enums.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for refund operations
 */
public interface RefundService {

    // ========== REFUND PROCESSING ==========

    /**
     * Create a new refund
     */
    RefundResponse createRefund(RefundRequest request);

    /**
     * Process a pending refund
     */
    RefundResponse processRefund(Long refundId);

    /**
     * Cancel a refund
     */
    RefundResponse cancelRefund(Long refundId, String reason);

    /**
     * Approve a refund (for manual approval workflow)
     */
    RefundResponse approveRefund(Long refundId, String approvedBy);

    /**
     * Reject a refund (for manual approval workflow)
     */
    RefundResponse rejectRefund(Long refundId, String rejectedBy, String reason);

    // ========== REFUND RETRIEVAL ==========

    /**
     * Get refund by ID
     */
    Optional<RefundResponse> getRefundById(Long refundId);

    /**
     * Get refund by refund number
     */
    Optional<RefundResponse> getRefundByNumber(String refundNumber);

    /**
     * Get refund by Stripe refund ID
     */
    Optional<RefundResponse> getRefundByStripeRefundId(String stripeRefundId);

    /**
     * Get refunds by payment ID
     */
    List<RefundResponse> getRefundsByPaymentId(Long paymentId);

    /**
     * Get refunds by order ID
     */
    List<RefundResponse> getRefundsByOrderId(Long orderId);

    /**
     * Get refunds by payment ID with pagination
     */
    Page<RefundResponse> getRefundsByPaymentId(Long paymentId, Pageable pageable);

    // ========== REFUND STATUS ==========

    /**
     * Update refund status
     */
    RefundResponse updateRefundStatus(Long refundId, RefundStatus status, String reason);

    /**
     * Sync refund status with Stripe
     */
    RefundResponse syncRefundStatusWithStripe(Long refundId);

    /**
     * Get refunds by status
     */
    List<RefundResponse> getRefundsByStatus(RefundStatus status);

    /**
     * Get pending refunds for processing
     */
    List<RefundResponse> getPendingRefunds();

    /**
     * Get refunds requiring approval
     */
    List<RefundResponse> getRefundsRequiringApproval();

    // ========== REFUND VALIDATION ==========

    /**
     * Validate refund request
     */
    void validateRefundRequest(RefundRequest request);

    /**
     * Check if payment can be refunded
     */
    boolean canRefundPayment(Long paymentId);

    /**
     * Check if refund amount is valid
     */
    boolean isValidRefundAmount(Long paymentId, BigDecimal refundAmount);

    /**
     * Get maximum refundable amount for payment
     */
    BigDecimal getMaxRefundableAmount(Long paymentId);

    /**
     * Get total refunded amount for payment
     */
    BigDecimal getTotalRefundedAmount(Long paymentId);

    // ========== REFUND SEARCH ==========

    /**
     * Search refunds with criteria
     */
    Page<RefundResponse> searchRefunds(
            String refundNumber,
            Long paymentId,
            Long orderId,
            RefundStatus status,
            String refundType,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String initiatedBy,
            Pageable pageable
    );

    /**
     * Get successful refunds by payment ID
     */
    List<RefundResponse> getSuccessfulRefundsByPaymentId(Long paymentId);

    /**
     * Get failed refunds by payment ID
     */
    List<RefundResponse> getFailedRefundsByPaymentId(Long paymentId);

    /**
     * Get refunds by date range
     */
    List<RefundResponse> getRefundsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    // ========== REFUND STATISTICS ==========

    /**
     * Get refund statistics by payment ID
     */
    RefundStatistics getRefundStatisticsByPaymentId(Long paymentId);

    /**
     * Get refund statistics by date range
     */
    RefundStatistics getRefundStatisticsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Get refund count by status
     */
    Long getRefundCountByStatus(RefundStatus status);

    /**
     * Get total refunded amount by date range
     */
    BigDecimal getTotalRefundedAmountByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    // ========== UTILITY METHODS ==========

    /**
     * Generate unique refund number
     */
    String generateRefundNumber();

    /**
     * Check if refund number exists
     */
    boolean refundNumberExists(String refundNumber);

    /**
     * Get refund entity by ID (for internal use)
     */
    Optional<Refund> getRefundEntityById(Long refundId);

    /**
     * Save refund entity (for internal use)
     */
    Refund saveRefundEntity(Refund refund);

    // ========== CLEANUP OPERATIONS ==========

    /**
     * Clean up old failed refunds
     */
    void cleanupOldFailedRefunds(LocalDateTime cutoffDate);

    /**
     * Clean up old canceled refunds
     */
    void cleanupOldCanceledRefunds(LocalDateTime cutoffDate);

    // ========== REFUND STATISTICS DTO ==========

    /**
     * Refund statistics data transfer object
     */
    record RefundStatistics(
            Long totalRefunds,
            Long successfulRefunds,
            Long failedRefunds,
            Long pendingRefunds,
            Long fullRefunds,
            Long partialRefunds,
            BigDecimal totalRefundedAmount,
            BigDecimal averageRefundAmount,
            LocalDateTime firstRefundDate,
            LocalDateTime lastRefundDate
    ) {}
}
