package org.de013.common.exception;

/**
 * Exception thrown when user is not authenticated
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create exception for missing authentication token
     */
    public static UnauthorizedException tokenMissing() {
        return new UnauthorizedException("Authentication token is required");
    }

    /**
     * Create exception for invalid authentication token
     */
    public static UnauthorizedException tokenInvalid() {
        return new UnauthorizedException("Authentication token is invalid");
    }

    /**
     * Create exception for expired authentication token
     */
    public static UnauthorizedException tokenExpired() {
        return new UnauthorizedException("Authentication token has expired");
    }

    /**
     * Create exception for authentication required
     */
    public static UnauthorizedException authenticationRequired() {
        return new UnauthorizedException("Authentication is required to access this resource");
    }
}
