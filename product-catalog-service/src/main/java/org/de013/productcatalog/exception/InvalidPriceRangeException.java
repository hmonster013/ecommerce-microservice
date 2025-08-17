package org.de013.productcatalog.exception;

import org.springframework.http.HttpStatus;
import java.math.BigDecimal;

/**
 * Exception thrown when an invalid price range is provided.
 */
public class InvalidPriceRangeException extends BaseBusinessException {

    private static final String ERROR_CODE = "INVALID_PRICE_RANGE";

    public InvalidPriceRangeException(BigDecimal minPrice, BigDecimal maxPrice) {
        super(
            String.format("Invalid price range: min price (%s) cannot be greater than max price (%s)", 
                         minPrice, maxPrice),
            ERROR_CODE,
            HttpStatus.BAD_REQUEST,
            minPrice, maxPrice
        );
    }

    public InvalidPriceRangeException(String message, Object... args) {
        super(message, ERROR_CODE, HttpStatus.BAD_REQUEST, args);
    }

    public InvalidPriceRangeException(String message, Throwable cause) {
        super(message, cause, ERROR_CODE, HttpStatus.BAD_REQUEST);
    }
}
