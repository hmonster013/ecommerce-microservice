package org.de013.apigateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for API Gateway specific errors
 */
@Getter
public class GatewayException extends RuntimeException {
    
    private final HttpStatus httpStatus;
    private final String errorCode;
    
    public GatewayException(String message, HttpStatus httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
    
    public GatewayException(String message, Throwable cause, HttpStatus httpStatus, String errorCode) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}

