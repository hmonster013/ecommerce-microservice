package org.de013.productcatalog.mapper;

import org.de013.common.dto.InventoryDto;
import org.de013.productcatalog.entity.Inventory;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public InventoryDto toInventoryDto(Inventory inventory) {
        if (inventory == null) return null;

        return InventoryDto.builder()
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
                .stockStatus(inventory.getStockStatus())
                .inStock(inventory.isInStock())
                .lowStock(inventory.isLowStock())
                .needsReorder(inventory.needsReorder())
                .canFulfillOrders(inventory.canFulfillOrder(1))
                .build();
    }

    // Backward compatibility method
    public InventoryDto toInventoryResponseDto(Inventory inventory) {
        return toInventoryDto(inventory);
    }
}

