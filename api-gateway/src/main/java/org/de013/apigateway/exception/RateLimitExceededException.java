package org.de013.apigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when rate limit is exceeded
 */
public class RateLimitExceededException extends GatewayException {
    
    private static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";
    
    public RateLimitExceededException() {
        super(
            "Rate limit exceeded. Please try again later",
            HttpStatus.TOO_MANY_REQUESTS,
            ERROR_CODE
        );
    }
    
    public RateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, ERROR_CODE);
    }
    
    public RateLimitExceededException(int limit, String timeWindow) {
        super(
            String.format("Rate limit of %d requests per %s exceeded", limit, timeWindow),
            HttpStatus.TOO_MANY_REQUESTS,
            ERROR_CODE
        );
    }
}

