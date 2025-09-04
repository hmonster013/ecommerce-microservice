package org.de013.paymentservice.exception;

import org.de013.common.exception.ResourceNotFoundException;

/**
 * Exception thrown when a payment method is not found
 */
public class PaymentMethodNotFoundException extends ResourceNotFoundException {

    private Long paymentMethodId;
    private String stripePaymentMethodId;

    public PaymentMethodNotFoundException(String message) {
        super(message);
    }

    public PaymentMethodNotFoundException(String message, Throwable cause) {
        super(message + (cause != null ? ": " + cause.getMessage() : ""));
    }

    public PaymentMethodNotFoundException(String message, Long paymentMethodId) {
        super(message);
        this.paymentMethodId = paymentMethodId;
    }

    public PaymentMethodNotFoundException(String message, String stripePaymentMethodId) {
        super(message);
        this.stripePaymentMethodId = stripePaymentMethodId;
    }

    public Long getPaymentMethodId() {
        return paymentMethodId;
    }

    public String getStripePaymentMethodId() {
        return stripePaymentMethodId;
    }
}
