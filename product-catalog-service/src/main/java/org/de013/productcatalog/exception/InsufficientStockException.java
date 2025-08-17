package org.de013.productcatalog.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when there is insufficient stock for a requested operation.
 */
public class InsufficientStockException extends BaseBusinessException {

    private static final String ERROR_CODE = "INSUFFICIENT_STOCK";

    public InsufficientStockException(Long productId, Integer requested, Integer available) {
        super(
            String.format("Insufficient stock for product %d. Requested: %d, Available: %d", 
                         productId, requested, available),
            ERROR_CODE,
            HttpStatus.CONFLICT,
            productId, requested, available
        );
    }

    public InsufficientStockException(String productSku, Integer requested, Integer available) {
        super(
            String.format("Insufficient stock for product '%s'. Requested: %d, Available: %d", 
                         productSku, requested, available),
            ERROR_CODE,
            HttpStatus.CONFLICT,
            productSku, requested, available
        );
    }

    public InsufficientStockException(String message, Object... args) {
        super(message, ERROR_CODE, HttpStatus.CONFLICT, args);
    }

    public InsufficientStockException(String message, Throwable cause) {
        super(message, cause, ERROR_CODE, HttpStatus.CONFLICT);
    }
}
