package org.de013.productcatalog.service;

import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.inventory.InventoryResponseDto;
import org.de013.productcatalog.dto.inventory.InventoryUpdateDto;
import org.de013.productcatalog.dto.inventory.StockAlertDto;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InventoryService {

    // CRUD Operations
    InventoryResponseDto getInventoryByProductId(Long productId);
    
    InventoryResponseDto updateInventory(Long productId, InventoryUpdateDto updateDto);
    
    InventoryResponseDto createInventory(Long productId, InventoryUpdateDto createDto);

    // Stock Level Operations
    List<InventoryResponseDto> getInStockInventories();
    
    PageResponse<InventoryResponseDto> getInStockInventories(Pageable pageable);
    
    List<InventoryResponseDto> getOutOfStockInventories();
    
    PageResponse<InventoryResponseDto> getOutOfStockInventories(Pageable pageable);
    
    List<InventoryResponseDto> getLowStockInventories();
    
    PageResponse<InventoryResponseDto> getLowStockInventories(Pageable pageable);

    // Stock Alerts
    List<StockAlertDto> getStockAlerts();
    
    List<StockAlertDto> getLowStockAlerts();
    
    List<StockAlertDto> getOutOfStockAlerts();
    
    List<StockAlertDto> getReorderAlerts();

    // Stock Management Operations
    InventoryResponseDto addStock(Long productId, Integer quantity);
    
    InventoryResponseDto removeStock(Long productId, Integer quantity);
    
    InventoryResponseDto adjustStock(Long productId, Integer adjustment);
    
    InventoryResponseDto setStock(Long productId, Integer quantity);

    // Stock Reservation Operations
    boolean reserveStock(Long productId, Integer quantity);
    
    boolean releaseReservedStock(Long productId, Integer quantity);
    
    boolean fulfillOrder(Long productId, Integer quantity);
    
    boolean canFulfillOrder(Long productId, Integer quantity);

    // Bulk Operations
    List<InventoryResponseDto> bulkUpdateStock(List<Long> productIds, Integer quantity);
    
    List<InventoryResponseDto> bulkAdjustStock(List<Long> productIds, Integer adjustment);
    
    List<InventoryResponseDto> bulkSetMinStockLevel(List<Long> productIds, Integer minStockLevel);
    
    List<InventoryResponseDto> bulkSetReorderPoint(List<Long> productIds, Integer reorderPoint);

    // Inventory Statistics
    long getTotalInventoryCount();
    
    long getInStockCount();
    
    long getOutOfStockCount();
    
    long getLowStockCount();
    
    long getNeedsReorderCount();
    
    Long getTotalQuantity();
    
    Long getTotalReservedQuantity();
    
    Long getTotalAvailableQuantity();

    // Location-based Operations
    List<InventoryResponseDto> getInventoryByLocation(String location);
    
    List<InventoryResponseDto> searchInventoryByLocation(String locationPattern);
    
    InventoryResponseDto updateInventoryLocation(Long productId, String location);

    // Supplier Operations
    List<InventoryResponseDto> getInventoryBySupplierSku(String supplierSku);
    
    InventoryResponseDto updateSupplierSku(Long productId, String supplierSku);

    // Reorder Operations
    List<InventoryResponseDto> getInventoriesNeedingReorder();
    
    PageResponse<InventoryResponseDto> getInventoriesNeedingReorder(Pageable pageable);
    
    InventoryResponseDto updateReorderSettings(Long productId, Integer reorderPoint, Integer reorderQuantity);

    // Inventory Validation
    boolean hasInventory(Long productId);
    
    boolean isInStock(Long productId);
    
    boolean isLowStock(Long productId);
    
    boolean needsReorder(Long productId);
    
    Integer getAvailableQuantity(Long productId);

    // Inventory Settings
    InventoryResponseDto enableInventoryTracking(Long productId);
    
    InventoryResponseDto disableInventoryTracking(Long productId);
    
    InventoryResponseDto enableBackorder(Long productId);
    
    InventoryResponseDto disableBackorder(Long productId);

    // Inventory Reports
    List<Object[]> getInventoryStatsByLocation();
    
    List<InventoryResponseDto> getInventoryByQuantityRange(Integer minQuantity, Integer maxQuantity);
    
    List<InventoryResponseDto> getInventoryByAvailableQuantityRange(Integer minAvailable, Integer maxAvailable);

    // Cache Operations
    void clearInventoryCache();
    
    void clearInventoryCache(Long productId);
    
    void refreshInventoryCache(Long productId);
}
