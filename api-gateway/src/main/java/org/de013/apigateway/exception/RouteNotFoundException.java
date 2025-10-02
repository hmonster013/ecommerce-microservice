package org.de013.apigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when no route is found for the requested path
 */
public class RouteNotFoundException extends GatewayException {
    
    private static final String ERROR_CODE = "ROUTE_NOT_FOUND";
    
    public RouteNotFoundException(String path) {
        super(
            String.format("No route found for path: %s", path),
            HttpStatus.NOT_FOUND,
            ERROR_CODE
        );
    }
    
    public RouteNotFoundException(String path, String method) {
        super(
            String.format("No route found for %s %s", method, path),
            HttpStatus.NOT_FOUND,
            ERROR_CODE
        );
    }
}

