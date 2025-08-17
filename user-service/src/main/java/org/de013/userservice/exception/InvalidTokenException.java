package org.de013.userservice.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Exception thrown when JWT token is invalid, expired, or malformed
 */
public class InvalidTokenException extends AuthenticationException {
    
    public InvalidTokenException(String message) {
        super(message);
    }
    
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static InvalidTokenException expired() {
        return new InvalidTokenException("JWT token has expired");
    }
    
    public static InvalidTokenException malformed() {
        return new InvalidTokenException("JWT token is malformed");
    }
    
    public static InvalidTokenException invalid() {
        return new InvalidTokenException("JWT token is invalid");
    }
    
    public static InvalidTokenException unsupported() {
        return new InvalidTokenException("JWT token is unsupported");
    }
    
    public static InvalidTokenException empty() {
        return new InvalidTokenException("JWT token is empty or null");
    }
    
    public static InvalidTokenException signatureInvalid() {
        return new InvalidTokenException("JWT token signature is invalid");
    }
}
