package org.de013.apigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when user is not authorized to access a resource
 */
public class UnauthorizedAccessException extends GatewayException {
    
    private static final String ERROR_CODE = "UNAUTHORIZED_ACCESS";
    
    public UnauthorizedAccessException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, ERROR_CODE);
    }
    
    public UnauthorizedAccessException(String message, String errorCode) {
        super(message, HttpStatus.UNAUTHORIZED, errorCode);
    }
    
    public static UnauthorizedAccessException tokenMissing() {
        return new UnauthorizedAccessException(
            "Authentication token is required",
            "TOKEN_MISSING"
        );
    }
    
    public static UnauthorizedAccessException tokenInvalid() {
        return new UnauthorizedAccessException(
            "Authentication token is invalid",
            "TOKEN_INVALID"
        );
    }
    
    public static UnauthorizedAccessException tokenExpired() {
        return new UnauthorizedAccessException(
            "Authentication token has expired",
            "TOKEN_EXPIRED"
        );
    }
}

