package org.de013.apigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a request to downstream service times out
 */
public class GatewayTimeoutException extends GatewayException {
    
    private static final String ERROR_CODE = "GATEWAY_TIMEOUT";
    
    public GatewayTimeoutException(String serviceName) {
        super(
            String.format("Request to service '%s' timed out", serviceName),
            HttpStatus.GATEWAY_TIMEOUT,
            ERROR_CODE
        );
    }
    
    public GatewayTimeoutException(String serviceName, Throwable cause) {
        super(
            String.format("Request to service '%s' timed out", serviceName),
            cause,
            HttpStatus.GATEWAY_TIMEOUT,
            ERROR_CODE
        );
    }
}

