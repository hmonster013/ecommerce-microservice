package org.de013.common.exception;

/**
 * Exception thrown for business logic violations
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create exception for invalid operation
     */
    public static BusinessException invalidOperation(String operation) {
        return new BusinessException(
            String.format("Invalid operation: %s", operation)
        );
    }

    /**
     * Create exception for business rule violation
     */
    public static BusinessException ruleViolation(String rule) {
        return new BusinessException(
            String.format("Business rule violation: %s", rule)
        );
    }

    /**
     * Create exception for invalid state
     */
    public static BusinessException invalidState(String state) {
        return new BusinessException(
            String.format("Invalid state: %s", state)
        );
    }
}
