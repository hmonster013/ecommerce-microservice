package org.de013.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import org.de013.paymentservice.entity.enums.RefundStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Refund entity for tracking payment refunds
 */
@Entity
@Table(name = "refunds", indexes = {
    @Index(name = "idx_payment_id", columnList = "paymentId"),
    @Index(name = "idx_order_id", columnList = "orderId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_stripe_refund_id", columnList = "stripeRefundId", unique = true),
    @Index(name = "idx_refund_number", columnList = "refundNumber", unique = true),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Refund extends BaseEntity {

    @Column(name = "refund_number", nullable = false, unique = true, length = 50)
    private String refundNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "payment_id", nullable = false, insertable = false, updatable = false)
    private Long paymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RefundStatus status;

    @Column(name = "reason", length = 500)
    private String reason;

    // Stripe-specific fields
    @Column(name = "stripe_refund_id", unique = true, length = 100)
    private String stripeRefundId;

    @Column(name = "stripe_charge_id", length = 100)
    private String stripeChargeId;

    @Column(name = "stripe_payment_intent_id", length = 100)
    private String stripePaymentIntentId;

    @Column(name = "stripe_response", columnDefinition = "TEXT")
    private String stripeResponse;

    // Refund details
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "receipt_number", length = 100)
    private String receiptNumber;

    // Processing details
    @Column(name = "processing_fee_refunded", precision = 19, scale = 4)
    private BigDecimal processingFeeRefunded;

    @Column(name = "net_refund_amount", precision = 19, scale = 2)
    private BigDecimal netRefundAmount;

    // Timing information
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "expected_arrival_date")
    private LocalDateTime expectedArrivalDate;

    // Refund metadata
    @Column(name = "refund_type", length = 30)
    @Builder.Default
    private String refundType = "FULL"; // FULL, PARTIAL

    @Column(name = "initiated_by", length = 100)
    private String initiatedBy; // USER, ADMIN, SYSTEM

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // Helper methods
    public boolean isSuccessful() {
        return status == RefundStatus.SUCCEEDED;
    }

    public boolean isFailed() {
        return status == RefundStatus.FAILED;
    }

    public boolean isPending() {
        return status == RefundStatus.PENDING;
    }

    public boolean requiresAction() {
        return status == RefundStatus.REQUIRES_ACTION;
    }

    public boolean isFullRefund() {
        return "FULL".equals(refundType);
    }

    public boolean isPartialRefund() {
        return "PARTIAL".equals(refundType);
    }

    public void markAsProcessed() {
        this.status = RefundStatus.SUCCEEDED;
        this.processedAt = LocalDateTime.now();
    }

    public void markAsSettled() {
        this.settledAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.status = RefundStatus.FAILED;
        this.failureReason = reason;
        this.processedAt = LocalDateTime.now();
    }

    public void approve(String approvedBy) {
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
    }

    public BigDecimal calculateNetRefundAmount() {
        if (processingFeeRefunded == null) {
            return amount;
        }
        return amount.add(processingFeeRefunded);
    }

    @PrePersist
    private void prePersist() {
        // Set defaults
        if (this.currency == null && this.payment != null) {
            this.currency = this.payment.getCurrency().name();
        }
        if (this.orderId == null && this.payment != null) {
            this.orderId = this.payment.getOrderId();
        }
        // Update net refund amount
        this.netRefundAmount = calculateNetRefundAmount();
    }

    @PreUpdate
    private void preUpdate() {
        // Update net refund amount on updates
        this.netRefundAmount = calculateNetRefundAmount();
    }

    // Helper method for getting payment ID
    public Long getPaymentId() {
        return payment != null ? payment.getId() : null;
    }
}
