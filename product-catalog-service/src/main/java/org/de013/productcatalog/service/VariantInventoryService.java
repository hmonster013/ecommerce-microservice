package org.de013.productcatalog.service;

import org.de013.productcatalog.dto.inventory.VariantInventoryCreateDto;
import org.de013.productcatalog.dto.inventory.VariantInventoryDto;
import org.de013.productcatalog.dto.inventory.VariantInventoryUpdateDto;
import org.de013.productcatalog.entity.enums.VariantType;

import java.util.List;

public interface VariantInventoryService {

    // CRUD Operations
    VariantInventoryDto createVariantInventory(VariantInventoryCreateDto createDto);
    
    VariantInventoryDto updateVariantInventory(Long variantId, VariantInventoryUpdateDto updateDto);
    
    void deleteVariantInventory(Long variantId);
    
    VariantInventoryDto getVariantInventoryByVariantId(Long variantId);
    
    VariantInventoryDto getVariantInventoryBySku(String sku);

    // Product-level Operations
    List<VariantInventoryDto> getVariantInventoriesByProductId(Long productId);
    
    List<VariantInventoryDto> getInStockVariantsByProductId(Long productId);
    
    List<VariantInventoryDto> getOutOfStockVariantsByProductId(Long productId);
    
    List<VariantInventoryDto> getLowStockVariantsByProductId(Long productId);
    
    List<VariantInventoryDto> getVariantsNeedingReorderByProductId(Long productId);

    // Variant Type Operations
    List<VariantInventoryDto> getVariantInventoriesByProductIdAndType(Long productId, VariantType variantType);
    
    List<VariantInventoryDto> getInStockVariantsByProductIdAndType(Long productId, VariantType variantType);

    // Stock Management Operations
    VariantInventoryDto addStock(Long variantId, Integer quantity);
    
    VariantInventoryDto removeStock(Long variantId, Integer quantity);
    
    VariantInventoryDto setStock(Long variantId, Integer quantity);

    // Stock Reservation Operations
    boolean reserveStock(Long variantId, Integer quantity);
    
    boolean releaseReservedStock(Long variantId, Integer quantity);
    
    boolean fulfillOrder(Long variantId, Integer quantity);

    // Availability Checks
    boolean isVariantInStock(Long variantId);
    
    boolean canFulfillOrder(Long variantId, Integer quantity);
    
    Integer getAvailableQuantity(Long variantId);
    
    boolean hasAnyVariantInStock(Long productId);

    // Bulk Operations
    void bulkAddStock(List<Long> variantIds, Integer quantity);
    
    void bulkSetStock(List<Long> variantIds, Integer quantity);
    
    List<VariantInventoryDto> bulkReserveStock(List<Long> variantIds, List<Integer> quantities);

    // Statistics Operations
    Long getTotalQuantityByProductId(Long productId);
    
    Long getTotalAvailableQuantityByProductId(Long productId);
    
    Long countInStockVariantsByProductId(Long productId);
    
    Long countOutOfStockVariantsByProductId(Long productId);

    // Location Operations
    List<VariantInventoryDto> getVariantInventoriesByLocation(String location);
    
    List<String> getAllLocations();
    
    void updateVariantLocation(Long variantId, String location);

    // Advanced Operations
    List<VariantInventoryDto> findVariantsThatCanFulfill(Long productId, Integer requestedQuantity);
    
    VariantInventoryDto findBestVariantForOrder(Long productId, VariantType variantType, String preferredValue, Integer quantity);

    // Validation Operations
    boolean isSkuUnique(String sku);
    
    boolean isSkuUnique(String sku, Long excludeVariantId);
    
    void validateVariantInventoryData(VariantInventoryCreateDto createDto);
    
    void validateVariantInventoryData(VariantInventoryUpdateDto updateDto, Long variantId);

    // Cache Operations
    void clearVariantInventoryCache(Long variantId);

    void clearProductVariantInventoryCache(Long productId);
}
