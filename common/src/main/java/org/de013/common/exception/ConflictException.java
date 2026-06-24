package org.de013.common.exception;

/**
 * Thrown when a request conflicts with the current state of a resource
 * (e.g. duplicate unique value, operation on a resource in an incompatible state).
 * Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
