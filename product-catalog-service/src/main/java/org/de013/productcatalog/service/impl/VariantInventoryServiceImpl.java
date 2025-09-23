package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.exception.ResourceNotFoundException;
import org.de013.productcatalog.dto.inventory.VariantInventoryCreateDto;
import org.de013.common.dto.VariantInventoryDto;
import org.de013.productcatalog.dto.inventory.VariantInventoryUpdateDto;
import org.de013.productcatalog.entity.Product;
import org.de013.productcatalog.entity.ProductVariant;
import org.de013.productcatalog.entity.VariantInventory;
import org.de013.productcatalog.entity.enums.VariantType;
import org.de013.productcatalog.mapper.VariantInventoryMapper;
import org.de013.productcatalog.repository.ProductRepository;
import org.de013.productcatalog.repository.ProductVariantRepository;
import org.de013.productcatalog.repository.VariantInventoryRepository;
import org.de013.productcatalog.service.VariantInventoryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VariantInventoryServiceImpl implements VariantInventoryService {

    private final VariantInventoryRepository variantInventoryRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final VariantInventoryMapper variantInventoryMapper;

    @Override
    @Transactional
    public VariantInventoryDto createVariantInventory(VariantInventoryCreateDto createDto) {
        log.info("Creating variant inventory for variant ID: {}", createDto.getVariantId());

        validateVariantInventoryData(createDto);

        // Find variant and product
        ProductVariant variant = findVariantById(createDto.getVariantId());
        Product product = variant.getProduct();

        // Check if inventory already exists
        if (variantInventoryRepository.findByVariantId(createDto.getVariantId()).isPresent()) {
            throw new IllegalArgumentException("Inventory already exists for variant ID: " + createDto.getVariantId());
        }

        // Create variant inventory
        VariantInventory variantInventory = VariantInventory.builder()
                .variant(variant)
                .product(product)
                .quantity(createDto.getInitialQuantity() != null ? createDto.getInitialQuantity() : 0)
                .reservedQuantity(0)
                .minStockLevel(createDto.getMinStockLevel())
                .maxStockLevel(createDto.getMaxStockLevel())
                .reorderPoint(createDto.getReorderPoint())
                .reorderQuantity(createDto.getReorderQuantity())
                .trackInventory(createDto.getTrackInventory())
                .allowBackorder(createDto.getAllowBackorder())
                .location(createDto.getLocation())
                .sku(createDto.getSku())
                .build();

        variantInventory = variantInventoryRepository.save(variantInventory);
        log.info("Variant inventory created successfully with ID: {}", variantInventory.getId());

        return variantInventoryMapper.toVariantInventoryDto(variantInventory);
    }

    @Override
    @Transactional
    @CacheEvict(value = "variantInventory", key = "#variantId")
    public VariantInventoryDto updateVariantInventory(Long variantId, VariantInventoryUpdateDto updateDto) {
        log.info("Updating variant inventory for variant ID: {}", variantId);

        validateVariantInventoryData(updateDto, variantId);

        VariantInventory variantInventory = findVariantInventoryByVariantId(variantId);

        // Update fields if provided
        if (updateDto.getMinStockLevel() != null) {
            variantInventory.setMinStockLevel(updateDto.getMinStockLevel());
        }
        if (updateDto.getMaxStockLevel() != null) {
            variantInventory.setMaxStockLevel(updateDto.getMaxStockLevel());
        }
        if (updateDto.getReorderPoint() != null) {
            variantInventory.setReorderPoint(updateDto.getReorderPoint());
        }
        if (updateDto.getReorderQuantity() != null) {
            variantInventory.setReorderQuantity(updateDto.getReorderQuantity());
        }
        if (updateDto.getTrackInventory() != null) {
            variantInventory.setTrackInventory(updateDto.getTrackInventory());
        }
        if (updateDto.getAllowBackorder() != null) {
            variantInventory.setAllowBackorder(updateDto.getAllowBackorder());
        }
        if (updateDto.getLocation() != null) {
            variantInventory.setLocation(updateDto.getLocation());
        }
        if (updateDto.getSku() != null) {
            variantInventory.setSku(updateDto.getSku());
        }

        variantInventory = variantInventoryRepository.save(variantInventory);
        log.info("Variant inventory updated successfully for variant ID: {}", variantId);

        return variantInventoryMapper.toVariantInventoryDto(variantInventory);
    }

    @Override
    @Transactional
    @CacheEvict(value = "variantInventory", key = "#variantId")
    public void deleteVariantInventory(Long variantId) {
        log.info("Deleting variant inventory for variant ID: {}", variantId);

        VariantInventory variantInventory = findVariantInventoryByVariantId(variantId);
        variantInventoryRepository.delete(variantInventory);

        log.info("Variant inventory deleted successfully for variant ID: {}", variantId);
    }

    @Override
    @Cacheable(value = "variantInventory", key = "#variantId")
    public VariantInventoryDto getVariantInventoryByVariantId(Long variantId) {
        log.debug("Getting variant inventory for variant ID: {}", variantId);

        VariantInventory variantInventory = findVariantInventoryByVariantId(variantId);
        return variantInventoryMapper.toVariantInventoryDto(variantInventory);
    }

    @Override
    public VariantInventoryDto getVariantInventoryBySku(String sku) {
        log.debug("Getting variant inventory by SKU: {}", sku);

        VariantInventory variantInventory = variantInventoryRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Variant inventory not found with SKU: " + sku));

        return variantInventoryMapper.toVariantInventoryDto(variantInventory);
    }

    @Override
    public List<VariantInventoryDto> getVariantInventoriesByProductId(Long productId) {
        log.debug("Getting variant inventories for product ID: {}", productId);

        List<VariantInventory> variantInventories = variantInventoryRepository.findByProductId(productId);
        return variantInventoryMapper.toVariantInventoryDtoList(variantInventories);
    }

    @Override
    public List<VariantInventoryDto> getInStockVariantsByProductId(Long productId) {
        log.debug("Getting in-stock variants for product ID: {}", productId);

        List<VariantInventory> variantInventories = variantInventoryRepository.findInStockByProductId(productId);
        return variantInventoryMapper.toVariantInventoryDtoList(variantInventories);
    }

    @Override
    public List<VariantInventoryDto> getOutOfStockVariantsByProductId(Long productId) {
        log.debug("Getting out-of-stock variants for product ID: {}", productId);

        List<VariantInventory> variantInventories = variantInventoryRepository.findOutOfStockByProductId(productId);
        return variantInventoryMapper.toVariantInventoryDtoList(variantInventories);
    }

    @Override
    public List<VariantInventoryDto> getLowStockVariantsByProductId(Long productId) {
        log.debug("Getting low-stock variants for product ID: {}", productId);

        List<VariantInventory> variantInventories = variantInventoryRepository.findLowStockByProductId(productId);
        return variantInventoryMapper.toVariantInventoryDtoList(variantInventories);
    }

    @Override
    public List<VariantInventoryDto> getVariantsNeedingReorderByProductId(Long productId) {
        log.debug("Getting variants needing reorder for product ID: {}", productId);

        List<VariantInventory> variantInventories = variantInventoryRepository.findNeedsReorderByProductId(productId);
        return variantInventoryMapper.toVariantInventoryDtoList(variantInventories);
    }

    @Override
    public List<VariantInventoryDto> getVariantInventoriesByProductIdAndType(Long productId, VariantType variantType) {
        log.debug("Getting variant inventories for product ID: {} and type: {}", productId, variantType);

        List<VariantInventory> variantInventories = variantInventoryRepository.findByProductIdAndVariantType(productId, variantType);
        return variantInventoryMapper.toVariantInventoryDtoList(variantInventories);
    }

    @Override
    public List<VariantInventoryDto> getInStockVariantsByProductIdAndType(Long productId, VariantType variantType) {
        log.debug("Getting in-stock variants for product ID: {} and type: {}", productId, variantType);

        List<VariantInventory> variantInventories = variantInventoryRepository.findInStockByProductIdAndVariantType(productId, variantType);
        return variantInventoryMapper.toVariantInventoryDtoList(variantInventories);
    }

    // Helper methods
    private VariantInventory findVariantInventoryByVariantId(Long variantId) {
        return variantInventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant inventory not found for variant ID: " + variantId));
    }

    private ProductVariant findVariantById(Long variantId) {
        return variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found with ID: " + variantId));
    }

    // Validation methods
    @Override
    public void validateVariantInventoryData(VariantInventoryCreateDto createDto) {
        log.debug("Validating variant inventory data for variant ID: {}", createDto.getVariantId());

        // Check if variant exists
        if (!variantRepository.existsById(createDto.getVariantId())) {
            throw new ResourceNotFoundException("Product variant not found with ID: " + createDto.getVariantId());
        }

        // Validate SKU uniqueness if provided
        if (StringUtils.hasText(createDto.getSku()) && !isSkuUnique(createDto.getSku())) {
            throw new IllegalArgumentException("SKU already exists: " + createDto.getSku());
        }

        // Validate stock levels
        if (!createDto.isMaxStockLevelValid()) {
            throw new IllegalArgumentException("Maximum stock level must be greater than or equal to minimum stock level");
        }

        if (!createDto.isReorderPointValid()) {
            throw new IllegalArgumentException("Reorder point must be less than or equal to maximum stock level");
        }
    }

    @Override
    public void validateVariantInventoryData(VariantInventoryUpdateDto updateDto, Long variantId) {
        log.debug("Validating variant inventory update data for variant ID: {}", variantId);

        // Validate SKU uniqueness if provided
        if (StringUtils.hasText(updateDto.getSku()) && !isSkuUnique(updateDto.getSku(), variantId)) {
            throw new IllegalArgumentException("SKU already exists: " + updateDto.getSku());
        }

        // Validate stock levels
        if (!updateDto.isMaxStockLevelValid()) {
            throw new IllegalArgumentException("Maximum stock level must be greater than or equal to minimum stock level");
        }

        if (!updateDto.isReorderPointValid()) {
            throw new IllegalArgumentException("Reorder point must be less than or equal to maximum stock level");
        }
    }

    @Override
    public boolean isSkuUnique(String sku) {
        return !variantInventoryRepository.findBySku(sku).isPresent();
    }

    @Override
    public boolean isSkuUnique(String sku, Long excludeVariantId) {
        Optional<VariantInventory> existing = variantInventoryRepository.findBySku(sku);
        return existing.isEmpty() || existing.get().getVariant().getId().equals(excludeVariantId);
    }

    // Cache operations
    @Override
    @CacheEvict(value = "variantInventory", key = "#variantId")
    public void clearVariantInventoryCache(Long variantId) {
        log.debug("Clearing variant inventory cache for variant ID: {}", variantId);
    }

    @Override
    @CacheEvict(value = "variantInventory", allEntries = true)
    public void clearProductVariantInventoryCache(Long productId) {
        log.debug("Clearing all variant inventory cache for product ID: {}", productId);
    }

    // Stock Management Operations
    @Override
    @Transactional
    @CacheEvict(value = "variantInventory", key = "#variantId")
    public VariantInventoryDto addStock(Long variantId, Integer quantity) {
        log.info("Adding {} stock to variant ID: {}", quantity, variantId);

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        VariantInventory variantInventory = findVariantInventoryByVariantId(variantId);
        variantInventory.addStock(quantity);

        variantInventory = variantInventoryRepository.save(variantInventory);
        log.info("Added {} stock to variant ID: {}, new quantity: {}", quantity, variantId, variantInventory.getQuantity());

        return variantInventoryMapper.toVariantInventoryDto(variantInventory);
    }

    @Override
    @Transactional
    @CacheEvict(value = "variantInventory", key = "#variantId")
    public VariantInventoryDto removeStock(Long variantId, Integer quantity) {
        log.info("Removing {} stock from variant ID: {}", quantity, variantId);

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        VariantInventory variantInventory = findVariantInventoryByVariantId(variantId);
        variantInventory.removeStock(quantity);

        variantInventory = variantInventoryRepository.save(variantInventory);
        log.info("Removed {} stock from variant ID: {}, new quantity: {}", quantity, variantId, variantInventory.getQuantity());

        return variantInventoryMapper.toVariantInventoryDto(variantInventory);
    }

    @Override
    @Transactional
    @CacheEvict(value = "variantInventory", key = "#variantId")
    public VariantInventoryDto setStock(Long variantId, Integer quantity) {
        log.info("Setting stock to {} for variant ID: {}", quantity, variantId);

        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }

        VariantInventory variantInventory = findVariantInventoryByVariantId(variantId);
        variantInventory.setQuantity(quantity);

        variantInventory = variantInventoryRepository.save(variantInventory);
        log.info("Set stock to {} for variant ID: {}", quantity, variantId);

        return variantInventoryMapper.toVariantInventoryDto(variantInventory);
    }

    // Stock Reservation Operations
    @Override
    @Transactional
    public boolean reserveStock(Long variantId, Integer quantity) {
        log.info("Reserving {} stock for variant ID: {}", quantity, variantId);

        try {
            int updated = variantInventoryRepository.reserveStock(variantId, quantity);
            boolean success = updated > 0;

            if (success) {
                clearVariantInventoryCache(variantId);
                log.info("Successfully reserved {} stock for variant ID: {}", quantity, variantId);
            } else {
                log.warn("Failed to reserve {} stock for variant ID: {} - insufficient stock", quantity, variantId);
            }

            return success;
        } catch (Exception e) {
            log.error("Error reserving stock for variant ID: {}", variantId, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean releaseReservedStock(Long variantId, Integer quantity) {
        log.info("Releasing {} reserved stock for variant ID: {}", quantity, variantId);

        try {
            int updated = variantInventoryRepository.releaseReservedStock(variantId, quantity);
            boolean success = updated > 0;

            if (success) {
                clearVariantInventoryCache(variantId);
                log.info("Successfully released {} reserved stock for variant ID: {}", quantity, variantId);
            } else {
                log.warn("Failed to release {} reserved stock for variant ID: {} - insufficient reserved stock", quantity, variantId);
            }

            return success;
        } catch (Exception e) {
            log.error("Error releasing reserved stock for variant ID: {}", variantId, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean fulfillOrder(Long variantId, Integer quantity) {
        log.info("Fulfilling order of {} for variant ID: {}", quantity, variantId);

        try {
            int updated = variantInventoryRepository.fulfillOrder(variantId, quantity);
            boolean success = updated > 0;

            if (success) {
                clearVariantInventoryCache(variantId);
                log.info("Successfully fulfilled order of {} for variant ID: {}", quantity, variantId);
            } else {
                log.warn("Failed to fulfill order of {} for variant ID: {} - insufficient reserved stock", quantity, variantId);
            }

            return success;
        } catch (Exception e) {
            log.error("Error fulfilling order for variant ID: {}", variantId, e);
            return false;
        }
    }

    // Availability Checks
    @Override
    public boolean isVariantInStock(Long variantId) {
        log.debug("Checking if variant ID: {} is in stock", variantId);

        VariantInventory variantInventory = findVariantInventoryByVariantId(variantId);
        return variantInventory.isInStock();
    }

    @Override
    public boolean canFulfillOrder(Long variantId, Integer quantity) {
        log.debug("Checking if variant ID: {} can fulfill order of {}", variantId, quantity);

        VariantInventory variantInventory = findVariantInventoryByVariantId(variantId);
        return variantInventory.canFulfillOrder(quantity);
    }

    @Override
    public Integer getAvailableQuantity(Long variantId) {
        log.debug("Getting available quantity for variant ID: {}", variantId);

        VariantInventory variantInventory = findVariantInventoryByVariantId(variantId);
        return variantInventory.getAvailableQuantity();
    }

    @Override
    public boolean hasAnyVariantInStock(Long productId) {
        log.debug("Checking if any variant is in stock for product ID: {}", productId);

        return variantInventoryRepository.hasAnyVariantInStock(productId);
    }

    // Bulk Operations - placeholder implementations
    @Override public void bulkAddStock(List<Long> variantIds, Integer quantity) {
        // TODO: Implement bulk add stock
    }

    @Override public void bulkSetStock(List<Long> variantIds, Integer quantity) {
        // TODO: Implement bulk set stock
    }

    @Override public List<VariantInventoryDto> bulkReserveStock(List<Long> variantIds, List<Integer> quantities) {
        // TODO: Implement bulk reserve stock
        return List.of();
    }

    // Statistics Operations
    @Override
    public Long getTotalQuantityByProductId(Long productId) {
        log.debug("Getting total quantity for product ID: {}", productId);
        return variantInventoryRepository.getTotalQuantityByProductId(productId);
    }

    @Override
    public Long getTotalAvailableQuantityByProductId(Long productId) {
        log.debug("Getting total available quantity for product ID: {}", productId);
        return variantInventoryRepository.getTotalAvailableQuantityByProductId(productId);
    }

    @Override
    public Long countInStockVariantsByProductId(Long productId) {
        log.debug("Counting in-stock variants for product ID: {}", productId);
        return variantInventoryRepository.countInStockVariantsByProductId(productId);
    }

    @Override
    public Long countOutOfStockVariantsByProductId(Long productId) {
        log.debug("Counting out-of-stock variants for product ID: {}", productId);
        return variantInventoryRepository.countOutOfStockVariantsByProductId(productId);
    }

    // Location Operations
    @Override
    public List<VariantInventoryDto> getVariantInventoriesByLocation(String location) {
        log.debug("Getting variant inventories by location: {}", location);

        List<VariantInventory> variantInventories = variantInventoryRepository.findByLocation(location);
        return variantInventoryMapper.toVariantInventoryDtoList(variantInventories);
    }

    @Override
    public List<String> getAllLocations() {
        log.debug("Getting all distinct locations");
        return variantInventoryRepository.findDistinctLocations();
    }

    @Override
    @Transactional
    @CacheEvict(value = "variantInventory", key = "#variantId")
    public void updateVariantLocation(Long variantId, String location) {
        log.info("Updating location to '{}' for variant ID: {}", location, variantId);

        VariantInventory variantInventory = findVariantInventoryByVariantId(variantId);
        variantInventory.setLocation(location);
        variantInventoryRepository.save(variantInventory);

        log.info("Updated location for variant ID: {}", variantId);
    }

    // Advanced Operations
    @Override
    public List<VariantInventoryDto> findVariantsThatCanFulfill(Long productId, Integer requestedQuantity) {
        log.debug("Finding variants that can fulfill {} for product ID: {}", requestedQuantity, productId);

        List<VariantInventory> variantInventories = variantInventoryRepository
                .findVariantsThatCanFulfill(productId, requestedQuantity);
        return variantInventoryMapper.toVariantInventoryDtoList(variantInventories);
    }

    @Override
    public VariantInventoryDto findBestVariantForOrder(Long productId, VariantType variantType, String preferredValue, Integer quantity) {
        log.debug("Finding best variant for order: product={}, type={}, value={}, quantity={}",
                 productId, variantType, preferredValue, quantity);

        // Try to find the preferred variant first
        if (StringUtils.hasText(preferredValue)) {
            Optional<VariantInventory> preferred = variantInventoryRepository
                    .findByProductIdAndVariantTypeAndValue(productId, variantType, preferredValue);

            if (preferred.isPresent() && preferred.get().canFulfillOrder(quantity)) {
                return variantInventoryMapper.toVariantInventoryDto(preferred.get());
            }
        }

        // If preferred variant is not available, find any variant that can fulfill the order
        List<VariantInventory> availableVariants = variantInventoryRepository
                .findInStockByProductIdAndVariantType(productId, variantType);

        for (VariantInventory variant : availableVariants) {
            if (variant.canFulfillOrder(quantity)) {
                return variantInventoryMapper.toVariantInventoryDto(variant);
            }
        }

        throw new ResourceNotFoundException("No variant available to fulfill order for product ID: " + productId);
    }
}
