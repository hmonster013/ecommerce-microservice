package org.de013.paymentservice.exception;

/**
 * Exception thrown when payment gateway operations fail
 */
public class PaymentGatewayException extends RuntimeException {

    private String gatewayCode;
    private String gatewayMessage;

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentGatewayException(String message, String gatewayCode, String gatewayMessage) {
        super(message);
        this.gatewayCode = gatewayCode;
        this.gatewayMessage = gatewayMessage;
    }

    public PaymentGatewayException(String message, String gatewayCode, String gatewayMessage, Throwable cause) {
        super(message, cause);
        this.gatewayCode = gatewayCode;
        this.gatewayMessage = gatewayMessage;
    }

    public String getGatewayCode() {
        return gatewayCode;
    }

    public String getGatewayMessage() {
        return gatewayMessage;
    }
}
