package org.de013.paymentservice.exception;

/**
 * Exception thrown when a payment is not found
 */
public class PaymentNotFoundException extends RuntimeException {

    private Long paymentId;
    private String paymentNumber;

    public PaymentNotFoundException(String message) {
        super(message);
    }

    public PaymentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentNotFoundException(String message, Long paymentId) {
        super(message);
        this.paymentId = paymentId;
    }

    public PaymentNotFoundException(String message, String paymentNumber) {
        super(message);
        this.paymentNumber = paymentNumber;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getPaymentNumber() {
        return paymentNumber;
    }
}
