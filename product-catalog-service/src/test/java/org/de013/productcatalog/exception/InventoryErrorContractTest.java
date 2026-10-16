package org.de013.productcatalog.exception;

import org.de013.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryErrorContractTest {
    private final ProductCatalogExceptionHandler handler = new ProductCatalogExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/products/1/inventory/set");

    @Test
    void invalidQuantityUses400InsteadOfGeneric500() {
        var response = handler.handleBusinessException(new InvalidStockQuantityException("Quantity must be positive"), request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_STOCK_QUANTITY", response.getBody().getCode());
    }

    @Test
    void reservedStockConflictUses409() {
        var response = handler.handleBusinessException(new InsufficientStockException("Cannot remove reserved stock"), request);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("INSUFFICIENT_STOCK", response.getBody().getCode());
    }

    @Test
    void missingInventoryUses404() {
        var response = handler.handleSharedResourceNotFoundException(new ResourceNotFoundException("Inventory not found"), request);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
