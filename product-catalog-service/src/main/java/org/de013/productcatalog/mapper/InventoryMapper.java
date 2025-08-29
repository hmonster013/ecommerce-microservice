package org.de013.productcatalog.mapper;

import org.de013.productcatalog.dto.inventory.InventoryResponseDto;
import org.de013.productcatalog.entity.Inventory;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public InventoryResponseDto toInventoryResponseDto(Inventory inventory) {
        if (inventory == null) return null;

        return InventoryResponseDto.builder()
                .id(inventory.getId())
                .productId(inventory.getProduct().getId())
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(inventory.getAvailableQuantity())
                .minStockLevel(inventory.getMinStockLevel())
                .maxStockLevel(inventory.getMaxStockLevel())
                .reorderPoint(inventory.getReorderPoint())
                .reorderQuantity(inventory.getReorderQuantity())
                .trackInventory(inventory.getTrackInventory())
                .allowBackorder(inventory.getAllowBackorder())
                .location(inventory.getLocation())
                .supplierSku(inventory.getSupplierSku())
                .inStock(inventory.isInStock())
                .lowStock(inventory.isLowStock())
                .needsReorder(inventory.needsReorder())
                .stockStatus(inventory.getStockStatus())
                .build();
    }
}

