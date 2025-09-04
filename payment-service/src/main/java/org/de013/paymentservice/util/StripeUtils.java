package org.de013.paymentservice.util;

import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Refund;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.entity.enums.RefundStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for Stripe-specific operations and conversions.
 */
@Slf4j
@Component
public class StripeUtils {
    
    /**
     * Converts Stripe PaymentIntent status to our PaymentStatus enum.
     */
    public static PaymentStatus convertStripePaymentStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return PaymentStatus.PENDING;
        }
        
        return switch (stripeStatus.toLowerCase()) {
            case "requires_payment_method" -> PaymentStatus.REQUIRES_PAYMENT_METHOD;
            case "requires_confirmation" -> PaymentStatus.REQUIRES_CONFIRMATION;
            case "requires_action" -> PaymentStatus.REQUIRES_ACTION;
            case "processing" -> PaymentStatus.PROCESSING;
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "canceled" -> PaymentStatus.CANCELED;
            default -> PaymentStatus.FAILED;
        };
    }
    
    /**
     * Converts our PaymentStatus enum to Stripe PaymentIntent status.
     */
    public static String convertToStripePaymentStatus(PaymentStatus paymentStatus) {
        if (paymentStatus == null) {
            return "requires_payment_method";
        }
        
        return switch (paymentStatus) {
            case REQUIRES_PAYMENT_METHOD -> "requires_payment_method";
            case REQUIRES_CONFIRMATION -> "requires_confirmation";
            case REQUIRES_ACTION -> "requires_action";
            case PROCESSING -> "processing";
            case SUCCEEDED -> "succeeded";
            case CANCELED -> "canceled";
            case FAILED -> "canceled"; // Map failed to canceled in Stripe
            default -> "requires_payment_method";
        };
    }
    
    /**
     * Converts Stripe Refund status to our RefundStatus enum.
     */
    public static RefundStatus convertStripeRefundStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return RefundStatus.PENDING;
        }
        
        return switch (stripeStatus.toLowerCase()) {
            case "pending" -> RefundStatus.PENDING;
            case "succeeded" -> RefundStatus.COMPLETED;
            case "failed" -> RefundStatus.FAILED;
            case "canceled" -> RefundStatus.CANCELED;
            case "requires_action" -> RefundStatus.PENDING;
            default -> RefundStatus.FAILED;
        };
    }
    
    /**
     * Converts amount from dollars to cents for Stripe API.
     */
    public static Long dollarsToCents(BigDecimal dollars) {
        if (dollars == null) {
            return null;
        }
        return CurrencyUtils.toSmallestUnit(dollars, "USD");
    }
    
    /**
     * Converts amount from cents to dollars from Stripe API.
     */
    public static BigDecimal centsToDollars(Long cents) {
        if (cents == null) {
            return null;
        }
        return CurrencyUtils.fromSmallestUnit(cents, "USD");
    }
    
    /**
     * Creates metadata map for Stripe objects.
     */
    public static Map<String, String> createMetadata(Long userId, Long orderId, String paymentNumber) {
        Map<String, String> metadata = new HashMap<>();
        
        if (userId != null) {
            metadata.put("user_id", userId.toString());
        }
        if (orderId != null) {
            metadata.put("order_id", orderId.toString());
        }
        if (paymentNumber != null) {
            metadata.put("payment_number", paymentNumber);
        }
        
        metadata.put("source", "ecommerce-platform");
        metadata.put("created_at", String.valueOf(System.currentTimeMillis()));
        
        return metadata;
    }
    
    /**
     * Extracts user ID from Stripe metadata.
     */
    public static Long extractUserIdFromMetadata(Map<String, String> metadata) {
        if (metadata == null || !metadata.containsKey("user_id")) {
            return null;
        }
        
        try {
            return Long.parseLong(metadata.get("user_id"));
        } catch (NumberFormatException e) {
            log.warn("Invalid user_id in metadata: {}", metadata.get("user_id"));
            return null;
        }
    }
    
    /**
     * Extracts order ID from Stripe metadata.
     */
    public static Long extractOrderIdFromMetadata(Map<String, String> metadata) {
        if (metadata == null || !metadata.containsKey("order_id")) {
            return null;
        }
        
        try {
            return Long.parseLong(metadata.get("order_id"));
        } catch (NumberFormatException e) {
            log.warn("Invalid order_id in metadata: {}", metadata.get("order_id"));
            return null;
        }
    }
    
    /**
     * Extracts payment number from Stripe metadata.
     */
    public static String extractPaymentNumberFromMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return null;
        }
        return metadata.get("payment_number");
    }
    
    /**
     * Checks if Stripe PaymentIntent is successful.
     */
    public static boolean isPaymentIntentSuccessful(PaymentIntent paymentIntent) {
        return paymentIntent != null && "succeeded".equals(paymentIntent.getStatus());
    }
    
    /**
     * Checks if Stripe PaymentIntent requires action.
     */
    public static boolean paymentIntentRequiresAction(PaymentIntent paymentIntent) {
        return paymentIntent != null && "requires_action".equals(paymentIntent.getStatus());
    }
    
    /**
     * Checks if Stripe PaymentIntent is cancelable.
     */
    public static boolean isPaymentIntentCancelable(PaymentIntent paymentIntent) {
        if (paymentIntent == null) {
            return false;
        }
        
        String status = paymentIntent.getStatus();
        return "requires_payment_method".equals(status) ||
               "requires_confirmation".equals(status) ||
               "requires_action".equals(status);
    }
    
    /**
     * Checks if Stripe Refund is successful.
     */
    public static boolean isRefundSuccessful(Refund refund) {
        return refund != null && "succeeded".equals(refund.getStatus());
    }
    
    /**
     * Gets the last 4 digits from a Stripe PaymentMethod card.
     */
    public static String getCardLast4(PaymentMethod paymentMethod) {
        if (paymentMethod == null || paymentMethod.getCard() == null) {
            return null;
        }
        return paymentMethod.getCard().getLast4();
    }
    
    /**
     * Gets the card brand from a Stripe PaymentMethod.
     */
    public static String getCardBrand(PaymentMethod paymentMethod) {
        if (paymentMethod == null || paymentMethod.getCard() == null) {
            return null;
        }
        return paymentMethod.getCard().getBrand();
    }
    
    /**
     * Gets the card expiry month from a Stripe PaymentMethod.
     */
    public static Long getCardExpiryMonth(PaymentMethod paymentMethod) {
        if (paymentMethod == null || paymentMethod.getCard() == null) {
            return null;
        }
        return paymentMethod.getCard().getExpMonth();
    }
    
    /**
     * Gets the card expiry year from a Stripe PaymentMethod.
     */
    public static Long getCardExpiryYear(PaymentMethod paymentMethod) {
        if (paymentMethod == null || paymentMethod.getCard() == null) {
            return null;
        }
        return paymentMethod.getCard().getExpYear();
    }
    
    /**
     * Formats Stripe error message for user display.
     */
    public static String formatStripeErrorMessage(String stripeErrorCode, String stripeErrorMessage) {
        if (stripeErrorCode == null) {
            return stripeErrorMessage != null ? stripeErrorMessage : "Payment processing failed";
        }
        
        return switch (stripeErrorCode) {
            case "card_declined" -> "Your card was declined. Please try a different payment method.";
            case "insufficient_funds" -> "Insufficient funds on your card. Please try a different payment method.";
            case "expired_card" -> "Your card has expired. Please try a different payment method.";
            case "incorrect_cvc" -> "The security code (CVC) is incorrect. Please check and try again.";
            case "processing_error" -> "A processing error occurred. Please try again.";
            case "rate_limit" -> "Too many requests. Please wait a moment and try again.";
            default -> stripeErrorMessage != null ? stripeErrorMessage : "Payment processing failed";
        };
    }
    
    /**
     * Checks if a Stripe error is retryable.
     */
    public static boolean isRetryableStripeError(String stripeErrorCode) {
        if (stripeErrorCode == null) {
            return false;
        }
        
        return switch (stripeErrorCode) {
            case "rate_limit", "processing_error", "api_connection_error" -> true;
            default -> false;
        };
    }
    
    /**
     * Validates Stripe webhook signature format.
     */
    public static boolean isValidWebhookSignature(String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        
        // Stripe signature format: t=timestamp,v1=signature
        return signature.matches("^t=\\d+,v1=[a-f0-9]+$");
    }
    
    /**
     * Extracts timestamp from Stripe webhook signature.
     */
    public static Long extractTimestampFromSignature(String signature) {
        if (!isValidWebhookSignature(signature)) {
            return null;
        }
        
        try {
            String timestampPart = signature.split(",")[0];
            return Long.parseLong(timestampPart.substring(2)); // Remove "t="
        } catch (Exception e) {
            log.warn("Failed to extract timestamp from signature: {}", signature);
            return null;
        }
    }
}
