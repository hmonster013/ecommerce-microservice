package org.de013.productcatalog.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for all business exceptions in the product catalog service.
 * Provides common functionality for error handling and HTTP status mapping.
 */
@Getter
public abstract class BaseBusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final Object[] args;

    protected BaseBusinessException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.args = new Object[0];
    }

    protected BaseBusinessException(String message, String errorCode, HttpStatus httpStatus, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.args = args;
    }

    protected BaseBusinessException(String message, Throwable cause, String errorCode, HttpStatus httpStatus) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.args = new Object[0];
    }

    protected BaseBusinessException(String message, Throwable cause, String errorCode, HttpStatus httpStatus, Object... args) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.args = args;
    }

    /**
     * Get the error code for this exception
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Get the HTTP status code for this exception
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * Get additional arguments for error message formatting
     */
    public Object[] getArgs() {
        return args;
    }

    /**
     * Check if this exception should be logged as an error
     */
    public boolean shouldLogAsError() {
        return httpStatus.is5xxServerError();
    }

    /**
     * Check if this exception should be logged as a warning
     */
    public boolean shouldLogAsWarning() {
        return httpStatus.is4xxClientError();
    }
}
