package org.de013.apigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when JWT token is invalid or expired
 */
public class InvalidTokenException extends GatewayException {
    
    private static final String ERROR_CODE = "INVALID_TOKEN";
    
    public InvalidTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, ERROR_CODE);
    }
    
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNAUTHORIZED, ERROR_CODE);
    }
    
    public static InvalidTokenException expired() {
        return new InvalidTokenException("JWT token has expired");
    }
    
    public static InvalidTokenException malformed() {
        return new InvalidTokenException("JWT token is malformed");
    }
    
    public static InvalidTokenException blacklisted() {
        return new InvalidTokenException("JWT token has been revoked");
    }
    
    public static InvalidTokenException missing() {
        return new InvalidTokenException("JWT token is missing");
    }
}

