package org.de013.paymentservice.constant;

import org.de013.common.constant.ApiPaths;

/**
 * Constants for Payment Service
 */
public final class PaymentConstants {

    // Private constructor to prevent instantiation
    private PaymentConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========== API Paths ==========
    public static final String API_V1_PAYMENTS = ApiPaths.API + ApiPaths.V1 + ApiPaths.PAYMENTS;
    public static final String API_V1_PAYMENT_METHODS = ApiPaths.API + ApiPaths.V1 + ApiPaths.PAYMENT_METHODS;
    public static final String API_V1_REFUNDS = ApiPaths.API + ApiPaths.V1 + ApiPaths.REFUNDS;
    public static final String API_V1_WEBHOOKS = ApiPaths.API + ApiPaths.V1 + ApiPaths.WEBHOOKS;

    // ========== Payment Messages ==========
    public static final String PAYMENT_PROCESSED = "Payment processed successfully";
    public static final String PAYMENT_CONFIRMED = "Payment confirmed successfully";
    public static final String PAYMENT_CANCELED = "Payment canceled successfully";
    public static final String PAYMENT_CAPTURED = "Payment captured successfully";
    public static final String PAYMENT_RETRIEVED = "Payment retrieved successfully";
    public static final String PAYMENT_NOT_FOUND = "Payment not found";
    public static final String PAYMENT_PROCESSING_FAILED = "Payment processing failed";

    // ========== Payment Method Messages ==========
    public static final String PAYMENT_METHOD_CREATED = "Payment method created successfully";
    public static final String PAYMENT_METHOD_UPDATED = "Payment method updated successfully";
    public static final String PAYMENT_METHOD_DELETED = "Payment method deleted successfully";
    public static final String PAYMENT_METHOD_RETRIEVED = "Payment method retrieved successfully";
    public static final String PAYMENT_METHOD_NOT_FOUND = "Payment method not found";
    public static final String PAYMENT_METHOD_SET_DEFAULT = "Payment method set as default successfully";
    public static final String PAYMENT_METHOD_ACTIVATED = "Payment method activated successfully";
    public static final String PAYMENT_METHOD_DEACTIVATED = "Payment method deactivated successfully";

    // ========== Refund Messages ==========
    public static final String REFUND_CREATED = "Refund created successfully";
    public static final String REFUND_PROCESSED = "Refund processed successfully";
    public static final String REFUND_CANCELED = "Refund canceled successfully";
    public static final String REFUND_APPROVED = "Refund approved successfully";
    public static final String REFUND_REJECTED = "Refund rejected successfully";
    public static final String REFUND_RETRIEVED = "Refund retrieved successfully";
    public static final String REFUND_NOT_FOUND = "Refund not found";
    public static final String REFUND_PROCESSING_FAILED = "Refund processing failed";

    // ========== Webhook Messages ==========
    public static final String WEBHOOK_PROCESSED = "Webhook processed successfully";
    public static final String WEBHOOK_SIGNATURE_INVALID = "Invalid webhook signature";
    public static final String WEBHOOK_PAYLOAD_INVALID = "Invalid webhook payload";
    public static final String WEBHOOK_PROCESSING_FAILED = "Webhook processing failed";

    // ========== Validation Messages ==========
    public static final String INVALID_PAYMENT_AMOUNT = "Payment amount must be greater than zero";
    public static final String INVALID_ORDER_ID = "Order ID is required";
    public static final String INVALID_USER_ID = "User ID is required";
    public static final String INVALID_PAYMENT_METHOD = "Payment method is required";
    public static final String INVALID_REFUND_AMOUNT = "Refund amount must be greater than zero";
    public static final String PAYMENT_CANNOT_BE_CANCELED = "Payment cannot be canceled in current status";
    public static final String PAYMENT_CANNOT_BE_CAPTURED = "Payment cannot be captured in current status";
    public static final String PAYMENT_CANNOT_BE_REFUNDED = "Payment cannot be refunded";

    // ========== Business Rules ==========
    public static final int MAX_REFUND_DAYS = 180;
    public static final int PAYMENT_METHOD_CLEANUP_DAYS = 90;
    public static final int WEBHOOK_RETRY_ATTEMPTS = 3;
    public static final long WEBHOOK_TIMEOUT_SECONDS = 30;

    // ========== Payment Status Messages ==========
    public static final String PAYMENT_STATUS_PENDING = "Payment is being processed";
    public static final String PAYMENT_STATUS_SUCCEEDED = "Payment completed successfully";
    public static final String PAYMENT_STATUS_FAILED = "Payment failed";
    public static final String PAYMENT_STATUS_CANCELED = "Payment was canceled";
    public static final String PAYMENT_STATUS_REQUIRES_ACTION = "Payment requires additional action";
    public static final String PAYMENT_STATUS_REQUIRES_CONFIRMATION = "Payment requires confirmation";

    // ========== Refund Status Messages ==========
    public static final String REFUND_STATUS_PENDING = "Refund is being processed";
    public static final String REFUND_STATUS_PROCESSING = "Refund is currently processing";
    public static final String REFUND_STATUS_SUCCEEDED = "Refund completed successfully";
    public static final String REFUND_STATUS_FAILED = "Refund failed";
    public static final String REFUND_STATUS_CANCELED = "Refund was canceled";
    public static final String REFUND_STATUS_REJECTED = "Refund was rejected";
    public static final String REFUND_STATUS_REQUIRES_ACTION = "Refund requires additional action";
}
