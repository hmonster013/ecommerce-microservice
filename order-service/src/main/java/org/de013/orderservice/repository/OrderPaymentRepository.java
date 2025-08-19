package org.de013.orderservice.repository;

import org.de013.orderservice.entity.OrderPayment;
import org.de013.orderservice.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Order Payment Repository
 * 
 * JPA repository for OrderPayment entity with payment history and analytics.
 * Provides comprehensive data access methods for payment management.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Repository
public interface OrderPaymentRepository extends JpaRepository<OrderPayment, Long>, JpaSpecificationExecutor<OrderPayment> {
    
    /**
     * Find payments by order ID ordered by created date descending
     * 
     * @param orderId the order ID
     * @return list of payments
     */
    List<OrderPayment> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    
    /**
     * Find payments by order ID with pagination
     * 
     * @param orderId the order ID
     * @param pageable pagination information
     * @return page of payments
     */
    Page<OrderPayment> findByOrderIdOrderByCreatedAtDesc(Long orderId, Pageable pageable);
    
    /**
     * Find latest payment for order
     * 
     * @param orderId the order ID
     * @return optional latest payment
     */
    Optional<OrderPayment> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);
    
    /**
     * Find payment by payment ID
     * 
     * @param paymentId the external payment ID
     * @return optional payment
     */
    Optional<OrderPayment> findByPaymentId(String paymentId);
    
    /**
     * Find payment by transaction ID
     * 
     * @param transactionId the transaction ID
     * @return optional payment
     */
    Optional<OrderPayment> findByTransactionId(String transactionId);
    
    /**
     * Find payments by payment status
     * 
     * @param status the payment status
     * @param pageable pagination information
     * @return page of payments
     */
    Page<OrderPayment> findByStatus(PaymentStatus status, Pageable pageable);
    
    /**
     * Find payments by payment method
     * 
     * @param paymentMethod the payment method
     * @param pageable pagination information
     * @return page of payments
     */
    Page<OrderPayment> findByPaymentMethod(String paymentMethod, Pageable pageable);
    
    /**
     * Find payments by payment gateway
     * 
     * @param paymentGateway the payment gateway
     * @param pageable pagination information
     * @return page of payments
     */
    Page<OrderPayment> findByPaymentGateway(String paymentGateway, Pageable pageable);
    
    /**
     * Find payments by date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination information
     * @return page of payments
     */
    Page<OrderPayment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find payments processed between dates
     * 
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination information
     * @return page of payments
     */
    Page<OrderPayment> findByProcessedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find payments by multiple statuses
     * 
     * @param statuses list of payment statuses
     * @param pageable pagination information
     * @return page of payments
     */
    Page<OrderPayment> findByStatusIn(List<PaymentStatus> statuses, Pageable pageable);
    
    /**
     * Find payments by risk score range
     * 
     * @param minRiskScore minimum risk score
     * @param maxRiskScore maximum risk score
     * @param pageable pagination information
     * @return page of payments
     */
    Page<OrderPayment> findByRiskScoreBetween(Integer minRiskScore, Integer maxRiskScore, Pageable pageable);
    
    /**
     * Find high risk payments
     * 
     * @param riskThreshold risk score threshold
     * @param pageable pagination information
     * @return page of high risk payments
     */
    Page<OrderPayment> findByRiskScoreGreaterThanEqual(Integer riskThreshold, Pageable pageable);
    
    /**
     * Find test payments
     * 
     * @param pageable pagination information
     * @return page of test payments
     */
    Page<OrderPayment> findByIsTestTrue(Pageable pageable);
    
    /**
     * Find production payments
     * 
     * @param pageable pagination information
     * @return page of production payments
     */
    Page<OrderPayment> findByIsTestFalse(Pageable pageable);
    
    /**
     * Find payments by attempt number
     * 
     * @param attemptNumber the attempt number
     * @param pageable pagination information
     * @return page of payments
     */
    Page<OrderPayment> findByAttemptNumber(Integer attemptNumber, Pageable pageable);
    
    /**
     * Find payments with multiple attempts
     * 
     * @param minAttempts minimum number of attempts
     * @param pageable pagination information
     * @return page of payments with multiple attempts
     */
    Page<OrderPayment> findByAttemptNumberGreaterThan(Integer minAttempts, Pageable pageable);
    
    /**
     * Find expired payments
     * 
     * @param currentTime current timestamp
     * @param pageable pagination information
     * @return page of expired payments
     */
    Page<OrderPayment> findByExpiresAtBeforeAndStatusIn(LocalDateTime currentTime, List<PaymentStatus> statuses, Pageable pageable);
    
    /**
     * Count payments by order ID
     * 
     * @param orderId the order ID
     * @return count of payments
     */
    long countByOrderId(Long orderId);
    
    /**
     * Count payments by status
     * 
     * @param status the payment status
     * @return count of payments
     */
    long countByStatus(PaymentStatus status);
    
    /**
     * Count payments by payment method
     * 
     * @param paymentMethod the payment method
     * @return count of payments
     */
    long countByPaymentMethod(String paymentMethod);
    
    /**
     * Count payments by payment gateway
     * 
     * @param paymentGateway the payment gateway
     * @return count of payments
     */
    long countByPaymentGateway(String paymentGateway);
    
    /**
     * Check if payment ID exists
     * 
     * @param paymentId the payment ID
     * @return true if exists
     */
    boolean existsByPaymentId(String paymentId);
    
    /**
     * Check if transaction ID exists
     * 
     * @param transactionId the transaction ID
     * @return true if exists
     */
    boolean existsByTransactionId(String transactionId);
    
    /**
     * Find successful payments for order
     * 
     * @param orderId the order ID
     * @return list of successful payments
     */
    @Query("SELECT op FROM OrderPayment op WHERE op.order.id = :orderId " +
           "AND op.status IN ('CAPTURED', 'SETTLED', 'AUTHORIZED') " +
           "ORDER BY op.createdAt DESC")
    List<OrderPayment> findSuccessfulPaymentsForOrder(@Param("orderId") Long orderId);
    
    /**
     * Find failed payments for order
     * 
     * @param orderId the order ID
     * @return list of failed payments
     */
    @Query("SELECT op FROM OrderPayment op WHERE op.order.id = :orderId " +
           "AND op.status IN ('FAILED', 'DECLINED', 'CANCELLED') " +
           "ORDER BY op.createdAt DESC")
    List<OrderPayment> findFailedPaymentsForOrder(@Param("orderId") Long orderId);
    
    /**
     * Find refunded payments for order
     * 
     * @param orderId the order ID
     * @return list of refunded payments
     */
    @Query("SELECT op FROM OrderPayment op WHERE op.order.id = :orderId " +
           "AND op.status IN ('REFUNDED', 'PARTIALLY_REFUNDED') " +
           "ORDER BY op.createdAt DESC")
    List<OrderPayment> findRefundedPaymentsForOrder(@Param("orderId") Long orderId);
    
    /**
     * Find payments with amount greater than specified amount
     * 
     * @param amount the minimum amount
     * @param currency the currency
     * @param pageable pagination information
     * @return page of payments
     */
    @Query("SELECT op FROM OrderPayment op WHERE op.amount.amount > :amount AND op.amount.currency = :currency")
    Page<OrderPayment> findPaymentsWithAmountGreaterThan(@Param("amount") BigDecimal amount, 
                                                         @Param("currency") String currency, 
                                                         Pageable pageable);
    
    /**
     * Find payments by customer IP
     * 
     * @param customerIp the customer IP address
     * @param pageable pagination information
     * @return page of payments
     */
    Page<OrderPayment> findByCustomerIp(String customerIp, Pageable pageable);
    
    /**
     * Find payments needing capture
     * 
     * @param pageable pagination information
     * @return page of payments needing capture
     */
    @Query("SELECT op FROM OrderPayment op WHERE op.status = 'AUTHORIZED' " +
           "AND (op.expiresAt IS NULL OR op.expiresAt > CURRENT_TIMESTAMP)")
    Page<OrderPayment> findPaymentsNeedingCapture(Pageable pageable);
    
    /**
     * Find payments eligible for refund
     * 
     * @param orderId the order ID
     * @return list of payments eligible for refund
     */
    @Query("SELECT op FROM OrderPayment op WHERE op.order.id = :orderId " +
           "AND op.status IN ('CAPTURED', 'SETTLED', 'PARTIALLY_REFUNDED') " +
           "AND (op.capturedAmount.amount > COALESCE(op.refundedAmount.amount, 0))")
    List<OrderPayment> findPaymentsEligibleForRefund(@Param("orderId") Long orderId);
    
    /**
     * Get total captured amount for order
     * 
     * @param orderId the order ID
     * @param currency the currency
     * @return total captured amount
     */
    @Query("SELECT COALESCE(SUM(op.capturedAmount.amount), 0) FROM OrderPayment op " +
           "WHERE op.order.id = :orderId AND op.capturedAmount.currency = :currency " +
           "AND op.status IN ('CAPTURED', 'SETTLED', 'PARTIALLY_REFUNDED')")
    BigDecimal getTotalCapturedAmountForOrder(@Param("orderId") Long orderId, @Param("currency") String currency);
    
    /**
     * Get total refunded amount for order
     * 
     * @param orderId the order ID
     * @param currency the currency
     * @return total refunded amount
     */
    @Query("SELECT COALESCE(SUM(op.refundedAmount.amount), 0) FROM OrderPayment op " +
           "WHERE op.order.id = :orderId AND op.refundedAmount.currency = :currency " +
           "AND op.refundedAmount.amount > 0")
    BigDecimal getTotalRefundedAmountForOrder(@Param("orderId") Long orderId, @Param("currency") String currency);
    
    /**
     * Get payment statistics for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return payment statistics
     */
    @Query("SELECT new map(" +
           "COUNT(op) as totalPayments, " +
           "SUM(op.amount.amount) as totalAmount, " +
           "SUM(CASE WHEN op.status IN ('CAPTURED', 'SETTLED') THEN op.capturedAmount.amount ELSE 0 END) as totalCaptured, " +
           "SUM(CASE WHEN op.refundedAmount.amount > 0 THEN op.refundedAmount.amount ELSE 0 END) as totalRefunded, " +
           "AVG(op.amount.amount) as averageAmount, " +
           "COUNT(DISTINCT op.order.id) as uniqueOrders) " +
           "FROM OrderPayment op WHERE op.createdAt BETWEEN :startDate AND :endDate")
    java.util.Map<String, Object> getPaymentStatistics(@Param("startDate") LocalDateTime startDate, 
                                                       @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get payment count by status for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return payment count by status
     */
    @Query("SELECT op.status, COUNT(op) FROM OrderPayment op " +
           "WHERE op.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY op.status " +
           "ORDER BY COUNT(op) DESC")
    List<Object[]> getPaymentCountByStatus(@Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get payment count by method for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return payment count by method
     */
    @Query("SELECT op.paymentMethod, COUNT(op), SUM(op.amount.amount) FROM OrderPayment op " +
           "WHERE op.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY op.paymentMethod " +
           "ORDER BY COUNT(op) DESC")
    List<Object[]> getPaymentCountByMethod(@Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get payment count by gateway for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return payment count by gateway
     */
    @Query("SELECT op.paymentGateway, COUNT(op), SUM(op.amount.amount) FROM OrderPayment op " +
           "WHERE op.createdAt BETWEEN :startDate AND :endDate " +
           "AND op.paymentGateway IS NOT NULL " +
           "GROUP BY op.paymentGateway " +
           "ORDER BY COUNT(op) DESC")
    List<Object[]> getPaymentCountByGateway(@Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get processing time data by payment method for calculation
     *
     * @param startDate start date
     * @param endDate end date
     * @return processing time data by payment method
     */
    @Query("SELECT op.paymentMethod, op.initiatedAt, op.processedAt " +
           "FROM OrderPayment op " +
           "WHERE op.createdAt BETWEEN :startDate AND :endDate " +
           "AND op.initiatedAt IS NOT NULL AND op.processedAt IS NOT NULL " +
           "ORDER BY op.paymentMethod")
    List<Object[]> getProcessingTimeDataByMethod(@Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get success rate by payment method
     * 
     * @param startDate start date
     * @param endDate end date
     * @return success rate by payment method
     */
    @Query("SELECT op.paymentMethod, " +
           "COUNT(op) as totalPayments, " +
           "SUM(CASE WHEN op.status IN ('CAPTURED', 'SETTLED', 'AUTHORIZED') THEN 1 ELSE 0 END) as successfulPayments, " +
           "(SUM(CASE WHEN op.status IN ('CAPTURED', 'SETTLED', 'AUTHORIZED') THEN 1 ELSE 0 END) * 100.0 / COUNT(op)) as successRate " +
           "FROM OrderPayment op " +
           "WHERE op.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY op.paymentMethod " +
           "ORDER BY successRate DESC")
    List<Object[]> getSuccessRateByMethod(@Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate);
    
    /**
     * Update payment status
     * 
     * @param paymentId the payment ID
     * @param status the new status
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderPayment op SET op.status = :status, op.updatedAt = :updatedAt WHERE op.id = :paymentId")
    int updatePaymentStatus(@Param("paymentId") Long paymentId, 
                           @Param("status") PaymentStatus status, 
                           @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Update captured amount
     * 
     * @param paymentId the payment ID
     * @param capturedAmount the captured amount
     * @param capturedAt the capture timestamp
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderPayment op SET op.capturedAmount.amount = :capturedAmount, " +
           "op.capturedAt = :capturedAt, op.updatedAt = :updatedAt WHERE op.id = :paymentId")
    int updateCapturedAmount(@Param("paymentId") Long paymentId, 
                            @Param("capturedAmount") BigDecimal capturedAmount, 
                            @Param("capturedAt") LocalDateTime capturedAt, 
                            @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Update refunded amount
     * 
     * @param paymentId the payment ID
     * @param refundedAmount the refunded amount
     * @param refundedAt the refund timestamp
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderPayment op SET op.refundedAmount.amount = :refundedAmount, " +
           "op.refundedAt = :refundedAt, op.updatedAt = :updatedAt WHERE op.id = :paymentId")
    int updateRefundedAmount(@Param("paymentId") Long paymentId, 
                            @Param("refundedAmount") BigDecimal refundedAmount, 
                            @Param("refundedAt") LocalDateTime refundedAt, 
                            @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Mark payments as expired
     * 
     * @param currentTime current timestamp
     * @param updatedAt the update timestamp
     * @return number of expired payments
     */
    @Modifying
    @Query("UPDATE OrderPayment op SET op.status = 'EXPIRED', op.updatedAt = :updatedAt " +
           "WHERE op.expiresAt < :currentTime AND op.status = 'AUTHORIZED'")
    int markExpiredPayments(@Param("currentTime") LocalDateTime currentTime, 
                           @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Delete payments by order ID (soft delete)
     * 
     * @param orderId the order ID
     * @param deletedAt the deletion timestamp
     * @return number of deleted records
     */
    @Modifying
    @Query("UPDATE OrderPayment op SET op.deletedAt = :deletedAt WHERE op.order.id = :orderId")
    int softDeleteByOrderId(@Param("orderId") Long orderId, @Param("deletedAt") LocalDateTime deletedAt);
    
    /**
     * Find payments needing retry
     * 
     * @param maxAttempts maximum number of attempts
     * @param retryStatuses statuses eligible for retry
     * @param hours hours since last attempt
     * @return list of payments needing retry
     */
    @Query("SELECT op FROM OrderPayment op WHERE op.attemptNumber < :maxAttempts " +
           "AND op.status IN :retryStatuses " +
           "AND op.createdAt < :cutoffTime")
    List<OrderPayment> findPaymentsNeedingRetry(@Param("maxAttempts") Integer maxAttempts, 
                                               @Param("retryStatuses") List<PaymentStatus> retryStatuses, 
                                               @Param("cutoffTime") LocalDateTime cutoffTime);
}
