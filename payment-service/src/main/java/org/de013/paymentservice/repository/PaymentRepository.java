package org.de013.paymentservice.repository;

import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.PaymentStatus;
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
 * Repository interface for Payment entity
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // ========== BASIC QUERIES ==========

    /**
     * Find payment by payment number
     */
    Optional<Payment> findByPaymentNumber(String paymentNumber);

    /**
     * Find payments by order ID
     */
    List<Payment> findByOrderId(Long orderId);

    /**
     * Find payments by order ID with pagination
     */
    Page<Payment> findByOrderId(Long orderId, Pageable pageable);

    /**
     * Find payments by user ID
     */
    List<Payment> findByUserId(Long userId);

    /**
     * Find payments by user ID with pagination
     */
    Page<Payment> findByUserId(Long userId, Pageable pageable);

    /**
     * Find payments by status
     */
    List<Payment> findByStatus(PaymentStatus status);

    /**
     * Find payments by status with pagination
     */
    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    // ========== STRIPE-SPECIFIC QUERIES ==========

    /**
     * Find payment by Stripe payment intent ID
     */
    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Find payment by Stripe customer ID
     */
    List<Payment> findByStripeCustomerId(String stripeCustomerId);

    /**
     * Find payments by Stripe customer ID with pagination
     */
    Page<Payment> findByStripeCustomerId(String stripeCustomerId, Pageable pageable);

    /**
     * Find payment by Stripe payment method ID
     */
    List<Payment> findByStripePaymentMethodId(String stripePaymentMethodId);

    // ========== COMBINED QUERIES ==========

    /**
     * Find payments by user ID and status
     */
    List<Payment> findByUserIdAndStatus(Long userId, PaymentStatus status);

    /**
     * Find payments by user ID and status with pagination
     */
    Page<Payment> findByUserIdAndStatus(Long userId, PaymentStatus status, Pageable pageable);

    /**
     * Find payments by order ID and status
     */
    List<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    /**
     * Find payments by user ID and status list
     */
    List<Payment> findByUserIdAndStatusIn(Long userId, List<PaymentStatus> statuses);

    /**
     * Find payments by order ID and status list
     */
    List<Payment> findByOrderIdAndStatusIn(Long orderId, List<PaymentStatus> statuses);

    // ========== DATE RANGE QUERIES ==========

    /**
     * Find payments created between dates
     */
    List<Payment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find payments created between dates with pagination
     */
    Page<Payment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find payments by user ID and date range
     */
    List<Payment> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find payments by user ID and date range with pagination
     */
    Page<Payment> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // ========== AMOUNT QUERIES ==========

    /**
     * Find payments by amount range
     */
    List<Payment> findByAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);

    /**
     * Find payments by user ID and amount range
     */
    List<Payment> findByUserIdAndAmountBetween(Long userId, BigDecimal minAmount, BigDecimal maxAmount);

    // ========== CUSTOM QUERIES ==========

    /**
     * Find successful payments by user ID
     */
    @Query("SELECT p FROM Payment p WHERE p.userId = :userId AND p.status = 'SUCCEEDED' ORDER BY p.createdAt DESC")
    List<Payment> findSuccessfulPaymentsByUserId(@Param("userId") Long userId);

    /**
     * Find successful payments by user ID with pagination
     */
    @Query("SELECT p FROM Payment p WHERE p.userId = :userId AND p.status = 'SUCCEEDED' ORDER BY p.createdAt DESC")
    Page<Payment> findSuccessfulPaymentsByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Find failed payments by user ID
     */
    @Query("SELECT p FROM Payment p WHERE p.userId = :userId AND p.status = 'FAILED' ORDER BY p.createdAt DESC")
    List<Payment> findFailedPaymentsByUserId(@Param("userId") Long userId);

    /**
     * Find pending payments by user ID
     */
    @Query("SELECT p FROM Payment p WHERE p.userId = :userId AND p.status IN ('PENDING', 'PROCESSING', 'REQUIRES_ACTION', 'REQUIRES_CONFIRMATION') ORDER BY p.createdAt DESC")
    List<Payment> findPendingPaymentsByUserId(@Param("userId") Long userId);

    /**
     * Find payments requiring action
     */
    @Query("SELECT p FROM Payment p WHERE p.status IN ('REQUIRES_ACTION', 'REQUIRES_CONFIRMATION', 'REQUIRES_PAYMENT_METHOD') ORDER BY p.createdAt ASC")
    List<Payment> findPaymentsRequiringAction();

    /**
     * Find payments by search criteria
     */
    @Query("SELECT p FROM Payment p WHERE " +
           "(:paymentNumber IS NULL OR p.paymentNumber LIKE %:paymentNumber%) AND " +
           "(:userId IS NULL OR p.userId = :userId) AND " +
           "(:orderId IS NULL OR p.orderId = :orderId) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:minAmount IS NULL OR p.amount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR p.amount <= :maxAmount) AND " +
           "(:startDate IS NULL OR p.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR p.createdAt <= :endDate) " +
           "ORDER BY p.createdAt DESC")
    Page<Payment> searchPayments(
            @Param("paymentNumber") String paymentNumber,
            @Param("userId") Long userId,
            @Param("orderId") Long orderId,
            @Param("status") PaymentStatus status,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    // ========== STATISTICS QUERIES ==========

    /**
     * Count payments by status
     */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status")
    Long countByStatus(@Param("status") PaymentStatus status);

    /**
     * Count payments by user ID and status
     */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.userId = :userId AND p.status = :status")
    Long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") PaymentStatus status);

    /**
     * Sum successful payments by user ID
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.userId = :userId AND p.status = 'SUCCEEDED'")
    BigDecimal sumSuccessfulPaymentsByUserId(@Param("userId") Long userId);

    /**
     * Sum successful payments by date range
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal sumSuccessfulPaymentsByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Get payment statistics by user ID
     */
    @Query("SELECT " +
           "COUNT(p) as totalPayments, " +
           "COALESCE(SUM(CASE WHEN p.status = 'SUCCEEDED' THEN p.amount ELSE 0 END), 0) as totalSuccessfulAmount, " +
           "COUNT(CASE WHEN p.status = 'SUCCEEDED' THEN 1 END) as successfulPayments, " +
           "COUNT(CASE WHEN p.status = 'FAILED' THEN 1 END) as failedPayments " +
           "FROM Payment p WHERE p.userId = :userId")
    Object[] getPaymentStatisticsByUserId(@Param("userId") Long userId);

    // ========== EXISTENCE CHECKS ==========

    /**
     * Check if payment exists by payment number
     */
    boolean existsByPaymentNumber(String paymentNumber);

    /**
     * Check if payment exists by Stripe payment intent ID
     */
    boolean existsByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Check if user has any successful payments
     */
    @Query("SELECT COUNT(p) > 0 FROM Payment p WHERE p.userId = :userId AND p.status = 'SUCCEEDED'")
    boolean hasSuccessfulPayments(@Param("userId") Long userId);

    // ========== CLEANUP QUERIES ==========

    /**
     * Find old failed payments for cleanup
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'FAILED' AND p.createdAt < :cutoffDate")
    List<Payment> findOldFailedPayments(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find old canceled payments for cleanup
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'CANCELED' AND p.createdAt < :cutoffDate")
    List<Payment> findOldCanceledPayments(@Param("cutoffDate") LocalDateTime cutoffDate);
}
