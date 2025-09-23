package org.de013.productcatalog.service;

import org.de013.common.dto.InventoryDto;

public interface InventoryService {

    // Core CRUD Operations
    InventoryDto getInventoryByProductId(Long productId);

    // Core Stock Management Operations
    InventoryDto addStock(Long productId, Integer quantity);

    InventoryDto removeStock(Long productId, Integer quantity);

    InventoryDto setStock(Long productId, Integer quantity);

    // Core Stock Reservation Operations
    boolean reserveStock(Long productId, Integer quantity);

    boolean releaseReservedStock(Long productId, Integer quantity);

    boolean fulfillOrder(Long productId, Integer quantity);

    // Core Inventory Validation
    Integer getAvailableQuantity(Long productId);
}
