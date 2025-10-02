package org.de013.apigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when user doesn't have permission to access a resource
 */
public class ForbiddenAccessException extends GatewayException {
    
    private static final String ERROR_CODE = "FORBIDDEN_ACCESS";
    
    public ForbiddenAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN, ERROR_CODE);
    }
    
    public ForbiddenAccessException(String message, String errorCode) {
        super(message, HttpStatus.FORBIDDEN, errorCode);
    }
    
    public static ForbiddenAccessException insufficientPermissions() {
        return new ForbiddenAccessException(
            "You don't have permission to access this resource",
            "INSUFFICIENT_PERMISSIONS"
        );
    }
    
    public static ForbiddenAccessException roleRequired(String role) {
        return new ForbiddenAccessException(
            String.format("Role '%s' is required to access this resource", role),
            "ROLE_REQUIRED"
        );
    }
}

