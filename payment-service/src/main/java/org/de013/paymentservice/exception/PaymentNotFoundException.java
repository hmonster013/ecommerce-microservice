package org.de013.paymentservice.exception;

import org.de013.common.exception.ResourceNotFoundException;

/**
 * Exception thrown when a payment is not found
 */
public class PaymentNotFoundException extends ResourceNotFoundException {

    private Long paymentId;
    private String paymentNumber;

    public PaymentNotFoundException(String message) {
        super(message);
    }

    public PaymentNotFoundException(Long paymentId) {
        super("Payment", "ID", paymentId);
        this.paymentId = paymentId;
    }

    public static PaymentNotFoundException byPaymentNumber(String paymentNumber) {
        PaymentNotFoundException ex = new PaymentNotFoundException("Payment not found with number: " + paymentNumber);
        ex.paymentNumber = paymentNumber;
        return ex;
    }

    public PaymentNotFoundException(String field, Object value) {
        super("Payment", field, value);
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getPaymentNumber() {
        return paymentNumber;
    }
}
