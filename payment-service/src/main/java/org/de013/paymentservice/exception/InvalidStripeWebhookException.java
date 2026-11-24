package org.de013.paymentservice.exception;

public class InvalidStripeWebhookException extends PaymentGatewayException {
    public InvalidStripeWebhookException(Throwable cause) {
        super("Invalid Stripe webhook", cause);
    }
}
