package org.de013.userservice.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Exception thrown during JWT authentication process
 */
public class JwtAuthenticationException extends AuthenticationException {
    
    public JwtAuthenticationException(String message) {
        super(message);
    }
    
    public JwtAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static JwtAuthenticationException tokenMissing() {
        return new JwtAuthenticationException("JWT token is missing from request");
    }
    
    public static JwtAuthenticationException invalidFormat() {
        return new JwtAuthenticationException("JWT token format is invalid");
    }
    
    public static JwtAuthenticationException userNotFound() {
        return new JwtAuthenticationException("User not found for the provided token");
    }
}
