package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.productcatalog.dto.inventory.InventoryResponseDto;
import org.de013.productcatalog.dto.inventory.InventoryUpdateDto;
import org.de013.productcatalog.dto.inventory.StockAlertDto;
import org.de013.productcatalog.entity.Inventory;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.repository.InventoryRepository;
import org.de013.productcatalog.repository.ProductRepository;
import org.de013.productcatalog.service.InventoryService;
import org.de013.productcatalog.mapper.InventoryMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final InventoryMapper inventoryMapper;

    @Override
    @Cacheable(value = "inventory", key = "#productId")
    public InventoryResponseDto getInventoryByProductId(Long productId) {
        log.debug("Getting inventory for product ID: {}", productId);
        
        Inventory inventory = findInventoryByProductId(productId);
        return inventoryMapper.toInventoryResponseDto(inventory);
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public InventoryResponseDto updateInventory(Long productId, InventoryUpdateDto updateDto) {
        log.info("Updating inventory for product ID: {}", productId);
        
        Inventory inventory = findInventoryByProductId(productId);
        updateInventoryFields(inventory, updateDto);
        
        inventory = inventoryRepository.save(inventory);
        
        log.info("Inventory updated successfully for product ID: {}", productId);
        return inventoryMapper.toInventoryResponseDto(inventory);
    }

    @Override
    @Transactional
    public InventoryResponseDto createInventory(Long productId, InventoryUpdateDto createDto) {
        log.info("Creating inventory for product ID: {}", productId);
        
        Product product = findProductById(productId);
        
        if (inventoryRepository.findByProductId(productId).isPresent()) {
            throw new RuntimeException("Inventory already exists for product ID: " + productId);
        }
        
        Inventory inventory = Inventory.builder()
                .product(product)
                .quantity(createDto.getQuantity() != null ? createDto.getQuantity() : 0)
                .reservedQuantity(createDto.getReservedQuantity() != null ? createDto.getReservedQuantity() : 0)
                .minStockLevel(createDto.getMinStockLevel() != null ? createDto.getMinStockLevel() : 0)
                .maxStockLevel(createDto.getMaxStockLevel())
                .reorderPoint(createDto.getReorderPoint() != null ? createDto.getReorderPoint() : 0)
                .reorderQuantity(createDto.getReorderQuantity())
                .trackInventory(createDto.getTrackInventory() != null ? createDto.getTrackInventory() : true)
                .allowBackorder(createDto.getAllowBackorder() != null ? createDto.getAllowBackorder() : false)
                .location(createDto.getLocation())
                .supplierSku(createDto.getSupplierSku())
                .build();
        
        inventory = inventoryRepository.save(inventory);
        
        log.info("Inventory created successfully for product ID: {}", productId);
        return inventoryMapper.toInventoryResponseDto(inventory);
    }

    @Override
    public List<InventoryResponseDto> getInStockInventories() {
        log.debug("Getting all in-stock inventories");
        
        List<Inventory> inventories = inventoryRepository.findInStockInventories();
        return inventories.stream()
                .map(inventoryMapper::toInventoryResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<InventoryResponseDto> getInStockInventories(Pageable pageable) {
        log.debug("Getting in-stock inventories with pagination: {}", pageable);
        
        Page<Inventory> inventories = inventoryRepository.findInStockInventories(pageable);
        return mapToPageResponse(inventories);
    }

    @Override
    public List<InventoryResponseDto> getOutOfStockInventories() {
        log.debug("Getting all out-of-stock inventories");
        
        List<Inventory> inventories = inventoryRepository.findOutOfStockInventories();
        return inventories.stream()
                .map(inventoryMapper::toInventoryResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<InventoryResponseDto> getOutOfStockInventories(Pageable pageable) {
        log.debug("Getting out-of-stock inventories with pagination: {}", pageable);
        
        Page<Inventory> inventories = inventoryRepository.findOutOfStockInventories(pageable);
        return mapToPageResponse(inventories);
    }

    @Override
    public List<InventoryResponseDto> getLowStockInventories() {
        log.debug("Getting all low-stock inventories");
        
        List<Inventory> inventories = inventoryRepository.findLowStockInventories();
        return inventories.stream()
                .map(inventoryMapper::toInventoryResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<InventoryResponseDto> getLowStockInventories(Pageable pageable) {
        log.debug("Getting low-stock inventories with pagination: {}", pageable);
        
        Page<Inventory> inventories = inventoryRepository.findLowStockInventories(pageable);
        return mapToPageResponse(inventories);
    }

    @Override
    public List<StockAlertDto> getStockAlerts() {
        log.debug("Getting all stock alerts");
        
        List<Object[]> alerts = inventoryRepository.getStockAlerts();
        return alerts.stream()
                .map(this::mapToStockAlertDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<StockAlertDto> getLowStockAlerts() {
        log.debug("Getting low stock alerts");
        
        List<Inventory> inventories = inventoryRepository.findLowStockInventories();
        return inventories.stream()
                .map(this::createLowStockAlert)
                .collect(Collectors.toList());
    }

    @Override
    public List<StockAlertDto> getOutOfStockAlerts() {
        log.debug("Getting out of stock alerts");
        
        List<Inventory> inventories = inventoryRepository.findOutOfStockInventories();
        return inventories.stream()
                .map(this::createOutOfStockAlert)
                .collect(Collectors.toList());
    }

    @Override
    public List<StockAlertDto> getReorderAlerts() {
        log.debug("Getting reorder alerts");
        
        List<Inventory> inventories = inventoryRepository.findInventoriesNeedingReorder();
        return inventories.stream()
                .map(this::createReorderAlert)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public InventoryResponseDto addStock(Long productId, Integer quantity) {
        log.info("Adding {} stock to product ID: {}", quantity, productId);
        
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        Inventory inventory = findInventoryByProductId(productId);
        inventory.addStock(quantity);
        
        inventory = inventoryRepository.save(inventory);
        
        log.info("Added {} stock to product ID: {}, new quantity: {}", quantity, productId, inventory.getQuantity());
        return inventoryMapper.toInventoryResponseDto(inventory);
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public InventoryResponseDto removeStock(Long productId, Integer quantity) {
        log.info("Removing {} stock from product ID: {}", quantity, productId);
        
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        
        Inventory inventory = findInventoryByProductId(productId);
        inventory.removeStock(quantity);
        
        inventory = inventoryRepository.save(inventory);
        
        log.info("Removed {} stock from product ID: {}, new quantity: {}", quantity, productId, inventory.getQuantity());
        return inventoryMapper.toInventoryResponseDto(inventory);
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public InventoryResponseDto adjustStock(Long productId, Integer adjustment) {
        log.info("Adjusting stock by {} for product ID: {}", adjustment, productId);
        
        Inventory inventory = findInventoryByProductId(productId);
        
        if (adjustment > 0) {
            inventory.addStock(adjustment);
        } else if (adjustment < 0) {
            inventory.removeStock(Math.abs(adjustment));
        }
        
        inventory = inventoryRepository.save(inventory);
        
        log.info("Adjusted stock by {} for product ID: {}, new quantity: {}", adjustment, productId, inventory.getQuantity());
        return inventoryMapper.toInventoryResponseDto(inventory);
    }

    @Override
    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public InventoryResponseDto setStock(Long productId, Integer quantity) {
        log.info("Setting stock to {} for product ID: {}", quantity, productId);
        
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        
        Inventory inventory = findInventoryByProductId(productId);
        inventory.setQuantity(quantity);
        
        inventory = inventoryRepository.save(inventory);
        
        log.info("Set stock to {} for product ID: {}", quantity, productId);
        return inventoryMapper.toInventoryResponseDto(inventory);
    }

    @Override
    @Transactional
    public boolean reserveStock(Long productId, Integer quantity) {
        log.info("Reserving {} stock for product ID: {}", quantity, productId);
        
        try {
            int updated = inventoryRepository.reserveStock(productId, quantity);
            boolean success = updated > 0;
            
            if (success) {
                clearInventoryCache(productId);
                log.info("Successfully reserved {} stock for product ID: {}", quantity, productId);
            } else {
                log.warn("Failed to reserve {} stock for product ID: {} - insufficient stock", quantity, productId);
            }
            
            return success;
        } catch (Exception e) {
            log.error("Error reserving stock for product ID: {}", productId, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean releaseReservedStock(Long productId, Integer quantity) {
        log.info("Releasing {} reserved stock for product ID: {}", quantity, productId);
        
        try {
            int updated = inventoryRepository.releaseReservedStock(productId, quantity);
            boolean success = updated > 0;
            
            if (success) {
                clearInventoryCache(productId);
                log.info("Successfully released {} reserved stock for product ID: {}", quantity, productId);
            } else {
                log.warn("Failed to release {} reserved stock for product ID: {} - insufficient reserved stock", quantity, productId);
            }
            
            return success;
        } catch (Exception e) {
            log.error("Error releasing reserved stock for product ID: {}", productId, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean fulfillOrder(Long productId, Integer quantity) {
        log.info("Fulfilling order of {} for product ID: {}", quantity, productId);
        
        try {
            int updated = inventoryRepository.fulfillOrder(productId, quantity);
            boolean success = updated > 0;
            
            if (success) {
                clearInventoryCache(productId);
                log.info("Successfully fulfilled order of {} for product ID: {}", quantity, productId);
            } else {
                log.warn("Failed to fulfill order of {} for product ID: {} - insufficient reserved stock", quantity, productId);
            }
            
            return success;
        } catch (Exception e) {
            log.error("Error fulfilling order for product ID: {}", productId, e);
            return false;
        }
    }

    @Override
    public boolean canFulfillOrder(Long productId, Integer quantity) {
        log.debug("Checking if can fulfill order of {} for product ID: {}", quantity, productId);
        
        Inventory inventory = findInventoryByProductId(productId);
        return inventory.canFulfillOrder(quantity);
    }

    // Helper methods
    private Inventory findInventoryByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product ID: " + productId));
    }

    private Product findProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + productId));
    }

    private PageResponse<InventoryResponseDto> mapToPageResponse(Page<Inventory> inventories) {
        List<InventoryResponseDto> content = inventories.getContent().stream()
                .map(inventoryMapper::toInventoryResponseDto)
                .collect(Collectors.toList());
        
        return PageResponse.<InventoryResponseDto>builder()
                .content(content)
                .page(inventories.getNumber())
                .size(inventories.getSize())
                .totalElements(inventories.getTotalElements())
                .totalPages(inventories.getTotalPages())
                .first(inventories.isFirst())
                .last(inventories.isLast())
                .empty(inventories.isEmpty())
                .build();
    }

    private void updateInventoryFields(Inventory inventory, InventoryUpdateDto updateDto) {
        if (updateDto.getQuantity() != null) {
            inventory.setQuantity(updateDto.getQuantity());
        }
        if (updateDto.getReservedQuantity() != null) {
            inventory.setReservedQuantity(updateDto.getReservedQuantity());
        }
        if (updateDto.getMinStockLevel() != null) {
            inventory.setMinStockLevel(updateDto.getMinStockLevel());
        }
        if (updateDto.getMaxStockLevel() != null) {
            inventory.setMaxStockLevel(updateDto.getMaxStockLevel());
        }
        if (updateDto.getReorderPoint() != null) {
            inventory.setReorderPoint(updateDto.getReorderPoint());
        }
        if (updateDto.getReorderQuantity() != null) {
            inventory.setReorderQuantity(updateDto.getReorderQuantity());
        }
        if (updateDto.getTrackInventory() != null) {
            inventory.setTrackInventory(updateDto.getTrackInventory());
        }
        if (updateDto.getAllowBackorder() != null) {
            inventory.setAllowBackorder(updateDto.getAllowBackorder());
        }
        if (updateDto.getLocation() != null) {
            inventory.setLocation(updateDto.getLocation());
        }
        if (updateDto.getSupplierSku() != null) {
            inventory.setSupplierSku(updateDto.getSupplierSku());
        }
    }

    private StockAlertDto mapToStockAlertDto(Object[] alertData) {
        // Map from repository query result
        return StockAlertDto.builder()
                .productId((Long) alertData[0])
                .productName((String) alertData[1])
                .productSku((String) alertData[2])
                .currentQuantity((Integer) alertData[3])
                .reservedQuantity((Integer) alertData[4])
                .availableQuantity((Integer) alertData[5])
                .minStockLevel((Integer) alertData[6])
                .alertType(StockAlertDto.AlertType.LOW_STOCK)
                .severity(StockAlertDto.AlertSeverity.WARNING)
                .build();
    }

    private StockAlertDto createLowStockAlert(Inventory inventory) {
        return StockAlertDto.builder()
                .productId(inventory.getProduct().getId())
                .productName(inventory.getProduct().getName())
                .productSku(inventory.getProduct().getSku())
                .currentQuantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(inventory.getAvailableQuantity())
                .minStockLevel(inventory.getMinStockLevel())
                .alertType(StockAlertDto.AlertType.LOW_STOCK)
                .severity(StockAlertDto.AlertSeverity.WARNING)
                .message("Product is running low on stock")
                .build();
    }

    private StockAlertDto createOutOfStockAlert(Inventory inventory) {
        return StockAlertDto.builder()
                .productId(inventory.getProduct().getId())
                .productName(inventory.getProduct().getName())
                .productSku(inventory.getProduct().getSku())
                .currentQuantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(inventory.getAvailableQuantity())
                .minStockLevel(inventory.getMinStockLevel())
                .alertType(StockAlertDto.AlertType.OUT_OF_STOCK)
                .severity(StockAlertDto.AlertSeverity.CRITICAL)
                .message("Product is out of stock")
                .build();
    }

    private StockAlertDto createReorderAlert(Inventory inventory) {
        return StockAlertDto.builder()
                .productId(inventory.getProduct().getId())
                .productName(inventory.getProduct().getName())
                .productSku(inventory.getProduct().getSku())
                .currentQuantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(inventory.getAvailableQuantity())
                .reorderPoint(inventory.getReorderPoint())
                .reorderQuantity(inventory.getReorderQuantity())
                .alertType(StockAlertDto.AlertType.NEEDS_REORDER)
                .severity(StockAlertDto.AlertSeverity.INFO)
                .message("Product needs reordering")
                .build();
    }

    // Placeholder implementations for remaining methods
    @Override public List<InventoryResponseDto> bulkUpdateStock(List<Long> productIds, Integer quantity) { return List.of(); }
    @Override public List<InventoryResponseDto> bulkAdjustStock(List<Long> productIds, Integer adjustment) { return List.of(); }
    @Override public List<InventoryResponseDto> bulkSetMinStockLevel(List<Long> productIds, Integer minStockLevel) { return List.of(); }
    @Override public List<InventoryResponseDto> bulkSetReorderPoint(List<Long> productIds, Integer reorderPoint) { return List.of(); }
    @Override public long getTotalInventoryCount() { return inventoryRepository.count(); }
    @Override public long getInStockCount() { return inventoryRepository.countInStockInventories(); }
    @Override public long getOutOfStockCount() { return inventoryRepository.countOutOfStockInventories(); }
    @Override public long getLowStockCount() { return inventoryRepository.countLowStockInventories(); }
    @Override public long getNeedsReorderCount() { return inventoryRepository.countInventoriesNeedingReorder(); }
    @Override public Long getTotalQuantity() { return inventoryRepository.getTotalQuantity(); }
    @Override public Long getTotalReservedQuantity() { return inventoryRepository.getTotalReservedQuantity(); }
    @Override public Long getTotalAvailableQuantity() { return inventoryRepository.getTotalAvailableQuantity(); }
    @Override public List<InventoryResponseDto> getInventoryByLocation(String location) { return List.of(); }
    @Override public List<InventoryResponseDto> searchInventoryByLocation(String locationPattern) { return List.of(); }
    @Override public InventoryResponseDto updateInventoryLocation(Long productId, String location) { return null; }
    @Override public List<InventoryResponseDto> getInventoryBySupplierSku(String supplierSku) { return List.of(); }
    @Override public InventoryResponseDto updateSupplierSku(Long productId, String supplierSku) { return null; }
    @Override public List<InventoryResponseDto> getInventoriesNeedingReorder() { return List.of(); }
    @Override public PageResponse<InventoryResponseDto> getInventoriesNeedingReorder(Pageable pageable) { return null; }
    @Override public InventoryResponseDto updateReorderSettings(Long productId, Integer reorderPoint, Integer reorderQuantity) { return null; }
    @Override public boolean hasInventory(Long productId) { return inventoryRepository.findByProductId(productId).isPresent(); }
    @Override public boolean isInStock(Long productId) { return getAvailableQuantity(productId) > 0; }
    @Override public boolean isLowStock(Long productId) { Inventory inv = findInventoryByProductId(productId); return inv.isLowStock(); }
    @Override public boolean needsReorder(Long productId) { Inventory inv = findInventoryByProductId(productId); return inv.needsReorder(); }
    @Override public Integer getAvailableQuantity(Long productId) { Inventory inv = findInventoryByProductId(productId); return inv.getAvailableQuantity(); }
    @Override public InventoryResponseDto enableInventoryTracking(Long productId) { return null; }
    @Override public InventoryResponseDto disableInventoryTracking(Long productId) { return null; }
    @Override public InventoryResponseDto enableBackorder(Long productId) { return null; }
    @Override public InventoryResponseDto disableBackorder(Long productId) { return null; }
    @Override public List<Object[]> getInventoryStatsByLocation() { return inventoryRepository.getInventoryStatsByLocation(); }
    @Override public List<InventoryResponseDto> getInventoryByQuantityRange(Integer minQuantity, Integer maxQuantity) { return List.of(); }
    @Override public List<InventoryResponseDto> getInventoryByAvailableQuantityRange(Integer minAvailable, Integer maxAvailable) { return List.of(); }
    @Override @CacheEvict(value = "inventory", allEntries = true) public void clearInventoryCache() { }
    @Override @CacheEvict(value = "inventory", key = "#productId") public void clearInventoryCache(Long productId) { }
    @Override public void refreshInventoryCache(Long productId) { }
}
