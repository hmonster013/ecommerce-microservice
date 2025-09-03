package org.de013.paymentservice.exception;

/**
 * Exception thrown when a payment method is not found
 */
public class PaymentMethodNotFoundException extends RuntimeException {

    private Long paymentMethodId;
    private String stripePaymentMethodId;

    public PaymentMethodNotFoundException(String message) {
        super(message);
    }

    public PaymentMethodNotFoundException(String message, Throwable cause) {
        super(message, cause);
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
