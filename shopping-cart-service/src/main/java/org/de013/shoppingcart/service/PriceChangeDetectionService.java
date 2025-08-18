package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.client.ProductCatalogFeignClient;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Price Change Detection Service
 * Monitors and detects price changes for cart items
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceChangeDetectionService {

    private final ProductCatalogFeignClient productCatalogFeignClient;
    private final CartRepository cartRepository;
    private final CacheInvalidationService cacheInvalidationService;

    @Value("${shopping-cart.pricing.change-threshold:0.01}")
    private BigDecimal priceChangeThreshold;

    @Value("${shopping-cart.pricing.significant-change-threshold:1.00}")
    private BigDecimal significantChangeThreshold;

    @Value("${shopping-cart.pricing.max-price-increase-percent:20.0}")
    private double maxPriceIncreasePercent;

    // ==================== PRICE CHANGE DETECTION ====================

    /**
     * Detect price changes for a specific cart
     */
    public Map<String, Object> detectCartPriceChanges(Long cartId) {
        try {
            log.debug("Detecting price changes for cart: {}", cartId);
            
            Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cartId);
            result.put("timestamp", LocalDateTime.now());
            
            List<Map<String, Object>> itemChanges = new ArrayList<>();
            boolean hasChanges = false;
            BigDecimal totalPriceImpact = BigDecimal.ZERO;
            int itemsWithChanges = 0;
            
            // Check each cart item for price changes
            for (CartItem item : cart.getCartItems()) {
                Map<String, Object> itemChange = detectItemPriceChange(item);
                itemChanges.add(itemChange);
                
                if (Boolean.TRUE.equals(itemChange.get("hasChange"))) {
                    hasChanges = true;
                    itemsWithChanges++;
                    
                    BigDecimal priceImpact = (BigDecimal) itemChange.get("totalPriceImpact");
                    if (priceImpact != null) {
                        totalPriceImpact = totalPriceImpact.add(priceImpact);
                    }
                }
            }
            
            result.put("hasChanges", hasChanges);
            result.put("itemChanges", itemChanges);
            result.put("totalItems", cart.getCartItems().size());
            result.put("itemsWithChanges", itemsWithChanges);
            result.put("totalPriceImpact", totalPriceImpact);
            result.put("priceIncreased", totalPriceImpact.compareTo(BigDecimal.ZERO) > 0);
            
            // Categorize the impact
            result.put("impactLevel", categorizeImpact(totalPriceImpact, cart.getTotalAmount()));
            
            return result;
            
        } catch (Exception e) {
            log.error("Error detecting price changes for cart {}: {}", cartId, e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cartId);
            result.put("hasChanges", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Detect price change for individual cart item
     */
    private Map<String, Object> detectItemPriceChange(CartItem item) {
        Map<String, Object> result = new HashMap<>();
        result.put("itemId", item.getId());
        result.put("productId", item.getProductId());
        result.put("currentPrice", item.getUnitPrice());
        
        try {
            // Get current market price
            Map<String, Object> currentPricing = productCatalogFeignClient.getProductPrice(item.getProductId());
            
            if (Boolean.TRUE.equals(currentPricing.get("fallback"))) {
                result.put("hasChange", false);
                result.put("reason", "Pricing service unavailable");
                return result;
            }
            
            BigDecimal marketPrice = new BigDecimal(currentPricing.get("price").toString());
            result.put("marketPrice", marketPrice);
            
            // Calculate price difference
            BigDecimal priceDifference = marketPrice.subtract(item.getUnitPrice());
            result.put("priceDifference", priceDifference);
            result.put("priceChangePercent", calculatePercentageChange(item.getUnitPrice(), marketPrice));
            
            // Check if change is significant
            boolean hasSignificantChange = priceDifference.abs().compareTo(priceChangeThreshold) > 0;
            result.put("hasChange", hasSignificantChange);
            
            if (hasSignificantChange) {
                result.put("changeType", priceDifference.compareTo(BigDecimal.ZERO) > 0 ? "INCREASE" : "DECREASE");
                result.put("isSignificant", priceDifference.abs().compareTo(significantChangeThreshold) > 0);
                
                // Calculate total impact for this item
                BigDecimal totalImpact = priceDifference.multiply(BigDecimal.valueOf(item.getQuantity()));
                result.put("totalPriceImpact", totalImpact);
                
                // Check if price increase exceeds maximum allowed
                if (priceDifference.compareTo(BigDecimal.ZERO) > 0) {
                    double increasePercent = calculatePercentageChange(item.getUnitPrice(), marketPrice);
                    result.put("exceedsMaxIncrease", increasePercent > maxPriceIncreasePercent);
                }
                
                log.info("Price change detected for item {}: {} -> {} ({})", 
                        item.getId(), item.getUnitPrice(), marketPrice, 
                        priceDifference.compareTo(BigDecimal.ZERO) > 0 ? "increase" : "decrease");
            }
            
        } catch (Exception e) {
            log.error("Error detecting price change for item {}: {}", item.getId(), e.getMessage());
            result.put("hasChange", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    // ==================== BATCH PRICE MONITORING ====================

    /**
     * Monitor price changes for multiple carts
     */
    @Async
    public CompletableFuture<Map<String, Object>> monitorMultipleCartPrices(List<Long> cartIds) {
        try {
            log.info("Monitoring price changes for {} carts", cartIds.size());
            
            Map<String, Object> result = new HashMap<>();
            result.put("timestamp", LocalDateTime.now());
            result.put("totalCarts", cartIds.size());
            
            Map<Long, Map<String, Object>> cartResults = new HashMap<>();
            int cartsWithChanges = 0;
            BigDecimal totalImpact = BigDecimal.ZERO;
            
            for (Long cartId : cartIds) {
                try {
                    Map<String, Object> cartResult = detectCartPriceChanges(cartId);
                    cartResults.put(cartId, cartResult);
                    
                    if (Boolean.TRUE.equals(cartResult.get("hasChanges"))) {
                        cartsWithChanges++;
                        
                        BigDecimal cartImpact = (BigDecimal) cartResult.get("totalPriceImpact");
                        if (cartImpact != null) {
                            totalImpact = totalImpact.add(cartImpact);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error monitoring cart {}: {}", cartId, e.getMessage());
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("cartId", cartId);
                    errorResult.put("error", e.getMessage());
                    cartResults.put(cartId, errorResult);
                }
            }
            
            result.put("cartResults", cartResults);
            result.put("cartsWithChanges", cartsWithChanges);
            result.put("totalPriceImpact", totalImpact);
            result.put("averageImpactPerCart", 
                cartIds.size() > 0 ? totalImpact.divide(BigDecimal.valueOf(cartIds.size()), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO);
            
            log.info("Price monitoring completed: {}/{} carts have changes, total impact: {}", 
                    cartsWithChanges, cartIds.size(), totalImpact);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Error in batch price monitoring: {}", e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            errorResult.put("timestamp", LocalDateTime.now());
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    // ==================== PRICE CHANGE ACTIONS ====================

    /**
     * Apply price changes to cart items
     */
    public Map<String, Object> applyPriceChangesToCart(Long cartId, boolean autoApprove) {
        try {
            log.info("Applying price changes to cart: {} (auto-approve: {})", cartId, autoApprove);
            
            Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
            
            Map<String, Object> priceChanges = detectCartPriceChanges(cartId);
            
            if (!Boolean.TRUE.equals(priceChanges.get("hasChanges"))) {
                return Map.of(
                    "cartId", cartId,
                    "applied", false,
                    "reason", "No price changes detected"
                );
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cartId);
            result.put("timestamp", LocalDateTime.now());
            
            List<Map<String, Object>> appliedChanges = new ArrayList<>();
            boolean hasSignificantChanges = false;
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemChanges = (List<Map<String, Object>>) priceChanges.get("itemChanges");
            
            for (Map<String, Object> itemChange : itemChanges) {
                if (Boolean.TRUE.equals(itemChange.get("hasChange"))) {
                    Long itemId = (Long) itemChange.get("itemId");
                    BigDecimal newPrice = (BigDecimal) itemChange.get("marketPrice");
                    Boolean isSignificant = (Boolean) itemChange.get("isSignificant");
                    
                    if (isSignificant != null && isSignificant) {
                        hasSignificantChanges = true;
                    }
                    
                    // Apply change if auto-approve or not significant
                    if (autoApprove || (isSignificant == null || !isSignificant)) {
                        CartItem item = cart.getCartItems().stream()
                            .filter(ci -> ci.getId().equals(itemId))
                            .findFirst()
                            .orElse(null);
                        
                        if (item != null) {
                            BigDecimal oldPrice = item.getUnitPrice();
                            item.setUnitPrice(newPrice);
                            item.setUpdatedAt(LocalDateTime.now());
                            
                            Map<String, Object> appliedChange = new HashMap<>();
                            appliedChange.put("itemId", itemId);
                            appliedChange.put("oldPrice", oldPrice);
                            appliedChange.put("newPrice", newPrice);
                            appliedChange.put("applied", true);
                            appliedChanges.add(appliedChange);
                        }
                    }
                }
            }
            
            // Save cart if changes were applied
            if (!appliedChanges.isEmpty()) {
                cartRepository.save(cart);
                cacheInvalidationService.invalidateCartCaches(cartId);
            }
            
            result.put("applied", !appliedChanges.isEmpty());
            result.put("appliedChanges", appliedChanges);
            result.put("hasSignificantChanges", hasSignificantChanges);
            result.put("requiresApproval", hasSignificantChanges && !autoApprove);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error applying price changes to cart {}: {}", cartId, e.getMessage(), e);
            
            return Map.of(
                "cartId", cartId,
                "applied", false,
                "error", e.getMessage()
            );
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Calculate percentage change between two prices
     */
    private double calculatePercentageChange(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        
        BigDecimal change = newPrice.subtract(oldPrice);
        BigDecimal percentChange = change.divide(oldPrice, 4, BigDecimal.ROUND_HALF_UP)
                                       .multiply(BigDecimal.valueOf(100));
        
        return percentChange.doubleValue();
    }

    /**
     * Categorize the impact level of price changes
     */
    private String categorizeImpact(BigDecimal totalImpact, BigDecimal cartTotal) {
        if (totalImpact.abs().compareTo(BigDecimal.valueOf(0.01)) <= 0) {
            return "MINIMAL";
        }
        
        if (cartTotal != null && cartTotal.compareTo(BigDecimal.ZERO) > 0) {
            double impactPercent = totalImpact.abs().divide(cartTotal, 4, BigDecimal.ROUND_HALF_UP)
                                             .multiply(BigDecimal.valueOf(100)).doubleValue();
            
            if (impactPercent < 1.0) {
                return "LOW";
            } else if (impactPercent < 5.0) {
                return "MODERATE";
            } else if (impactPercent < 10.0) {
                return "HIGH";
            } else {
                return "CRITICAL";
            }
        }
        
        // Fallback to absolute amount categorization
        BigDecimal absImpact = totalImpact.abs();
        if (absImpact.compareTo(BigDecimal.valueOf(1.00)) <= 0) {
            return "LOW";
        } else if (absImpact.compareTo(BigDecimal.valueOf(10.00)) <= 0) {
            return "MODERATE";
        } else if (absImpact.compareTo(BigDecimal.valueOf(50.00)) <= 0) {
            return "HIGH";
        } else {
            return "CRITICAL";
        }
    }
}
