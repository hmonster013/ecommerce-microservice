package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.InventoryDto;
import org.de013.productcatalog.entity.Inventory;
import org.de013.productcatalog.mapper.InventoryMapper;
import org.de013.productcatalog.repository.InventoryRepository;
import org.de013.productcatalog.service.InventoryService;
import org.springframework.cache.annotation.CacheEvict;
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
    private final org.springframework.cache.CacheManager cacheManager;

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

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Inventory inventory = findInventoryByProductId(productId);
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

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        Inventory inventory = findInventoryByProductId(productId);
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

        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }

        Inventory inventory = findInventoryByProductId(productId);
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


    // Helper methods
    private Inventory findInventoryByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new RuntimeException("Inventory not found for product ID: " + productId));
    }


    @Override
    public Integer getAvailableQuantity(Long productId) {
        Inventory inventory = findInventoryByProductId(productId);
        return inventory.getAvailableQuantity();
    }

    // Helper method for cache management
    private void clearInventoryCache(Long productId) {
        log.debug("Clearing inventory and products cache for product ID: {}", productId);
        org.springframework.cache.Cache inventoryCache = cacheManager.getCache("inventory");
        if (inventoryCache != null) {
            inventoryCache.evict(productId);
        }
        org.springframework.cache.Cache productsCache = cacheManager.getCache("products");
        if (productsCache != null) {
            productsCache.evict(productId);
        }
    }
}
