package org.de013.paymentservice.exception;

/**
 * Exception thrown when payment processing fails
 */
public class PaymentProcessingException extends RuntimeException {

    private String errorCode;
    private String gatewayError;

    public PaymentProcessingException(String message) {
        super(message);
    }

    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentProcessingException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public PaymentProcessingException(String message, String errorCode, String gatewayError) {
        super(message);
        this.errorCode = errorCode;
        this.gatewayError = gatewayError;
    }

    public PaymentProcessingException(String message, String errorCode, String gatewayError, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.gatewayError = gatewayError;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getGatewayError() {
        return gatewayError;
    }
}
