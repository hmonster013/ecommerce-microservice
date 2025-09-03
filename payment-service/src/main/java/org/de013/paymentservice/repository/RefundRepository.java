package org.de013.paymentservice.repository;

import org.de013.paymentservice.entity.Refund;
import org.de013.paymentservice.entity.enums.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Refund entity
 */
@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    // ========== BASIC QUERIES ==========

    /**
     * Find refund by refund number
     */
    Optional<Refund> findByRefundNumber(String refundNumber);

    /**
     * Find refunds by payment ID
     */
    List<Refund> findByPaymentId(Long paymentId);

    /**
     * Find refunds by payment ID with pagination
     */
    Page<Refund> findByPaymentId(Long paymentId, Pageable pageable);

    /**
     * Find refunds by payment ID ordered by creation date
     */
    List<Refund> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);

    /**
     * Find refunds by order ID
     */
    List<Refund> findByOrderId(Long orderId);

    /**
     * Find refunds by order ID with pagination
     */
    Page<Refund> findByOrderId(Long orderId, Pageable pageable);

    /**
     * Find refunds by status
     */
    List<Refund> findByStatus(RefundStatus status);

    /**
     * Find refunds by status with pagination
     */
    Page<Refund> findByStatus(RefundStatus status, Pageable pageable);

    // ========== STRIPE-SPECIFIC QUERIES ==========

    /**
     * Find refund by Stripe refund ID
     */
    Optional<Refund> findByStripeRefundId(String stripeRefundId);

    /**
     * Find refunds by Stripe charge ID
     */
    List<Refund> findByStripeChargeId(String stripeChargeId);

    /**
     * Find refunds by Stripe payment intent ID
     */
    List<Refund> findByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Find refund by payment ID and Stripe refund ID
     */
    Optional<Refund> findByPaymentIdAndStripeRefundId(Long paymentId, String stripeRefundId);

    // ========== STATUS-BASED QUERIES ==========

    /**
     * Find successful refunds by payment ID
     */
    @Query("SELECT r FROM Refund r WHERE r.paymentId = :paymentId AND r.status = 'SUCCEEDED' ORDER BY r.createdAt DESC")
    List<Refund> findSuccessfulRefundsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Find pending refunds by payment ID
     */
    @Query("SELECT r FROM Refund r WHERE r.paymentId = :paymentId AND r.status = 'PENDING' ORDER BY r.createdAt DESC")
    List<Refund> findPendingRefundsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Find failed refunds by payment ID
     */
    @Query("SELECT r FROM Refund r WHERE r.paymentId = :paymentId AND r.status = 'FAILED' ORDER BY r.createdAt DESC")
    List<Refund> findFailedRefundsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Find pending refunds for processing
     */
    @Query("SELECT r FROM Refund r WHERE r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<Refund> findPendingRefunds();

    /**
     * Find refunds requiring action
     */
    @Query("SELECT r FROM Refund r WHERE r.status = 'REQUIRES_ACTION' ORDER BY r.createdAt ASC")
    List<Refund> findRefundsRequiringAction();

    // ========== REFUND TYPE QUERIES ==========

    /**
     * Find full refunds by payment ID
     */
    @Query("SELECT r FROM Refund r WHERE r.paymentId = :paymentId AND r.refundType = 'FULL' ORDER BY r.createdAt DESC")
    List<Refund> findFullRefundsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Find partial refunds by payment ID
     */
    @Query("SELECT r FROM Refund r WHERE r.paymentId = :paymentId AND r.refundType = 'PARTIAL' ORDER BY r.createdAt DESC")
    List<Refund> findPartialRefundsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Find latest refund by payment ID
     */
    @Query("SELECT r FROM Refund r WHERE r.paymentId = :paymentId ORDER BY r.createdAt DESC LIMIT 1")
    Optional<Refund> findLatestRefundByPaymentId(@Param("paymentId") Long paymentId);

    // ========== DATE RANGE QUERIES ==========

    /**
     * Find refunds by date range
     */
    List<Refund> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find refunds by date range with pagination
     */
    Page<Refund> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find refunds by payment ID and date range
     */
    @Query("SELECT r FROM Refund r WHERE r.paymentId = :paymentId AND r.createdAt BETWEEN :startDate AND :endDate ORDER BY r.createdAt DESC")
    List<Refund> findByPaymentIdAndDateRange(@Param("paymentId") Long paymentId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find successful refunds by date range
     */
    @Query("SELECT r FROM Refund r WHERE r.status = 'SUCCEEDED' AND r.createdAt BETWEEN :startDate AND :endDate ORDER BY r.createdAt DESC")
    List<Refund> findSuccessfulRefundsByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // ========== AMOUNT QUERIES ==========

    /**
     * Find refunds by amount range
     */
    List<Refund> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);

    /**
     * Find refunds by payment ID and amount range
     */
    @Query("SELECT r FROM Refund r WHERE r.paymentId = :paymentId AND r.amount BETWEEN :minAmount AND :maxAmount ORDER BY r.createdAt DESC")
    List<Refund> findByPaymentIdAndAmountRange(@Param("paymentId") Long paymentId, @Param("minAmount") BigDecimal minAmount, @Param("maxAmount") BigDecimal maxAmount);

    // ========== SEARCH AND FILTERING ==========

    /**
     * Search refunds by criteria
     */
    @Query("SELECT r FROM Refund r WHERE " +
           "(:refundNumber IS NULL OR r.refundNumber LIKE %:refundNumber%) AND " +
           "(:paymentId IS NULL OR r.paymentId = :paymentId) AND " +
           "(:orderId IS NULL OR r.orderId = :orderId) AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:refundType IS NULL OR r.refundType = :refundType) AND " +
           "(:minAmount IS NULL OR r.amount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR r.amount <= :maxAmount) AND " +
           "(:startDate IS NULL OR r.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR r.createdAt <= :endDate) AND " +
           "(:initiatedBy IS NULL OR r.initiatedBy = :initiatedBy) " +
           "ORDER BY r.createdAt DESC")
    Page<Refund> searchRefunds(
            @Param("refundNumber") String refundNumber,
            @Param("paymentId") Long paymentId,
            @Param("orderId") Long orderId,
            @Param("status") RefundStatus status,
            @Param("refundType") String refundType,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("initiatedBy") String initiatedBy,
            Pageable pageable
    );

    // ========== STATISTICS QUERIES ==========

    /**
     * Count refunds by payment ID
     */
    @Query("SELECT COUNT(r) FROM Refund r WHERE r.paymentId = :paymentId")
    Long countByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Count refunds by payment ID and status
     */
    @Query("SELECT COUNT(r) FROM Refund r WHERE r.paymentId = :paymentId AND r.status = :status")
    Long countByPaymentIdAndStatus(@Param("paymentId") Long paymentId, @Param("status") RefundStatus status);

    /**
     * Count refunds by status
     */
    @Query("SELECT COUNT(r) FROM Refund r WHERE r.status = :status")
    Long countByStatus(@Param("status") RefundStatus status);

    /**
     * Sum successful refunds by payment ID
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.paymentId = :paymentId AND r.status = 'SUCCEEDED'")
    BigDecimal sumSuccessfulRefundsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Sum successful refunds by date range
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.status = 'SUCCEEDED' AND r.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumSuccessfulRefundsByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Get refund statistics by payment ID
     */
    @Query("SELECT " +
           "COUNT(r) as totalRefunds, " +
           "COUNT(CASE WHEN r.status = 'SUCCEEDED' THEN 1 END) as successfulRefunds, " +
           "COUNT(CASE WHEN r.status = 'FAILED' THEN 1 END) as failedRefunds, " +
           "COUNT(CASE WHEN r.status = 'PENDING' THEN 1 END) as pendingRefunds, " +
           "COUNT(CASE WHEN r.refundType = 'FULL' THEN 1 END) as fullRefunds, " +
           "COUNT(CASE WHEN r.refundType = 'PARTIAL' THEN 1 END) as partialRefunds, " +
           "COALESCE(SUM(CASE WHEN r.status = 'SUCCEEDED' THEN r.amount ELSE 0 END), 0) as totalRefundedAmount " +
           "FROM Refund r WHERE r.paymentId = :paymentId")
    Object[] getRefundStatisticsByPaymentId(@Param("paymentId") Long paymentId);

    // ========== APPROVAL WORKFLOW QUERIES ==========

    /**
     * Find refunds pending approval
     */
    @Query("SELECT r FROM Refund r WHERE r.approvedBy IS NULL AND r.status = 'PENDING' ORDER BY r.createdAt ASC")
    List<Refund> findRefundsPendingApproval();

    /**
     * Find refunds approved by user
     */
    @Query("SELECT r FROM Refund r WHERE r.approvedBy = :approvedBy ORDER BY r.approvedAt DESC")
    List<Refund> findRefundsApprovedBy(@Param("approvedBy") String approvedBy);

    /**
     * Find refunds initiated by user
     */
    @Query("SELECT r FROM Refund r WHERE r.initiatedBy = :initiatedBy ORDER BY r.createdAt DESC")
    List<Refund> findRefundsInitiatedBy(@Param("initiatedBy") String initiatedBy);

    // ========== PROCESSING STATUS QUERIES ==========

    /**
     * Find processed refunds by date range
     */
    @Query("SELECT r FROM Refund r WHERE r.processedAt IS NOT NULL AND r.processedAt BETWEEN :startDate AND :endDate ORDER BY r.processedAt DESC")
    List<Refund> findProcessedRefundsByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find settled refunds by date range
     */
    @Query("SELECT r FROM Refund r WHERE r.settledAt IS NOT NULL AND r.settledAt BETWEEN :startDate AND :endDate ORDER BY r.settledAt DESC")
    List<Refund> findSettledRefundsByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find unsettled successful refunds
     */
    @Query("SELECT r FROM Refund r WHERE r.status = 'SUCCEEDED' AND r.settledAt IS NULL ORDER BY r.processedAt ASC")
    List<Refund> findUnsettledSuccessfulRefunds();

    /**
     * Find refunds with expected arrival date in range
     */
    @Query("SELECT r FROM Refund r WHERE r.expectedArrivalDate BETWEEN :startDate AND :endDate ORDER BY r.expectedArrivalDate ASC")
    List<Refund> findRefundsByExpectedArrivalDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // ========== EXISTENCE CHECKS ==========

    /**
     * Check if refund exists by refund number
     */
    boolean existsByRefundNumber(String refundNumber);

    /**
     * Check if refund exists by Stripe refund ID
     */
    boolean existsByStripeRefundId(String stripeRefundId);

    /**
     * Check if payment has any successful refunds
     */
    @Query("SELECT COUNT(r) > 0 FROM Refund r WHERE r.paymentId = :paymentId AND r.status = 'SUCCEEDED'")
    boolean hasSuccessfulRefunds(@Param("paymentId") Long paymentId);

    /**
     * Check if payment has full refund
     */
    @Query("SELECT COUNT(r) > 0 FROM Refund r WHERE r.paymentId = :paymentId AND r.refundType = 'FULL' AND r.status = 'SUCCEEDED'")
    boolean hasFullRefund(@Param("paymentId") Long paymentId);

    // ========== CLEANUP QUERIES ==========

    /**
     * Find old failed refunds for cleanup
     */
    @Query("SELECT r FROM Refund r WHERE r.status = 'FAILED' AND r.createdAt < :cutoffDate")
    List<Refund> findOldFailedRefunds(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find old canceled refunds for cleanup
     */
    @Query("SELECT r FROM Refund r WHERE r.status = 'CANCELED' AND r.createdAt < :cutoffDate")
    List<Refund> findOldCanceledRefunds(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find duplicate refunds by Stripe refund ID
     */
    @Query("SELECT r FROM Refund r WHERE r.stripeRefundId IN " +
           "(SELECT r2.stripeRefundId FROM Refund r2 WHERE r2.stripeRefundId IS NOT NULL GROUP BY r2.stripeRefundId HAVING COUNT(r2.stripeRefundId) > 1)")
    List<Refund> findDuplicateRefundsByStripeRefundId();
}
