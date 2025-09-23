package org.de013.productcatalog.mapper;

import org.de013.common.dto.VariantInventoryDto;
import org.de013.productcatalog.entity.VariantInventory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class VariantInventoryMapper {

    public VariantInventoryDto toVariantInventoryDto(VariantInventory variantInventory) {
        if (variantInventory == null) {
            return null;
        }

        return VariantInventoryDto.builder()
                .id(variantInventory.getId())
                .variantId(variantInventory.getVariant() != null ? variantInventory.getVariant().getId() : null)
                .productId(variantInventory.getProduct() != null ? variantInventory.getProduct().getId() : null)
                .variantName(variantInventory.getVariant() != null ? variantInventory.getVariant().getName() : null)
                .variantValue(variantInventory.getVariant() != null ? variantInventory.getVariant().getValue() : null)
                .variantType(variantInventory.getVariant() != null && variantInventory.getVariant().getVariantType() != null ?
                           variantInventory.getVariant().getVariantType().name() : null)
                .quantity(variantInventory.getQuantity())
                .reservedQuantity(variantInventory.getReservedQuantity())
                .availableQuantity(variantInventory.getAvailableQuantity())
                .minStockLevel(variantInventory.getMinStockLevel())
                .maxStockLevel(variantInventory.getMaxStockLevel())
                .reorderPoint(variantInventory.getReorderPoint())
                .reorderQuantity(variantInventory.getReorderQuantity())
                .trackInventory(variantInventory.getTrackInventory())
                .allowBackorder(variantInventory.getAllowBackorder())
                .location(variantInventory.getLocation())
                .sku(variantInventory.getSku())
                .stockStatus(variantInventory.getStockStatus())
                .inStock(variantInventory.isInStock())
                .outOfStock(variantInventory.isOutOfStock())
                .lowStock(variantInventory.isLowStock())
                .needsReorder(variantInventory.needsReorder())
                .canFulfillOrders(variantInventory.canFulfillOrder(1))
                .createdAt(variantInventory.getCreatedAt())
                .updatedAt(variantInventory.getUpdatedAt())
                .createdBy(variantInventory.getCreatedBy())
                .updatedBy(variantInventory.getUpdatedBy())
                .build();
    }

    public List<VariantInventoryDto> toVariantInventoryDtoList(List<VariantInventory> variantInventories) {
        if (variantInventories == null) {
            return null;
        }

        return variantInventories.stream()
                .map(this::toVariantInventoryDto)
                .collect(Collectors.toList());
    }

    public VariantInventory toVariantInventory(VariantInventoryDto variantInventoryDto) {
        if (variantInventoryDto == null) {
            return null;
        }

        return VariantInventory.builder()
                .id(variantInventoryDto.getId())
                .quantity(variantInventoryDto.getQuantity())
                .reservedQuantity(variantInventoryDto.getReservedQuantity())
                .minStockLevel(variantInventoryDto.getMinStockLevel())
                .maxStockLevel(variantInventoryDto.getMaxStockLevel())
                .reorderPoint(variantInventoryDto.getReorderPoint())
                .reorderQuantity(variantInventoryDto.getReorderQuantity())
                .trackInventory(variantInventoryDto.getTrackInventory())
                .allowBackorder(variantInventoryDto.getAllowBackorder())
                .location(variantInventoryDto.getLocation())
                .sku(variantInventoryDto.getSku())
                .build();
    }

    public void updateVariantInventoryFromDto(VariantInventoryDto dto, VariantInventory entity) {
        if (dto == null || entity == null) {
            return;
        }

        // Only update non-null fields from DTO
        if (dto.getMinStockLevel() != null) {
            entity.setMinStockLevel(dto.getMinStockLevel());
        }
        if (dto.getMaxStockLevel() != null) {
            entity.setMaxStockLevel(dto.getMaxStockLevel());
        }
        if (dto.getReorderPoint() != null) {
            entity.setReorderPoint(dto.getReorderPoint());
        }
        if (dto.getReorderQuantity() != null) {
            entity.setReorderQuantity(dto.getReorderQuantity());
        }
        if (dto.getTrackInventory() != null) {
            entity.setTrackInventory(dto.getTrackInventory());
        }
        if (dto.getAllowBackorder() != null) {
            entity.setAllowBackorder(dto.getAllowBackorder());
        }
        if (dto.getLocation() != null) {
            entity.setLocation(dto.getLocation());
        }
        if (dto.getSku() != null) {
            entity.setSku(dto.getSku());
        }
        // Note: quantity and reservedQuantity are intentionally not updated here
        // They should be updated through specific service methods
    }
}
