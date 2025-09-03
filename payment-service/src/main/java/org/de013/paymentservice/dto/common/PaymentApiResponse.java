package org.de013.paymentservice.dto.common;

import org.de013.common.dto.ApiResponse;

/**
 * Payment-specific API response wrapper extending common ApiResponse
 * Provides payment-specific factory methods and constants
 */
public class PaymentApiResponse<T> extends ApiResponse<T> {

    // Payment-specific response codes
    public static final String PAYMENT_SUCCESS = "PAYMENT_SUCCESS";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_REQUIRES_ACTION = "PAYMENT_REQUIRES_ACTION";
    public static final String PAYMENT_CANCELED = "PAYMENT_CANCELED";
    public static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
    public static final String PAYMENT_ALREADY_PROCESSED = "PAYMENT_ALREADY_PROCESSED";
    
    public static final String REFUND_SUCCESS = "REFUND_SUCCESS";
    public static final String REFUND_FAILED = "REFUND_FAILED";
    public static final String REFUND_NOT_FOUND = "REFUND_NOT_FOUND";
    public static final String REFUND_AMOUNT_EXCEEDED = "REFUND_AMOUNT_EXCEEDED";
    
    public static final String PAYMENT_METHOD_SUCCESS = "PAYMENT_METHOD_SUCCESS";
    public static final String PAYMENT_METHOD_NOT_FOUND = "PAYMENT_METHOD_NOT_FOUND";
    public static final String PAYMENT_METHOD_EXPIRED = "PAYMENT_METHOD_EXPIRED";
    public static final String PAYMENT_METHOD_INVALID = "PAYMENT_METHOD_INVALID";
    
    public static final String STRIPE_ERROR = "STRIPE_ERROR";
    public static final String WEBHOOK_ERROR = "WEBHOOK_ERROR";

    // Payment success responses
    public static <T> PaymentApiResponse<T> paymentSuccess(T data) {
        PaymentApiResponse<T> response = new PaymentApiResponse<>();
        response.setSuccess(true);
        response.setCode(PAYMENT_SUCCESS);
        response.setMessage("Payment processed successfully");
        response.setData(data);
        response.setTimestamp(java.time.LocalDateTime.now());
        return response;
    }

    public static <T> PaymentApiResponse<T> paymentSuccess(T data, String message) {
        PaymentApiResponse<T> response = new PaymentApiResponse<>();
        response.setSuccess(true);
        response.setCode(PAYMENT_SUCCESS);
        response.setMessage(message);
        response.setData(data);
        response.setTimestamp(java.time.LocalDateTime.now());
        return response;
    }

    // Payment requires action
    public static <T> PaymentApiResponse<T> paymentRequiresAction(T data, String message) {
        PaymentApiResponse<T> response = new PaymentApiResponse<>();
        response.setSuccess(false);
        response.setCode(PAYMENT_REQUIRES_ACTION);
        response.setMessage(message);
        response.setData(data);
        response.setTimestamp(java.time.LocalDateTime.now());
        return response;
    }

    // Payment error responses
    public static <T> PaymentApiResponse<T> paymentFailed(String message) {
        PaymentApiResponse<T> response = new PaymentApiResponse<>();
        response.setSuccess(false);
        response.setCode(PAYMENT_FAILED);
        response.setMessage(message);
        response.setTimestamp(java.time.LocalDateTime.now());
        return response;
    }

    public static <T> PaymentApiResponse<T> paymentNotFound(String message) {
        PaymentApiResponse<T> response = new PaymentApiResponse<>();
        response.setSuccess(false);
        response.setCode(PAYMENT_NOT_FOUND);
        response.setMessage(message != null ? message : "Payment not found");
        response.setTimestamp(java.time.LocalDateTime.now());
        return response;
    }

    // Refund responses
    public static <T> PaymentApiResponse<T> refundSuccess(T data) {
        PaymentApiResponse<T> response = new PaymentApiResponse<>();
        response.setSuccess(true);
        response.setCode(REFUND_SUCCESS);
        response.setMessage("Refund processed successfully");
        response.setData(data);
        response.setTimestamp(java.time.LocalDateTime.now());
        return response;
    }

    public static <T> PaymentApiResponse<T> refundFailed(String message) {
        PaymentApiResponse<T> response = new PaymentApiResponse<>();
        response.setSuccess(false);
        response.setCode(REFUND_FAILED);
        response.setMessage(message);
        response.setTimestamp(java.time.LocalDateTime.now());
        return response;
    }

    // Payment method responses
    public static <T> PaymentApiResponse<T> paymentMethodSuccess(T data) {
        PaymentApiResponse<T> response = new PaymentApiResponse<>();
        response.setSuccess(true);
        response.setCode(PAYMENT_METHOD_SUCCESS);
        response.setMessage("Payment method operation successful");
        response.setData(data);
        response.setTimestamp(java.time.LocalDateTime.now());
        return response;
    }

    public static <T> PaymentApiResponse<T> paymentMethodNotFound(String message) {
        PaymentApiResponse<T> response = new PaymentApiResponse<>();
        response.setSuccess(false);
        response.setCode(PAYMENT_METHOD_NOT_FOUND);
        response.setMessage(message != null ? message : "Payment method not found");
        response.setTimestamp(java.time.LocalDateTime.now());
        return response;
    }

    // Stripe error responses
    public static <T> PaymentApiResponse<T> stripeError(String message) {
        PaymentApiResponse<T> response = new PaymentApiResponse<>();
        response.setSuccess(false);
        response.setCode(STRIPE_ERROR);
        response.setMessage("Stripe error: " + message);
        response.setTimestamp(java.time.LocalDateTime.now());
        return response;
    }

    // Webhook error responses
    public static <T> PaymentApiResponse<T> webhookError(String message) {
        PaymentApiResponse<T> response = new PaymentApiResponse<>();
        response.setSuccess(false);
        response.setCode(WEBHOOK_ERROR);
        response.setMessage("Webhook error: " + message);
        response.setTimestamp(java.time.LocalDateTime.now());
        return response;
    }
}
