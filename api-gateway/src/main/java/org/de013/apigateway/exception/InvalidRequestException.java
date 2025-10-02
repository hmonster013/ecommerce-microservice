package org.de013.apigateway.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when request is invalid
 */
public class InvalidRequestException extends GatewayException {
    
    private static final String ERROR_CODE = "INVALID_REQUEST";
    
    public InvalidRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }
    
    public InvalidRequestException(String message, String errorCode) {
        super(message, HttpStatus.BAD_REQUEST, errorCode);
    }
    
    public static InvalidRequestException missingHeader(String headerName) {
        return new InvalidRequestException(
            String.format("Required header '%s' is missing", headerName),
            "MISSING_HEADER"
        );
    }
    
    public static InvalidRequestException invalidHeader(String headerName) {
        return new InvalidRequestException(
            String.format("Header '%s' has invalid value", headerName),
            "INVALID_HEADER"
        );
    }
    
    public static InvalidRequestException missingParameter(String paramName) {
        return new InvalidRequestException(
            String.format("Required parameter '%s' is missing", paramName),
            "MISSING_PARAMETER"
        );
    }
}

