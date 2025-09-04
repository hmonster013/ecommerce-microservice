package org.de013.paymentservice.exception;

import lombok.extern.slf4j.Slf4j;
// Local StripeException
import org.springframework.stereotype.Component;

/**
 * Utility class for mapping Stripe exceptions to our custom exceptions.
 * This provides a centralized way to handle Stripe API errors.
 */
@Slf4j
@Component
public class StripeExceptionMapper {

    /**
     * Maps a Stripe exception to our custom StripeException.
     */
    public StripeException mapStripeException(com.stripe.exception.StripeException stripeEx) {
        return mapStripeException(stripeEx, null, null);
    }

    /**
     * Maps a Stripe exception to our custom StripeException with payment context.
     */
    public StripeException mapStripeException(com.stripe.exception.StripeException stripeEx, 
                                            String paymentId, String operation) {
        log.error("Stripe API error - Code: {}, Type: {}, Message: {}, RequestId: {}", 
                stripeEx.getCode(), stripeEx.getClass().getSimpleName(), 
                stripeEx.getMessage(), stripeEx.getRequestId());

        // Handle specific Stripe exception types
        if (stripeEx instanceof com.stripe.exception.CardException) {
            return handleCardException((com.stripe.exception.CardException) stripeEx, paymentId, operation);
        } else if (stripeEx instanceof com.stripe.exception.RateLimitException) {
            return handleRateLimitException((com.stripe.exception.RateLimitException) stripeEx);
        } else if (stripeEx instanceof com.stripe.exception.InvalidRequestException) {
            return handleInvalidRequestException((com.stripe.exception.InvalidRequestException) stripeEx, paymentId, operation);
        } else if (stripeEx instanceof com.stripe.exception.AuthenticationException) {
            return handleAuthenticationException((com.stripe.exception.AuthenticationException) stripeEx);
        } else if (stripeEx instanceof com.stripe.exception.ApiConnectionException) {
            return handleApiConnectionException((com.stripe.exception.ApiConnectionException) stripeEx, paymentId, operation);
        } else if (stripeEx instanceof com.stripe.exception.ApiException) {
            return handleApiException((com.stripe.exception.ApiException) stripeEx, paymentId, operation);
        } else if (stripeEx instanceof com.stripe.exception.SignatureVerificationException) {
            return handleSignatureVerificationException((com.stripe.exception.SignatureVerificationException) stripeEx);
        }

        // Generic Stripe exception
        return new StripeException(
            stripeEx.getMessage(),
            paymentId,
            operation,
            stripeEx.getCode(),
            stripeEx.getClass().getSimpleName(),
            stripeEx
        );
    }

    private StripeException handleCardException(com.stripe.exception.CardException cardEx, 
                                              String paymentId, String operation) {
        String code = cardEx.getCode();
        String declineCode = cardEx.getDeclineCode();
        
        if ("card_declined".equals(code)) {
            if ("insufficient_funds".equals(declineCode)) {
                return StripeException.insufficientFunds(paymentId);
            } else if ("expired_card".equals(declineCode)) {
                return StripeException.expiredCard(paymentId);
            } else {
                return StripeException.cardDeclined(paymentId, declineCode != null ? declineCode : "unknown");
            }
        } else if ("incorrect_cvc".equals(code)) {
            return StripeException.invalidCvc(paymentId);
        } else if ("expired_card".equals(code)) {
            return StripeException.expiredCard(paymentId);
        }

        return new StripeException(
            cardEx.getMessage(),
            paymentId,
            operation,
            code,
            "card_error",
            cardEx
        );
    }

    private StripeException handleRateLimitException(com.stripe.exception.RateLimitException rateLimitEx) {
        return StripeException.rateLimitExceeded();
    }

    private StripeException handleInvalidRequestException(com.stripe.exception.InvalidRequestException invalidEx, 
                                                        String paymentId, String operation) {
        return new StripeException(
            "Invalid request to Stripe API: " + invalidEx.getMessage(),
            paymentId,
            operation,
            "invalid_request",
            "validation_error",
            invalidEx
        );
    }

    private StripeException handleAuthenticationException(com.stripe.exception.AuthenticationException authEx) {
        return StripeException.authenticationFailed();
    }

    private StripeException handleApiConnectionException(com.stripe.exception.ApiConnectionException connEx, 
                                                       String paymentId, String operation) {
        return new StripeException(
            "Failed to connect to Stripe API: " + connEx.getMessage(),
            paymentId,
            operation,
            "api_connection_error",
            "api_connection_error",
            connEx
        );
    }

    private StripeException handleApiException(com.stripe.exception.ApiException apiEx, 
                                             String paymentId, String operation) {
        return new StripeException(
            "Stripe API error: " + apiEx.getMessage(),
            paymentId,
            operation,
            "api_error",
            "api_error",
            apiEx
        );
    }

    private StripeException handleSignatureVerificationException(com.stripe.exception.SignatureVerificationException sigEx) {
        return StripeException.webhookSignatureInvalid();
    }

    /**
     * Determines if a Stripe exception is retryable.
     */
    public boolean isRetryable(com.stripe.exception.StripeException stripeEx) {
        // Rate limit exceptions are retryable
        if (stripeEx instanceof com.stripe.exception.RateLimitException) {
            return true;
        }
        
        // API connection exceptions are retryable
        if (stripeEx instanceof com.stripe.exception.ApiConnectionException) {
            return true;
        }
        
        // Some API exceptions are retryable (5xx errors)
        if (stripeEx instanceof com.stripe.exception.ApiException) {
            Integer statusCode = stripeEx.getStatusCode();
            return statusCode != null && statusCode >= 500;
        }
        
        return false;
    }

    /**
     * Gets the recommended retry delay in milliseconds.
     */
    public long getRetryDelay(com.stripe.exception.StripeException stripeEx) {
        if (stripeEx instanceof com.stripe.exception.RateLimitException) {
            // For rate limit exceptions, use exponential backoff starting at 1 second
            return 1000L;
        }
        
        if (stripeEx instanceof com.stripe.exception.ApiConnectionException) {
            // For connection exceptions, use a shorter delay
            return 500L;
        }
        
        // Default retry delay
        return 1000L;
    }
}
