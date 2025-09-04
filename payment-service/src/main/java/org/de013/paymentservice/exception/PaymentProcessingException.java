package org.de013.paymentservice.exception;

import org.de013.common.exception.BusinessException;

/**
 * Exception thrown when payment processing fails.
 * This includes failures during payment creation, confirmation, capture, or cancellation.
 */
public class PaymentProcessingException extends BusinessException {

    private final String paymentId;
    private final String operation;
    private final String errorCode;
    private final String gatewayError;

    public PaymentProcessingException(String message) {
        super(message);
        this.paymentId = null;
        this.operation = null;
        this.errorCode = null;
        this.gatewayError = null;
    }

    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.paymentId = null;
        this.operation = null;
        this.errorCode = null;
        this.gatewayError = null;
    }

    public PaymentProcessingException(String message, String paymentId, String operation) {
        super(message);
        this.paymentId = paymentId;
        this.operation = operation;
        this.errorCode = null;
        this.gatewayError = null;
    }

    public PaymentProcessingException(String message, String paymentId, String operation, String errorCode) {
        super(message);
        this.paymentId = paymentId;
        this.operation = operation;
        this.errorCode = errorCode;
        this.gatewayError = null;
    }

    public PaymentProcessingException(String message, String paymentId, String operation, String errorCode, String gatewayError) {
        super(message);
        this.paymentId = paymentId;
        this.operation = operation;
        this.errorCode = errorCode;
        this.gatewayError = gatewayError;
    }

    public PaymentProcessingException(String message, String paymentId, String operation, Throwable cause) {
        super(message, cause);
        this.paymentId = paymentId;
        this.operation = operation;
        this.errorCode = null;
        this.gatewayError = null;
    }

    public PaymentProcessingException(String message, String paymentId, String operation, String errorCode, String gatewayError, Throwable cause) {
        super(message, cause);
        this.paymentId = paymentId;
        this.operation = operation;
        this.errorCode = errorCode;
        this.gatewayError = gatewayError;
    }

    // Getters
    public String getPaymentId() {
        return paymentId;
    }

    public String getOperation() {
        return operation;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getGatewayError() {
        return gatewayError;
    }

    // Static factory methods for common scenarios
    public static PaymentProcessingException forProcessing(String paymentId, String reason) {
        return new PaymentProcessingException(
            "Failed to process payment: " + reason,
            paymentId,
            "PROCESS"
        );
    }

    public static PaymentProcessingException forConfirmation(String paymentId, String reason) {
        return new PaymentProcessingException(
            "Failed to confirm payment: " + reason,
            paymentId,
            "CONFIRM"
        );
    }

    public static PaymentProcessingException forCapture(String paymentId, String reason) {
        return new PaymentProcessingException(
            "Failed to capture payment: " + reason,
            paymentId,
            "CAPTURE"
        );
    }

    public static PaymentProcessingException forCancellation(String paymentId, String reason) {
        return new PaymentProcessingException(
            "Failed to cancel payment: " + reason,
            paymentId,
            "CANCEL"
        );
    }

    public static PaymentProcessingException forSync(String paymentId, String reason) {
        return new PaymentProcessingException(
            "Failed to sync payment with provider: " + reason,
            paymentId,
            "SYNC"
        );
    }
}
