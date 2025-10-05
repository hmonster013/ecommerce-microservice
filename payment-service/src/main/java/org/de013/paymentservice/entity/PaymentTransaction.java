package org.de013.paymentservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.entity.enums.TransactionType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PaymentTransaction entity for tracking individual payment operations
 */
@Entity
@Table(name = "payment_transactions", indexes = {
    @Index(name = "idx_payment_id", columnList = "paymentId"),
    @Index(name = "idx_type", columnList = "type"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_stripe_charge_id", columnList = "stripeChargeId", unique = true),
    @Index(name = "idx_stripe_transfer_group", columnList = "stripeTransferGroup"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PaymentTransaction extends BaseEntity{

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "payment_id", insertable = false, updatable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    // Stripe-specific fields
    @Column(name = "stripe_charge_id", unique = true, length = 100)
    private String stripeChargeId;

    @Column(name = "stripe_payment_intent_id", length = 100)
    private String stripePaymentIntentId;

    @Column(name = "stripe_transfer_group", length = 100)
    private String stripeTransferGroup;

    @Column(name = "stripe_response", columnDefinition = "TEXT")
    private String stripeResponse;

    // Transaction details
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "gateway_transaction_id", length = 100)
    private String gatewayTransactionId; // Generic field for other gateways

    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse;

    // Processing details
    @Column(name = "processing_fee", precision = 19, scale = 4)
    private BigDecimal processingFee;

    @Column(name = "net_amount", precision = 19, scale = 2)
    private BigDecimal netAmount; // amount - processingFee

    @Column(name = "currency", length = 3)
    private String currency;

    // Timing information
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    // Helper methods
    public boolean isSuccessful() {
        return status == PaymentStatus.SUCCEEDED;
    }

    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING || status == PaymentStatus.PROCESSING;
    }

    public boolean requiresAction() {
        return status == PaymentStatus.REQUIRES_ACTION || 
               status == PaymentStatus.REQUIRES_CONFIRMATION ||
               status == PaymentStatus.REQUIRES_PAYMENT_METHOD;
    }

    public void markAsProcessed() {
        this.processedAt = LocalDateTime.now();
        if (this.status == PaymentStatus.PENDING || this.status == PaymentStatus.PROCESSING) {
            this.status = PaymentStatus.SUCCEEDED;
        }
    }

    public void markAsSettled() {
        this.settledAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.processedAt = LocalDateTime.now();
    }

    public BigDecimal calculateNetAmount() {
        if (processingFee == null) {
            return amount;
        }
        return amount.subtract(processingFee);
    }

    @PrePersist
    @PreUpdate
    private void updateNetAmount() {
        this.netAmount = calculateNetAmount();
    }
}
