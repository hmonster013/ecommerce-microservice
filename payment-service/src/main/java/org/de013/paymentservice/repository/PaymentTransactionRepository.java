package org.de013.paymentservice.repository;

import org.de013.paymentservice.entity.PaymentTransaction;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.entity.enums.TransactionType;
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
 * Repository interface for PaymentTransaction entity
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    // ========== BASIC QUERIES ==========

    /**
     * Find transactions by payment ID
     */
    List<PaymentTransaction> findByPaymentId(Long paymentId);

    /**
     * Find transactions by payment ID with pagination
     */
    Page<PaymentTransaction> findByPaymentId(Long paymentId, Pageable pageable);

    /**
     * Find transactions by payment ID ordered by creation date
     */
    List<PaymentTransaction> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);

    /**
     * Find transactions by type
     */
    List<PaymentTransaction> findByType(TransactionType type);

    /**
     * Find transactions by status
     */
    List<PaymentTransaction> findByStatus(PaymentStatus status);

    /**
     * Find transactions by type and status
     */
    List<PaymentTransaction> findByTypeAndStatus(TransactionType type, PaymentStatus status);

    // ========== STRIPE-SPECIFIC QUERIES ==========

    /**
     * Find transaction by Stripe charge ID
     */
    Optional<PaymentTransaction> findByStripeChargeId(String stripeChargeId);

    /**
     * Find transactions by Stripe payment intent ID
     */
    List<PaymentTransaction> findByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Find transactions by Stripe transfer group
     */
    List<PaymentTransaction> findByStripeTransferGroup(String stripeTransferGroup);

    /**
     * Find transactions by payment ID and Stripe charge ID
     */
    Optional<PaymentTransaction> findByPaymentIdAndStripeChargeId(Long paymentId, String stripeChargeId);

    // ========== TRANSACTION TYPE QUERIES ==========

    /**
     * Find charge transactions by payment ID
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.type = 'CHARGE' ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findChargeTransactionsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Find refund transactions by payment ID
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.type IN ('REFUND', 'PARTIAL_REFUND') ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findRefundTransactionsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Find authorization transactions by payment ID
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.type = 'AUTHORIZATION' ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findAuthorizationTransactionsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Find capture transactions by payment ID
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.type = 'CAPTURE' ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findCaptureTransactionsByPaymentId(@Param("paymentId") Long paymentId);

    // ========== SUCCESSFUL TRANSACTIONS ==========

    /**
     * Find successful transactions by payment ID
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.status = 'SUCCEEDED' ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findSuccessfulTransactionsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Find latest successful charge transaction by payment ID
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.type = 'CHARGE' AND pt.status = 'SUCCEEDED' ORDER BY pt.createdAt DESC LIMIT 1")
    Optional<PaymentTransaction> findLatestSuccessfulChargeByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Find latest transaction by payment ID
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId ORDER BY pt.createdAt DESC LIMIT 1")
    Optional<PaymentTransaction> findLatestTransactionByPaymentId(@Param("paymentId") Long paymentId);

    // ========== FAILED TRANSACTIONS ==========

    /**
     * Find failed transactions by payment ID
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.status = 'FAILED' ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findFailedTransactionsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Find failed transactions for retry
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'FAILED' AND pt.type IN ('CHARGE', 'CAPTURE') AND pt.createdAt > :cutoffDate ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findFailedTransactionsForRetry(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========== DATE RANGE QUERIES ==========

    /**
     * Find transactions by payment ID and date range
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.createdAt BETWEEN :startDate AND :endDate ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findByPaymentIdAndDateRange(@Param("paymentId") Long paymentId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find transactions by date range
     */
    List<PaymentTransaction> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find transactions by date range with pagination
     */
    Page<PaymentTransaction> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find successful transactions by date range
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'SUCCEEDED' AND pt.createdAt BETWEEN :startDate AND :endDate ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findSuccessfulTransactionsByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // ========== AMOUNT QUERIES ==========

    /**
     * Find transactions by amount range
     */
    List<PaymentTransaction> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);

    /**
     * Find transactions by payment ID and amount range
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.amount BETWEEN :minAmount AND :maxAmount ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findByPaymentIdAndAmountRange(@Param("paymentId") Long paymentId, @Param("minAmount") BigDecimal minAmount, @Param("maxAmount") BigDecimal maxAmount);

    // ========== SEARCH AND FILTERING ==========

    /**
     * Search transactions by criteria
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE " +
           "(:paymentId IS NULL OR pt.paymentId = :paymentId) AND " +
           "(:type IS NULL OR pt.type = :type) AND " +
           "(:status IS NULL OR pt.status = :status) AND " +
           "(:minAmount IS NULL OR pt.amount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR pt.amount <= :maxAmount) AND " +
           "(:startDate IS NULL OR pt.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR pt.createdAt <= :endDate) AND " +
           "(:stripeChargeId IS NULL OR pt.stripeChargeId = :stripeChargeId) " +
           "ORDER BY pt.createdAt DESC")
    Page<PaymentTransaction> searchTransactions(
            @Param("paymentId") Long paymentId,
            @Param("type") TransactionType type,
            @Param("status") PaymentStatus status,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("stripeChargeId") String stripeChargeId,
            Pageable pageable
    );

    // ========== STATISTICS QUERIES ==========

    /**
     * Count transactions by payment ID
     */
    @Query("SELECT COUNT(pt) FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId")
    Long countByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Count transactions by payment ID and status
     */
    @Query("SELECT COUNT(pt) FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.status = :status")
    Long countByPaymentIdAndStatus(@Param("paymentId") Long paymentId, @Param("status") PaymentStatus status);

    /**
     * Count transactions by type and status
     */
    @Query("SELECT COUNT(pt) FROM PaymentTransaction pt WHERE pt.type = :type AND pt.status = :status")
    Long countByTypeAndStatus(@Param("type") TransactionType type, @Param("status") PaymentStatus status);

    /**
     * Sum successful transaction amounts by payment ID
     */
    @Query("SELECT COALESCE(SUM(pt.amount), 0) FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.status = 'SUCCEEDED'")
    BigDecimal sumSuccessfulTransactionsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Sum successful charge amounts by payment ID
     */
    @Query("SELECT COALESCE(SUM(pt.amount), 0) FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.type = 'CHARGE' AND pt.status = 'SUCCEEDED'")
    BigDecimal sumSuccessfulChargesByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Sum successful refund amounts by payment ID
     */
    @Query("SELECT COALESCE(SUM(pt.amount), 0) FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.type IN ('REFUND', 'PARTIAL_REFUND') AND pt.status = 'SUCCEEDED'")
    BigDecimal sumSuccessfulRefundsByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * Get transaction statistics by payment ID
     */
    @Query("SELECT " +
           "COUNT(pt) as totalTransactions, " +
           "COUNT(CASE WHEN pt.status = 'SUCCEEDED' THEN 1 END) as successfulTransactions, " +
           "COUNT(CASE WHEN pt.status = 'FAILED' THEN 1 END) as failedTransactions, " +
           "COUNT(CASE WHEN pt.type = 'CHARGE' THEN 1 END) as chargeTransactions, " +
           "COUNT(CASE WHEN pt.type IN ('REFUND', 'PARTIAL_REFUND') THEN 1 END) as refundTransactions, " +
           "COALESCE(SUM(CASE WHEN pt.status = 'SUCCEEDED' THEN pt.amount ELSE 0 END), 0) as totalSuccessfulAmount " +
           "FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId")
    Object[] getTransactionStatisticsByPaymentId(@Param("paymentId") Long paymentId);

    // ========== PROCESSING STATUS QUERIES ==========

    /**
     * Find pending transactions
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status IN ('PENDING', 'PROCESSING') ORDER BY pt.createdAt ASC")
    List<PaymentTransaction> findPendingTransactions();

    /**
     * Find transactions requiring action
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status IN ('REQUIRES_ACTION', 'REQUIRES_CONFIRMATION') ORDER BY pt.createdAt ASC")
    List<PaymentTransaction> findTransactionsRequiringAction();

    /**
     * Find settled transactions by date range
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.settledAt IS NOT NULL AND pt.settledAt BETWEEN :startDate AND :endDate ORDER BY pt.settledAt DESC")
    List<PaymentTransaction> findSettledTransactionsByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Find unsettled successful transactions
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'SUCCEEDED' AND pt.settledAt IS NULL ORDER BY pt.processedAt ASC")
    List<PaymentTransaction> findUnsettledSuccessfulTransactions();

    // ========== EXISTENCE CHECKS ==========

    /**
     * Check if transaction exists by Stripe charge ID
     */
    boolean existsByStripeChargeId(String stripeChargeId);

    /**
     * Check if payment has successful charge transaction
     */
    @Query("SELECT COUNT(pt) > 0 FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.type = 'CHARGE' AND pt.status = 'SUCCEEDED'")
    boolean hasSuccessfulCharge(@Param("paymentId") Long paymentId);

    /**
     * Check if payment has any refund transactions
     */
    @Query("SELECT COUNT(pt) > 0 FROM PaymentTransaction pt WHERE pt.paymentId = :paymentId AND pt.type IN ('REFUND', 'PARTIAL_REFUND')")
    boolean hasRefundTransactions(@Param("paymentId") Long paymentId);

    // ========== CLEANUP QUERIES ==========

    /**
     * Find old failed transactions for cleanup
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.status = 'FAILED' AND pt.createdAt < :cutoffDate")
    List<PaymentTransaction> findOldFailedTransactions(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find duplicate transactions by Stripe charge ID
     */
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.stripeChargeId IN " +
           "(SELECT pt2.stripeChargeId FROM PaymentTransaction pt2 WHERE pt2.stripeChargeId IS NOT NULL GROUP BY pt2.stripeChargeId HAVING COUNT(pt2.stripeChargeId) > 1)")
    List<PaymentTransaction> findDuplicateTransactionsByStripeChargeId();
}
