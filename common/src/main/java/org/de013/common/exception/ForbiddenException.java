package org.de013.common.exception;

/**
 * Exception thrown when user is authenticated but doesn't have required permissions
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create exception for insufficient permissions
     */
    public static ForbiddenException insufficientPermissions() {
        return new ForbiddenException("You don't have permission to access this resource");
    }

    /**
     * Create exception for required role
     */
    public static ForbiddenException roleRequired(String role) {
        return new ForbiddenException(
            String.format("Role '%s' is required to access this resource", role)
        );
    }

    /**
     * Create exception for resource access denied
     */
    public static ForbiddenException accessDenied(String resource) {
        return new ForbiddenException(
            String.format("Access denied to resource: %s", resource)
        );
    }
}
