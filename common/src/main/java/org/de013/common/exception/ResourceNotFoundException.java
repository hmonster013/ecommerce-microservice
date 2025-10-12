package org.de013.common.exception;

/**
 * Exception thrown when a requested resource is not found
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create exception with resource, field and value
     */
    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s not found with %s: %s", resource, field, value));
    }

    /**
     * Create exception for resource not found by ID
     */
    public static ResourceNotFoundException byId(String resource, Object id) {
        return new ResourceNotFoundException(resource, "id", id);
    }

    /**
     * Create exception for resource not found
     */
    public static ResourceNotFoundException notFound(String resource) {
        return new ResourceNotFoundException(String.format("%s not found", resource));
    }
}
