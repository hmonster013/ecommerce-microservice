package org.de013.productcatalog.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to create a product with a duplicate SKU.
 */
public class DuplicateSkuException extends BaseBusinessException {

    private static final String ERROR_CODE = "DUPLICATE_SKU";

    public DuplicateSkuException(String sku) {
        super(
            String.format("Product with SKU '%s' already exists", sku),
            ERROR_CODE,
            HttpStatus.CONFLICT,
            sku
        );
    }

    public DuplicateSkuException(String sku, Long existingProductId) {
        super(
            String.format("Product with SKU '%s' already exists (Product ID: %d)", sku, existingProductId),
            ERROR_CODE,
            HttpStatus.CONFLICT,
            sku, existingProductId
        );
    }

    public DuplicateSkuException(String message, Object... args) {
        super(message, ERROR_CODE, HttpStatus.CONFLICT, args);
    }

    public DuplicateSkuException(String message, Throwable cause) {
        super(message, cause, ERROR_CODE, HttpStatus.CONFLICT);
    }
}
