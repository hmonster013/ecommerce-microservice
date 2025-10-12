package org.de013.apigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when downstream service returns an invalid response
 */
public class BadGatewayException extends GatewayException {
    
    private static final String ERROR_CODE = "BAD_GATEWAY";
    
    public BadGatewayException(String serviceName) {
        super(
            String.format("Invalid response from service '%s'", serviceName),
            HttpStatus.BAD_GATEWAY,
            ERROR_CODE
        );
    }
    
    public BadGatewayException(String serviceName, Throwable cause) {
        super(
            String.format("Invalid response from service '%s'", serviceName),
            cause,
            HttpStatus.BAD_GATEWAY,
            ERROR_CODE
        );
    }
}

