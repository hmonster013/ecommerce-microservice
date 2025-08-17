package org.de013.userservice.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * Exception thrown when user provides invalid credentials during authentication
 */
public class InvalidCredentialsException extends AuthenticationException {
    
    public InvalidCredentialsException(String message) {
        super(message);
    }
    
    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static InvalidCredentialsException invalidEmailOrPassword() {
        return new InvalidCredentialsException("Invalid email or password");
    }
    
    public static InvalidCredentialsException accountLocked() {
        return new InvalidCredentialsException("Account is locked");
    }
    
    public static InvalidCredentialsException accountDisabled() {
        return new InvalidCredentialsException("Account is disabled");
    }
    
    public static InvalidCredentialsException accountExpired() {
        return new InvalidCredentialsException("Account has expired");
    }
}
