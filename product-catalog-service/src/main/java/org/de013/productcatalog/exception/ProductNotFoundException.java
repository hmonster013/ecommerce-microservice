package org.de013.productcatalog.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested product is not found.
 */
public class ProductNotFoundException extends BaseBusinessException {

    private static final String ERROR_CODE = "PRODUCT_NOT_FOUND";
    private static final String DEFAULT_MESSAGE = "Product not found";

    public ProductNotFoundException(Long productId) {
        super(
            String.format("Product with ID %d not found", productId),
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            productId
        );
    }

    public ProductNotFoundException(String sku) {
        super(
            String.format("Product with SKU '%s' not found", sku),
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            sku
        );
    }

    public ProductNotFoundException(String message, Object... args) {
        super(message, ERROR_CODE, HttpStatus.NOT_FOUND, args);
    }

    public ProductNotFoundException(String message, Throwable cause) {
        super(message, cause, ERROR_CODE, HttpStatus.NOT_FOUND);
    }
}
