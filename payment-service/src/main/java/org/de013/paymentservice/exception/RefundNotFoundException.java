package org.de013.paymentservice.exception;

import org.de013.common.exception.ResourceNotFoundException;

/**
 * Exception thrown when a refund is not found
 */
public class RefundNotFoundException extends ResourceNotFoundException {

    private Long refundId;
    private String refundNumber;
    private String stripeRefundId;

    public RefundNotFoundException(String message) {
        super(message);
    }

    public RefundNotFoundException(String message, Throwable cause) {
        super(message + (cause != null ? ": " + cause.getMessage() : ""));
    }

    public RefundNotFoundException(String message, Long refundId) {
        super(message);
        this.refundId = refundId;
    }

    public RefundNotFoundException(String message, String identifier, boolean isRefundNumber) {
        super(message);
        if (isRefundNumber) {
            this.refundNumber = identifier;
        } else {
            this.stripeRefundId = identifier;
        }
    }

    public Long getRefundId() {
        return refundId;
    }

    public String getRefundNumber() {
        return refundNumber;
    }

    public String getStripeRefundId() {
        return stripeRefundId;
    }
}
