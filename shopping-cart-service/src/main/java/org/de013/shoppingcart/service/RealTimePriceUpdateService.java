package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.client.ProductCatalogFeignClient;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Real-time Price Update Service
 * Handles real-time price updates from Product Catalog Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimePriceUpdateService {

    private final ProductCatalogFeignClient productCatalogFeignClient;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final CacheInvalidationService cacheInvalidationService;

    // ==================== PRICE UPDATE PROCESSING ====================

    /**
     * Process price update for a specific product
     */
    @Async
    public CompletableFuture<Void> processPriceUpdate(String productId, BigDecimal newPrice, BigDecimal oldPrice) {
        try {
            log.info("Processing price update for product {}: {} -> {}", productId, oldPrice, newPrice);
            
            // Find all carts containing this product
            List<Cart> affectedCarts = findCartsContainingProduct(productId);
            
            if (affectedCarts.isEmpty()) {
                log.debug("No carts found containing product: {}", productId);
                return CompletableFuture.completedFuture(null);
            }
            
            log.info("Found {} carts affected by price update for product: {}", affectedCarts.size(), productId);
            
            // Update prices in affected carts
            for (Cart cart : affectedCarts) {
                updateCartItemPrices(cart, productId, newPrice, oldPrice);
            }
            
            // Invalidate related caches
            cacheInvalidationService.invalidateProductCaches(productId);
            
            log.info("Completed price update processing for product: {}", productId);
            
        } catch (Exception e) {
            log.error("Error processing price update for product {}: {}", productId, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Process bulk price updates
     */
    @Async
    public CompletableFuture<Void> processBulkPriceUpdates(Map<String, Map<String, BigDecimal>> priceUpdates) {
        try {
            log.info("Processing bulk price updates for {} products", priceUpdates.size());
            
            for (Map.Entry<String, Map<String, BigDecimal>> entry : priceUpdates.entrySet()) {
                String productId = entry.getKey();
                Map<String, BigDecimal> prices = entry.getValue();
                
                BigDecimal newPrice = prices.get("newPrice");
                BigDecimal oldPrice = prices.get("oldPrice");
                
                if (newPrice != null && oldPrice != null) {
                    processPriceUpdate(productId, newPrice, oldPrice);
                }
            }
            
            log.info("Completed bulk price updates processing");
            
        } catch (Exception e) {
            log.error("Error processing bulk price updates: {}", e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    // ==================== STOCK UPDATE PROCESSING ====================

    /**
     * Process stock level update for a product
     */
    @Async
    public CompletableFuture<Void> processStockUpdate(String productId, Integer newStock, Integer oldStock) {
        try {
            log.info("Processing stock update for product {}: {} -> {}", productId, oldStock, newStock);
            
            // Find carts with this product
            List<Cart> affectedCarts = findCartsContainingProduct(productId);
            
            if (affectedCarts.isEmpty()) {
                log.debug("No carts found containing product: {}", productId);
                return CompletableFuture.completedFuture(null);
            }
            
            // Check for out-of-stock situations
            if (newStock <= 0) {
                handleOutOfStockProduct(productId, affectedCarts);
            } else if (newStock < oldStock) {
                handleReducedStockProduct(productId, newStock, affectedCarts);
            }
            
            // Invalidate related caches
            cacheInvalidationService.invalidateProductCaches(productId);
            
            log.info("Completed stock update processing for product: {}", productId);
            
        } catch (Exception e) {
            log.error("Error processing stock update for product {}: {}", productId, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    // ==================== PRODUCT AVAILABILITY UPDATES ====================

    /**
     * Process product availability update
     */
    @Async
    public CompletableFuture<Void> processAvailabilityUpdate(String productId, boolean isAvailable, String reason) {
        try {
            log.info("Processing availability update for product {}: available={}, reason={}", 
                    productId, isAvailable, reason);
            
            if (!isAvailable) {
                // Handle product becoming unavailable
                List<Cart> affectedCarts = findCartsContainingProduct(productId);
                handleUnavailableProduct(productId, affectedCarts, reason);
            }
            
            // Invalidate related caches
            cacheInvalidationService.invalidateProductCaches(productId);
            
            log.info("Completed availability update processing for product: {}", productId);
            
        } catch (Exception e) {
            log.error("Error processing availability update for product {}: {}", productId, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Find all carts containing a specific product
     */
    private List<Cart> findCartsContainingProduct(String productId) {
        try {
            // This would be a custom query in a real implementation
            return cartRepository.findAll().stream()
                    .filter(cart -> cart.getCartItems().stream()
                            .anyMatch(item -> productId.equals(item.getProductId())))
                    .toList();
        } catch (Exception e) {
            log.error("Error finding carts containing product {}: {}", productId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Update cart item prices for a specific product
     */
    private void updateCartItemPrices(Cart cart, String productId, BigDecimal newPrice, BigDecimal oldPrice) {
        try {
            boolean updated = false;
            
            for (CartItem item : cart.getCartItems()) {
                if (productId.equals(item.getProductId())) {
                    // Update unit price
                    item.setUnitPrice(newPrice);
                    
                    // Recalculate subtotal (CartItem doesn't have setSubtotal, it's calculated)
                    // The subtotal will be recalculated when cart totals are updated
                    
                    // Update timestamps
                    item.setUpdatedAt(LocalDateTime.now());
                    
                    updated = true;
                    
                    log.debug("Updated price for item {} in cart {}: {} -> {}", 
                            item.getId(), cart.getId(), oldPrice, newPrice);
                }
            }
            
            if (updated) {
                // Recalculate cart totals
                cartService.updateCartTotals(cart.getId());
                
                // Invalidate cart caches
                cacheInvalidationService.invalidateCartCaches(cart.getId());
                
                log.info("Updated cart {} due to price change for product: {}", cart.getId(), productId);
            }
            
        } catch (Exception e) {
            log.error("Error updating cart item prices for cart {}: {}", cart.getId(), e.getMessage());
        }
    }

    /**
     * Handle out-of-stock product
     */
    private void handleOutOfStockProduct(String productId, List<Cart> affectedCarts) {
        try {
            log.warn("Product {} is out of stock, updating {} affected carts", productId, affectedCarts.size());
            
            for (Cart cart : affectedCarts) {
                for (CartItem item : cart.getCartItems()) {
                    if (productId.equals(item.getProductId())) {
                        // Mark item as out of stock
                        item.setStockQuantity(0);
                        item.setUpdatedAt(LocalDateTime.now());
                        
                        log.info("Marked item {} as out of stock in cart {}", item.getId(), cart.getId());
                    }
                }
                
                // Save cart changes
                cartRepository.save(cart);
                
                // Invalidate cart caches
                cacheInvalidationService.invalidateCartCaches(cart.getId());
            }
            
        } catch (Exception e) {
            log.error("Error handling out-of-stock product {}: {}", productId, e.getMessage());
        }
    }

    /**
     * Handle reduced stock product
     */
    private void handleReducedStockProduct(String productId, Integer newStock, List<Cart> affectedCarts) {
        try {
            log.info("Product {} stock reduced to {}, checking {} affected carts", 
                    productId, newStock, affectedCarts.size());
            
            for (Cart cart : affectedCarts) {
                for (CartItem item : cart.getCartItems()) {
                    if (productId.equals(item.getProductId())) {
                        // Update stock quantity
                        item.setStockQuantity(newStock);
                        
                        // Check if cart quantity exceeds available stock
                        if (item.getQuantity() > newStock) {
                            log.warn("Cart {} item {} quantity ({}) exceeds available stock ({})", 
                                    cart.getId(), item.getId(), item.getQuantity(), newStock);
                            
                            // Optionally adjust quantity to available stock
                            // item.setQuantity(newStock);
                            // item.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(newStock)));
                        }
                        
                        item.setUpdatedAt(LocalDateTime.now());
                    }
                }
                
                // Save cart changes
                cartRepository.save(cart);
                
                // Invalidate cart caches
                cacheInvalidationService.invalidateCartCaches(cart.getId());
            }
            
        } catch (Exception e) {
            log.error("Error handling reduced stock for product {}: {}", productId, e.getMessage());
        }
    }

    /**
     * Handle unavailable product
     */
    private void handleUnavailableProduct(String productId, List<Cart> affectedCarts, String reason) {
        try {
            log.warn("Product {} became unavailable ({}), updating {} affected carts", 
                    productId, reason, affectedCarts.size());
            
            for (Cart cart : affectedCarts) {
                for (CartItem item : cart.getCartItems()) {
                    if (productId.equals(item.getProductId())) {
                        // Mark item as unavailable
                        item.setStockQuantity(0);
                        item.setUpdatedAt(LocalDateTime.now());
                        
                        log.info("Marked item {} as unavailable in cart {} due to: {}", 
                                item.getId(), cart.getId(), reason);
                    }
                }
                
                // Save cart changes
                cartRepository.save(cart);
                
                // Invalidate cart caches
                cacheInvalidationService.invalidateCartCaches(cart.getId());
            }
            
        } catch (Exception e) {
            log.error("Error handling unavailable product {}: {}", productId, e.getMessage());
        }
    }
}
