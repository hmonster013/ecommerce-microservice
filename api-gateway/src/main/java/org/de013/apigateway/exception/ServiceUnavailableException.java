package org.de013.apigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a downstream service is unavailable
 */
public class ServiceUnavailableException extends GatewayException {
    
    private static final String ERROR_CODE = "SERVICE_UNAVAILABLE";
    
    public ServiceUnavailableException(String serviceName) {
        super(
            String.format("Service '%s' is currently unavailable", serviceName),
            HttpStatus.SERVICE_UNAVAILABLE,
            ERROR_CODE
        );
    }
    
    public ServiceUnavailableException(String serviceName, Throwable cause) {
        super(
            String.format("Service '%s' is currently unavailable", serviceName),
            cause,
            HttpStatus.SERVICE_UNAVAILABLE,
            ERROR_CODE
        );
    }
}

