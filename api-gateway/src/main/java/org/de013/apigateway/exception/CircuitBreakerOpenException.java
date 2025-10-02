package org.de013.apigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when circuit breaker is open
 */
public class CircuitBreakerOpenException extends GatewayException {
    
    private static final String ERROR_CODE = "CIRCUIT_BREAKER_OPEN";
    
    public CircuitBreakerOpenException(String serviceName) {
        super(
            String.format("Circuit breaker is open for service '%s'. Service is temporarily unavailable", serviceName),
            HttpStatus.SERVICE_UNAVAILABLE,
            ERROR_CODE
        );
    }
}

