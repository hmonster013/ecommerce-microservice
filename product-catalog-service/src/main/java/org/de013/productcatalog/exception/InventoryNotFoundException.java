package org.de013.productcatalog.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when inventory record is not found for a product.
 */
public class InventoryNotFoundException extends BaseBusinessException {

    private static final String ERROR_CODE = "INVENTORY_NOT_FOUND";

    public InventoryNotFoundException(Long productId) {
        super(
            String.format("Inventory not found for product ID %d", productId),
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            productId
        );
    }

    public InventoryNotFoundException(String message, Object... args) {
        super(message, ERROR_CODE, HttpStatus.NOT_FOUND, args);
    }

    public InventoryNotFoundException(String message, Throwable cause) {
        super(message, cause, ERROR_CODE, HttpStatus.NOT_FOUND);
    }
}
