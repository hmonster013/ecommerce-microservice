package org.de013.productcatalog.exception;

import org.springframework.http.HttpStatus;

public class InvalidStockQuantityException extends BaseBusinessException {
    public InvalidStockQuantityException(String message) {
        super(message, "INVALID_STOCK_QUANTITY", HttpStatus.BAD_REQUEST);
    }
}
