package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.InventoryDto;
import org.de013.common.exception.ResourceNotFoundException;
import org.de013.productcatalog.entity.Inventory;
import org.de013.productcatalog.exception.InsufficientStockException;
import org.de013.productcatalog.exception.InvalidStockQuantityException;
import org.de013.productcatalog.mapper.InventoryMapper;
import org.de013.productcatalog.repository.InventoryRepository;
import org.de013.productcatalog.service.InventoryService;
import org.de013.productcatalog.service.support.InventoryCacheInvalidator;
import org.de013.productcatalog.service.support.DurableReservationGuard;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;
    private final InventoryCacheInvalidator cacheInvalidator;
    private final DurableReservationGuard durableReservationGuard;

    @Override
    @Cacheable(value = "inventory", key = "#productId")
    public InventoryDto getInventoryByProductId(Long productId) {
        log.debug("Getting inventory for product ID: {}", productId);

        Inventory inventory = findInventoryByProductId(productId);
        return inventoryMapper.toInventoryDto(inventory);
    }

    @Override
    @Transactional
    public InventoryDto addStock(Long productId, Integer quantity) {
        log.info("Adding {} stock to product ID: {}", quantity, productId);

        if (quantity == null || quantity <= 0) {
            throw new InvalidStockQuantityException("Quantity must be positive");
        }

        Inventory inventory = lockInventoryByProductId(productId);
        if (quantity > Integer.MAX_VALUE - inventory.getQuantity()) {
            throw new InvalidStockQuantityException("Stock quantity exceeds the supported maximum");
        }
        inventory.addStock(quantity);

        inventory = inventoryRepository.save(inventory);

        clearInventoryCache(productId);
        log.info("Added {} stock to product ID: {}, new quantity: {}", quantity, productId, inventory.getQuantity());
        return inventoryMapper.toInventoryDto(inventory);
    }

    @Override
    @Transactional
    public InventoryDto removeStock(Long productId, Integer quantity) {
        log.info("Removing {} stock from product ID: {}", quantity, productId);

        if (quantity == null || quantity <= 0) {
            throw new InvalidStockQuantityException("Quantity must be positive");
        }

        Inventory inventory = lockInventoryByProductId(productId);
        if (quantity > inventory.getQuantity() - inventory.getReservedQuantity()) {
            throw new InsufficientStockException("Cannot remove reserved stock");
        }
        inventory.removeStock(quantity);

        inventory = inventoryRepository.save(inventory);

        clearInventoryCache(productId);
        log.info("Removed {} stock from product ID: {}, new quantity: {}", quantity, productId, inventory.getQuantity());
        return inventoryMapper.toInventoryDto(inventory);
    }


    @Override
    @Transactional
    public InventoryDto setStock(Long productId, Integer quantity) {
        log.info("Setting stock to {} for product ID: {}", quantity, productId);

        if (quantity == null || quantity < 0) {
            throw new InvalidStockQuantityException("Quantity must be non-negative");
        }

        Inventory inventory = lockInventoryByProductId(productId);
        if (quantity < inventory.getReservedQuantity()) {
            throw new InsufficientStockException("Stock quantity cannot be less than reserved quantity");
        }
        inventory.setQuantity(quantity);

        inventory = inventoryRepository.save(inventory);

        clearInventoryCache(productId);
        log.info("Set stock to {} for product ID: {}", quantity, productId);
        return inventoryMapper.toInventoryDto(inventory);
    }

    @Override
    @Transactional
    public boolean reserveStock(Long productId, Integer quantity) {
        log.info("Reserving {} stock for product ID: {}", quantity, productId);

        requirePositiveQuantity(quantity);
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

        requirePositiveQuantity(quantity);
        durableReservationGuard.rejectLegacyMutation(false, productId);
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

        requirePositiveQuantity(quantity);
        durableReservationGuard.rejectLegacyMutation(false, productId);
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


    // Helper methods
    private Inventory findInventoryByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + productId));
    }

    private Inventory lockInventoryByProductId(Long productId) {
        return inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product ID: " + productId));
    }

    private void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new InvalidStockQuantityException("Quantity must be positive");
        }
    }


    @Override
    public Integer getAvailableQuantity(Long productId) {
        Inventory inventory = findInventoryByProductId(productId);
        return inventory.getAvailableQuantity();
    }

    // Helper method for cache management
    private void clearInventoryCache(Long productId) {
        log.debug("Clearing inventory and products cache for product ID: {}", productId);
        cacheInvalidator.productChanged(productId);
    }
}
