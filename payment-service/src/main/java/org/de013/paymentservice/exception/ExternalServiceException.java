package org.de013.paymentservice.exception;

/**
 * Exception thrown when external service calls fail
 */
public class ExternalServiceException extends RuntimeException {
    
    public ExternalServiceException(String message) {
        super(message);
    }
    
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
