package org.de013.orderservice.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.orderservice.entity.enums.PaymentStatus;
import org.de013.orderservice.entity.valueobject.Money;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Order Payment Entity
 *
 * Represents payment information and transactions for an order.
 * Maintains payment history and status tracking.
 *
 * @author Development Team
 * @version 1.0.0
 */
@Entity
@Table(name = "order_payments", indexes = {
    @Index(name = "idx_order_payments_order_id", columnList = "order_id"),
    @Index(name = "idx_order_payments_payment_id", columnList = "payment_id"),
    @Index(name = "idx_order_payments_payment_status", columnList = "payment_status"),
    @Index(name = "idx_order_payments_payment_method", columnList = "payment_method"),
    @Index(name = "idx_order_payments_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_order_payments_order_status", columnList = "order_id, payment_status"),
    @Index(name = "idx_order_payments_processed_at", columnList = "processed_at")
})
@SQLDelete(sql = "UPDATE order_payments SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"order"})
@EqualsAndHashCode(callSuper = true, exclude = {"order"})
public class OrderPayment extends BaseEntity {

    /**
     * Reference to the parent order
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @NotNull(message = "Order is required")
    private Order order;

    /**
     * External payment ID from payment service
     */
    @Column(name = "payment_id", length = 100, nullable = false)
    @NotBlank(message = "Payment ID is required")
    @Size(max = 100, message = "Payment ID must not exceed 100 characters")
    private String paymentId;

    /**
     * Transaction ID from payment gateway
     */
    @Column(name = "transaction_id", length = 200)
    @Size(max = 200, message = "Transaction ID must not exceed 200 characters")
    private String transactionId;

    /**
     * Payment method used (CREDIT_CARD, DEBIT_CARD, PAYPAL, etc.)
     */
    @Column(name = "payment_method", length = 50, nullable = false)
    @NotBlank(message = "Payment method is required")
    @Size(max = 50, message = "Payment method must not exceed 50 characters")
    private String paymentMethod;

    /**
     * Payment method details (last 4 digits of card, PayPal email, etc.)
     */
    @Column(name = "payment_method_details", length = 500)
    @Size(max = 500, message = "Payment method details must not exceed 500 characters")
    private String paymentMethodDetails;

    /**
     * Payment status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 30, nullable = false)
    @NotNull(message = "Payment status is required")
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * Payment amount
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "currency", length = 3))
    })
    @Valid
    @NotNull(message = "Payment amount is required")
    private Money amount;

    /**
     * Authorized amount (may be different from requested amount)
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "authorized_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "authorized_currency", length = 3))
    })
    @Valid
    private Money authorizedAmount;

    /**
     * Captured amount (may be different from authorized amount)
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "captured_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "captured_currency", length = 3))
    })
    @Valid
    private Money capturedAmount;

    /**
     * Refunded amount
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "refunded_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "refunded_currency", length = 3))
    })
    @Valid
    private Money refundedAmount;

    /**
     * Payment gateway used (STRIPE, PAYPAL, SQUARE, etc.)
     */
    @Column(name = "payment_gateway", length = 50)
    @Size(max = 50, message = "Payment gateway must not exceed 50 characters")
    private String paymentGateway;

    /**
     * Gateway transaction reference
     */
    @Column(name = "gateway_transaction_ref", length = 200)
    @Size(max = 200, message = "Gateway transaction reference must not exceed 200 characters")
    private String gatewayTransactionRef;

    /**
     * Authorization code from payment gateway
     */
    @Column(name = "authorization_code", length = 100)
    @Size(max = 100, message = "Authorization code must not exceed 100 characters")
    private String authorizationCode;

    /**
     * Payment processor response code
     */
    @Column(name = "processor_response_code", length = 20)
    @Size(max = 20, message = "Processor response code must not exceed 20 characters")
    private String processorResponseCode;

    /**
     * Payment processor response message
     */
    @Column(name = "processor_response_message", length = 500)
    @Size(max = 500, message = "Processor response message must not exceed 500 characters")
    private String processorResponseMessage;

    /**
     * Risk score from fraud detection (0-100)
     */
    @Column(name = "risk_score")
    @Min(value = 0, message = "Risk score must be at least 0")
    @Max(value = 100, message = "Risk score must be at most 100")
    private Integer riskScore;

    /**
     * Risk assessment result (LOW, MEDIUM, HIGH, BLOCKED)
     */
    @Column(name = "risk_assessment", length = 20)
    @Size(max = 20, message = "Risk assessment must not exceed 20 characters")
    private String riskAssessment;

    /**
     * CVV verification result
     */
    @Column(name = "cvv_result", length = 10)
    @Size(max = 10, message = "CVV result must not exceed 10 characters")
    private String cvvResult;

    /**
     * AVS (Address Verification System) result
     */
    @Column(name = "avs_result", length = 10)
    @Size(max = 10, message = "AVS result must not exceed 10 characters")
    private String avsResult;

    /**
     * 3D Secure authentication result
     */
    @Column(name = "three_d_secure_result", length = 20)
    @Size(max = 20, message = "3D Secure result must not exceed 20 characters")
    private String threeDSecureResult;

    /**
     * Payment initiated timestamp
     */
    @Column(name = "initiated_at")
    private LocalDateTime initiatedAt;

    /**
     * Payment processed timestamp
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /**
     * Payment authorized timestamp
     */
    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    /**
     * Payment captured timestamp
     */
    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    /**
     * Payment failed timestamp
     */
    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    /**
     * Payment refunded timestamp
     */
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    /**
     * Payment expiry timestamp (for authorization)
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Failure reason if payment failed
     */
    @Column(name = "failure_reason", length = 1000)
    @Size(max = 1000, message = "Failure reason must not exceed 1000 characters")
    private String failureReason;

    /**
     * Refund reason if payment was refunded
     */
    @Column(name = "refund_reason", length = 1000)
    @Size(max = 1000, message = "Refund reason must not exceed 1000 characters")
    private String refundReason;

    /**
     * Customer IP address at time of payment
     */
    @Column(name = "customer_ip", length = 45)
    @Size(max = 45, message = "Customer IP must not exceed 45 characters")
    private String customerIp;

    /**
     * Customer user agent at time of payment
     */
    @Column(name = "customer_user_agent", length = 1000)
    @Size(max = 1000, message = "Customer user agent must not exceed 1000 characters")
    private String customerUserAgent;

    /**
     * Additional metadata in JSON format
     */
    @Column(name = "metadata", length = 2000)
    @Size(max = 2000, message = "Metadata must not exceed 2000 characters")
    private String metadata;

    /**
     * Whether this is a test payment
     */
    @Column(name = "is_test")
    @Builder.Default
    private Boolean isTest = false;

    /**
     * Payment attempt number
     */
    @Column(name = "attempt_number")
    @Min(value = 1, message = "Attempt number must be at least 1")
    @Builder.Default
    private Integer attemptNumber = 1;

    /**
     * Soft delete timestamp
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ==================== Business Methods ====================

    /**
     * Check if payment is successful
     *
     * @return true if payment is successful
     */
    public boolean isSuccessful() {
        return status != null && status.isSuccessful();
    }

    /**
     * Check if payment has failed
     *
     * @return true if payment has failed
     */
    public boolean isFailed() {
        return status != null && status.isFailed();
    }

    /**
     * Check if payment is pending
     *
     * @return true if payment is pending
     */
    public boolean isPending() {
        return status != null && status.isPending();
    }

    /**
     * Check if payment can be refunded
     *
     * @return true if payment can be refunded
     */
    public boolean canBeRefunded() {
        return status != null && status.canBeRefunded();
    }

    /**
     * Check if payment can be cancelled
     *
     * @return true if payment can be cancelled
     */
    public boolean canBeCancelled() {
        return status != null && status.canBeCancelled();
    }

    /**
     * Get the available refund amount
     *
     * @return available refund amount
     */
    public Money getAvailableRefundAmount() {
        if (capturedAmount == null) {
            return Money.zero(amount.getCurrency());
        }

        if (refundedAmount != null && refundedAmount.isPositive()) {
            return capturedAmount.subtract(refundedAmount);
        }

        return capturedAmount;
    }

    /**
     * Check if payment is fully refunded
     *
     * @return true if fully refunded
     */
    public boolean isFullyRefunded() {
        return refundedAmount != null && capturedAmount != null &&
               refundedAmount.isGreaterThanOrEqual(capturedAmount);
    }

    /**
     * Check if payment is partially refunded
     *
     * @return true if partially refunded
     */
    public boolean isPartiallyRefunded() {
        return refundedAmount != null && refundedAmount.isPositive() && !isFullyRefunded();
    }

    /**
     * Get the refund percentage
     *
     * @return refund percentage (0-100)
     */
    public java.math.BigDecimal getRefundPercentage() {
        if (refundedAmount == null || !refundedAmount.isPositive() ||
            capturedAmount == null || !capturedAmount.isPositive()) {
            return java.math.BigDecimal.ZERO;
        }

        return refundedAmount.getAmount()
                .divide(capturedAmount.getAmount(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal.valueOf(100));
    }

    /**
     * Check if payment is high risk
     *
     * @return true if high risk
     */
    public boolean isHighRisk() {
        return riskScore != null && riskScore >= 70;
    }

    /**
     * Check if payment is expired
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Get payment processing time in minutes
     *
     * @return processing time in minutes
     */
    public long getProcessingTimeMinutes() {
        if (initiatedAt != null && processedAt != null) {
            return java.time.Duration.between(initiatedAt, processedAt).toMinutes();
        }
        return 0;
    }

    /**
     * Check if payment processing was fast (under 5 minutes)
     *
     * @return true if processing was fast
     */
    public boolean isFastProcessing() {
        return getProcessingTimeMinutes() <= 5;
    }

    /**
     * Update payment status with timestamp
     *
     * @param newStatus the new payment status
     */
    public void updateStatus(PaymentStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("New status cannot be null");
        }

        if (status == null || status.canTransitionTo(newStatus)) {
            PaymentStatus oldStatus = this.status;
            this.status = newStatus;

            // Update relevant timestamps
            LocalDateTime now = LocalDateTime.now();
            switch (newStatus) {
                case INITIATED -> this.initiatedAt = now;
                case PROCESSING -> {
                    if (this.initiatedAt == null) this.initiatedAt = now;
                }
                case AUTHORIZED -> {
                    this.authorizedAt = now;
                    this.processedAt = now;
                }
                case CAPTURED -> {
                    this.capturedAt = now;
                    if (this.processedAt == null) this.processedAt = now;
                }
                case FAILED, DECLINED, CANCELLED -> {
                    this.failedAt = now;
                    if (this.processedAt == null) this.processedAt = now;
                }
                case REFUNDED, PARTIALLY_REFUNDED -> this.refundedAt = now;
            }
        } else {
            throw new IllegalArgumentException(
                String.format("Cannot transition from %s to %s", status, newStatus));
        }
    }

    /**
     * Process authorization
     *
     * @param authAmount the authorized amount
     * @param authCode the authorization code
     */
    public void processAuthorization(Money authAmount, String authCode) {
        this.authorizedAmount = authAmount;
        this.authorizationCode = authCode;
        updateStatus(PaymentStatus.AUTHORIZED);
    }

    /**
     * Process capture
     *
     * @param captureAmount the captured amount
     */
    public void processCapture(Money captureAmount) {
        this.capturedAmount = captureAmount;
        updateStatus(PaymentStatus.CAPTURED);
    }

    /**
     * Process refund
     *
     * @param refundAmount the refund amount
     * @param reason the refund reason
     */
    public void processRefund(Money refundAmount, String reason) {
        if (this.refundedAmount == null) {
            this.refundedAmount = refundAmount;
        } else {
            this.refundedAmount = this.refundedAmount.add(refundAmount);
        }

        this.refundReason = reason;

        if (isFullyRefunded()) {
            updateStatus(PaymentStatus.REFUNDED);
        } else {
            updateStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
    }

    /**
     * Mark payment as failed
     *
     * @param reason the failure reason
     */
    public void markAsFailed(String reason) {
        this.failureReason = reason;
        updateStatus(PaymentStatus.FAILED);
    }

    /**
     * Soft delete this payment record
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * Check if this payment record is soft deleted
     *
     * @return true if soft deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
