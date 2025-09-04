package org.de013.paymentservice.exception;

import org.de013.common.exception.BusinessException;
import java.math.BigDecimal;

/**
 * Exception thrown when refund operations fail.
 * This includes failures during refund creation, processing, approval, or cancellation.
 */
public class RefundException extends BusinessException {
    
    private final String refundId;
    private final String paymentId;
    private final String operation;
    private final String refundStatus;
    private final BigDecimal refundAmount;
    private final String currency;
    private final String errorCode;
    
    public RefundException(String message) {
        super(message);
        this.refundId = null;
        this.paymentId = null;
        this.operation = null;
        this.refundStatus = null;
        this.refundAmount = null;
        this.currency = null;
        this.errorCode = null;
    }
    
    public RefundException(String message, Throwable cause) {
        super(message, cause);
        this.refundId = null;
        this.paymentId = null;
        this.operation = null;
        this.refundStatus = null;
        this.refundAmount = null;
        this.currency = null;
        this.errorCode = null;
    }
    
    public RefundException(String message, String refundId, String paymentId, String operation) {
        super(message);
        this.refundId = refundId;
        this.paymentId = paymentId;
        this.operation = operation;
        this.refundStatus = null;
        this.refundAmount = null;
        this.currency = null;
        this.errorCode = null;
    }
    
    public RefundException(String message, String refundId, String paymentId, String operation, 
                          String errorCode, Throwable cause) {
        super(message, cause);
        this.refundId = refundId;
        this.paymentId = paymentId;
        this.operation = operation;
        this.refundStatus = null;
        this.refundAmount = null;
        this.currency = null;
        this.errorCode = errorCode;
    }
    
    public RefundException(String message, String refundId, String paymentId, String operation,
                          String refundStatus, BigDecimal refundAmount, String currency, String errorCode) {
        super(message);
        this.refundId = refundId;
        this.paymentId = paymentId;
        this.operation = operation;
        this.refundStatus = refundStatus;
        this.refundAmount = refundAmount;
        this.currency = currency;
        this.errorCode = errorCode;
    }
    
    // Getters
    public String getRefundId() {
        return refundId;
    }
    
    public String getPaymentId() {
        return paymentId;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public String getRefundStatus() {
        return refundStatus;
    }
    
    public BigDecimal getRefundAmount() {
        return refundAmount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    // Static factory methods for common scenarios
    public static RefundException notFound(String refundId) {
        return new RefundException(
            "Refund not found with ID: " + refundId,
            refundId, null, "RETRIEVE"
        );
    }
    
    public static RefundException processingFailed(String refundId, String paymentId, String reason) {
        return new RefundException(
            "Failed to process refund: " + reason,
            refundId, paymentId, "PROCESS"
        );
    }
    
    public static RefundException approvalFailed(String refundId, String paymentId, String reason) {
        return new RefundException(
            "Failed to approve refund: " + reason,
            refundId, paymentId, "APPROVE"
        );
    }
    
    public static RefundException rejectionFailed(String refundId, String paymentId, String reason) {
        return new RefundException(
            "Failed to reject refund: " + reason,
            refundId, paymentId, "REJECT"
        );
    }
    
    public static RefundException cancellationFailed(String refundId, String paymentId, String reason) {
        return new RefundException(
            "Failed to cancel refund: " + reason,
            refundId, paymentId, "CANCEL"
        );
    }
    
    public static RefundException invalidStatus(String refundId, String currentStatus, String requiredStatus) {
        return new RefundException(
            String.format("Invalid refund status. Current: %s, Required: %s", currentStatus, requiredStatus),
            refundId, null, "STATUS_CHECK"
        );
    }
    
    public static RefundException amountExceeded(String refundId, String paymentId, 
                                               BigDecimal requestedAmount, BigDecimal maxAmount, String currency) {
        String message = String.format(
            "Refund amount exceeds maximum allowed. Requested: %s %s, Maximum: %s %s",
            requestedAmount, currency, maxAmount, currency
        );
        return new RefundException(
            message, refundId, paymentId, "VALIDATE", 
            null, requestedAmount, currency, "AMOUNT_EXCEEDED"
        );
    }
    
    public static RefundException alreadyRefunded(String refundId, String paymentId, BigDecimal totalRefunded, String currency) {
        String message = String.format(
            "Payment has already been fully refunded. Total refunded: %s %s",
            totalRefunded, currency
        );
        return new RefundException(
            message, refundId, paymentId, "VALIDATE",
            "COMPLETED", totalRefunded, currency, "ALREADY_REFUNDED"
        );
    }
    
    public static RefundException paymentNotRefundable(String paymentId, String paymentStatus) {
        return new RefundException(
            String.format("Payment cannot be refunded. Payment status: %s", paymentStatus),
            null, paymentId, "VALIDATE"
        );
    }
    
    public static RefundException syncFailed(String refundId, String paymentId, String reason) {
        return new RefundException(
            "Failed to sync refund with payment provider: " + reason,
            refundId, paymentId, "SYNC"
        );
    }
}
