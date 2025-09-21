package org.de013.productcatalog.service;

import org.de013.productcatalog.dto.inventory.InventoryResponseDto;

public interface InventoryService {

    // Core CRUD Operations
    InventoryResponseDto getInventoryByProductId(Long productId);

    // Core Stock Management Operations
    InventoryResponseDto addStock(Long productId, Integer quantity);

    InventoryResponseDto removeStock(Long productId, Integer quantity);

    InventoryResponseDto setStock(Long productId, Integer quantity);

    // Core Stock Reservation Operations
    boolean reserveStock(Long productId, Integer quantity);

    boolean releaseReservedStock(Long productId, Integer quantity);

    boolean fulfillOrder(Long productId, Integer quantity);

    // Core Inventory Validation
    Integer getAvailableQuantity(Long productId);
}
