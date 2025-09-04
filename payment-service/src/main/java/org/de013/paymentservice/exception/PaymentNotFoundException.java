package org.de013.paymentservice.exception;

import org.de013.common.exception.ResourceNotFoundException;

/**
 * Exception thrown when a payment is not found.
 * This is a specific type of ResourceNotFoundException for payment-related operations.
 */
public class PaymentNotFoundException extends ResourceNotFoundException {

    public PaymentNotFoundException(String message) {
        super(message);
    }



    /**
     * Creates a PaymentNotFoundException for a payment ID.
     */
    public static PaymentNotFoundException forId(Long paymentId) {
        return new PaymentNotFoundException("Payment not found with ID: " + paymentId);
    }

    /**
     * Creates a PaymentNotFoundException for a payment number.
     */
    public static PaymentNotFoundException forNumber(String paymentNumber) {
        return new PaymentNotFoundException("Payment not found with number: " + paymentNumber);
    }

    /**
     * Creates a PaymentNotFoundException for a user ID.
     */
    public static PaymentNotFoundException forUser(Long userId) {
        return new PaymentNotFoundException("No payments found for user ID: " + userId);
    }

    /**
     * Creates a PaymentNotFoundException for an order ID.
     */
    public static PaymentNotFoundException forOrder(Long orderId) {
        return new PaymentNotFoundException("No payment found for order ID: " + orderId);
    }
}
