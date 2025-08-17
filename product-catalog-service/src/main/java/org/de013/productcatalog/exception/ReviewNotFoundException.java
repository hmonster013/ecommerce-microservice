package org.de013.productcatalog.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested review is not found.
 */
public class ReviewNotFoundException extends BaseBusinessException {

    private static final String ERROR_CODE = "REVIEW_NOT_FOUND";

    public ReviewNotFoundException(Long reviewId) {
        super(
            String.format("Review with ID %d not found", reviewId),
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            reviewId
        );
    }

    public ReviewNotFoundException(String message, Object... args) {
        super(message, ERROR_CODE, HttpStatus.NOT_FOUND, args);
    }

    public ReviewNotFoundException(String message, Throwable cause) {
        super(message, cause, ERROR_CODE, HttpStatus.NOT_FOUND);
    }
}
