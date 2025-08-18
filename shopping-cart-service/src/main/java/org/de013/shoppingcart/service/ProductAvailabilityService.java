package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.client.ProductCatalogFeignClient;
import org.de013.shoppingcart.dto.ProductInfo;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.CartItem;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Product Availability Validation Service
 * Handles real-time product availability, stock, and pricing validation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductAvailabilityService {

    private final ProductCatalogFeignClient productCatalogFeignClient;

    // ==================== PRODUCT AVAILABILITY ====================

    /**
     * Validate product availability for cart item
     */
    @Cacheable(value = "product-availability", key = "#productId", unless = "#result.get('fallback') == true")
    public Map<String, Object> validateProductAvailability(String productId, Integer requestedQuantity) {
        try {
            log.debug("Validating availability for product: {} quantity: {}", productId, requestedQuantity);
            
            // Get product information
            ProductInfo productInfo = productCatalogFeignClient.getProductById(productId);
            Map<String, Object> availability = productCatalogFeignClient.checkAvailability(productId);
            Map<String, Object> stockInfo = productCatalogFeignClient.getStockLevel(productId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("productId", productId);
            result.put("requestedQuantity", requestedQuantity);
            result.put("timestamp", LocalDateTime.now());
            
            // Check if product exists and is active
            if (productInfo == null || "UNAVAILABLE".equals(productInfo.getStatus())) {
                result.put("available", false);
                result.put("reason", "Product not found or unavailable");
                result.put("errorCode", "PRODUCT_UNAVAILABLE");
                return result;
            }
            
            // Check product availability
            Boolean isAvailable = (Boolean) availability.getOrDefault("available", false);
            if (!isAvailable) {
                result.put("available", false);
                result.put("reason", "Product temporarily unavailable");
                result.put("errorCode", "PRODUCT_TEMPORARILY_UNAVAILABLE");
                return result;
            }
            
            // Check stock quantity
            Integer stockQuantity = (Integer) stockInfo.getOrDefault("available", 0);
            if (stockQuantity < requestedQuantity) {
                result.put("available", false);
                result.put("reason", "Insufficient stock");
                result.put("errorCode", "INSUFFICIENT_STOCK");
                result.put("availableQuantity", stockQuantity);
                result.put("shortfall", requestedQuantity - stockQuantity);
                return result;
            }
            
            // Check if product is purchasable
            Map<String, Object> purchasable = productCatalogFeignClient.isPurchasable(productId, requestedQuantity);
            Boolean canPurchase = (Boolean) purchasable.getOrDefault("purchasable", false);
            if (!canPurchase) {
                result.put("available", false);
                result.put("reason", purchasable.getOrDefault("reason", "Product not purchasable"));
                result.put("errorCode", "PRODUCT_NOT_PURCHASABLE");
                return result;
            }
            
            // All checks passed
            result.put("available", true);
            result.put("stockQuantity", stockQuantity);
            result.put("productInfo", productInfo);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error validating product availability for {}: {}", productId, e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("productId", productId);
            result.put("available", false);
            result.put("reason", "Validation service error");
            result.put("errorCode", "VALIDATION_ERROR");
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Validate availability for multiple products
     */
    public Map<String, Map<String, Object>> validateBulkAvailability(Map<String, Integer> productQuantities) {
        try {
            log.debug("Validating bulk availability for {} products", productQuantities.size());
            
            Map<String, Map<String, Object>> results = new HashMap<>();
            
            // Get bulk availability data
            List<String> productIds = productQuantities.keySet().stream().toList();
            Map<String, Map<String, Object>> bulkAvailability = 
                productCatalogFeignClient.checkBulkAvailability(productIds);
            
            // Validate each product
            for (Map.Entry<String, Integer> entry : productQuantities.entrySet()) {
                String productId = entry.getKey();
                Integer quantity = entry.getValue();
                
                Map<String, Object> validation = validateProductAvailability(productId, quantity);
                results.put(productId, validation);
            }
            
            return results;
            
        } catch (Exception e) {
            log.error("Error in bulk availability validation: {}", e.getMessage(), e);
            
            Map<String, Map<String, Object>> results = new HashMap<>();
            for (String productId : productQuantities.keySet()) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("productId", productId);
                errorResult.put("available", false);
                errorResult.put("reason", "Bulk validation error");
                errorResult.put("errorCode", "BULK_VALIDATION_ERROR");
                results.put(productId, errorResult);
            }
            return results;
        }
    }

    // ==================== PRICE VALIDATION ====================

    /**
     * Validate current product pricing
     */
    @Cacheable(value = "product-pricing", key = "#productId", unless = "#result.get('fallback') == true")
    public Map<String, Object> validateProductPricing(String productId, BigDecimal expectedPrice) {
        try {
            log.debug("Validating pricing for product: {} expected: {}", productId, expectedPrice);
            
            Map<String, Object> currentPricing = productCatalogFeignClient.getProductPrice(productId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("productId", productId);
            result.put("expectedPrice", expectedPrice);
            result.put("timestamp", LocalDateTime.now());
            
            // Check if pricing data is available
            if (Boolean.TRUE.equals(currentPricing.get("fallback"))) {
                result.put("valid", false);
                result.put("reason", "Pricing service unavailable");
                result.put("errorCode", "PRICING_SERVICE_UNAVAILABLE");
                return result;
            }
            
            BigDecimal currentPrice = new BigDecimal(currentPricing.get("price").toString());
            result.put("currentPrice", currentPrice);
            
            // Check for price changes
            BigDecimal priceDifference = currentPrice.subtract(expectedPrice).abs();
            BigDecimal changeThreshold = new BigDecimal("0.01"); // 1 cent threshold
            
            if (priceDifference.compareTo(changeThreshold) > 0) {
                result.put("valid", false);
                result.put("reason", "Price has changed");
                result.put("errorCode", "PRICE_CHANGED");
                result.put("priceDifference", priceDifference);
                result.put("priceIncreased", currentPrice.compareTo(expectedPrice) > 0);
                return result;
            }
            
            // Price is valid
            result.put("valid", true);
            result.put("priceDifference", priceDifference);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error validating product pricing for {}: {}", productId, e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("productId", productId);
            result.put("valid", false);
            result.put("reason", "Price validation error");
            result.put("errorCode", "PRICE_VALIDATION_ERROR");
            result.put("error", e.getMessage());
            return result;
        }
    }

    // ==================== CART VALIDATION ====================

    /**
     * Validate entire cart for availability and pricing
     */
    public Map<String, Object> validateCartAvailability(Cart cart) {
        try {
            log.debug("Validating cart availability for cart: {}", cart.getId());
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cart.getId());
            result.put("timestamp", LocalDateTime.now());
            
            List<Map<String, Object>> itemValidations = new ArrayList<>();
            boolean allItemsValid = true;
            int totalIssues = 0;
            
            // Validate each cart item
            for (CartItem item : cart.getCartItems()) {
                Map<String, Object> itemValidation = validateCartItem(item);
                itemValidations.add(itemValidation);
                
                if (!Boolean.TRUE.equals(itemValidation.get("valid"))) {
                    allItemsValid = false;
                    totalIssues++;
                }
            }
            
            result.put("valid", allItemsValid);
            result.put("itemValidations", itemValidations);
            result.put("totalItems", cart.getCartItems().size());
            result.put("itemsWithIssues", totalIssues);
            result.put("validItems", cart.getCartItems().size() - totalIssues);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error validating cart availability for cart {}: {}", cart.getId(), e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cart.getId());
            result.put("valid", false);
            result.put("reason", "Cart validation error");
            result.put("errorCode", "CART_VALIDATION_ERROR");
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Validate individual cart item
     */
    private Map<String, Object> validateCartItem(CartItem item) {
        Map<String, Object> result = new HashMap<>();
        result.put("itemId", item.getId());
        result.put("productId", item.getProductId());
        result.put("quantity", item.getQuantity());
        
        try {
            // Validate availability
            Map<String, Object> availabilityResult = validateProductAvailability(
                item.getProductId(), item.getQuantity());
            
            // Validate pricing
            Map<String, Object> pricingResult = validateProductPricing(
                item.getProductId(), item.getUnitPrice());
            
            boolean isValid = Boolean.TRUE.equals(availabilityResult.get("available")) &&
                            Boolean.TRUE.equals(pricingResult.get("valid"));
            
            result.put("valid", isValid);
            result.put("availability", availabilityResult);
            result.put("pricing", pricingResult);
            
            // Collect issues
            List<String> issues = new ArrayList<>();
            if (!Boolean.TRUE.equals(availabilityResult.get("available"))) {
                issues.add((String) availabilityResult.get("reason"));
            }
            if (!Boolean.TRUE.equals(pricingResult.get("valid"))) {
                issues.add((String) pricingResult.get("reason"));
            }
            result.put("issues", issues);
            
        } catch (Exception e) {
            log.error("Error validating cart item {}: {}", item.getId(), e.getMessage());
            result.put("valid", false);
            result.put("issues", List.of("Item validation error: " + e.getMessage()));
        }
        
        return result;
    }

    // ==================== STOCK RESERVATION ====================

    /**
     * Reserve stock for cart items
     */
    public Map<String, Object> reserveCartStock(Cart cart, int ttlMinutes) {
        try {
            log.info("Reserving stock for cart: {} with TTL: {} minutes", cart.getId(), ttlMinutes);
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cart.getId());
            result.put("timestamp", LocalDateTime.now());
            
            List<Map<String, Object>> reservations = new ArrayList<>();
            boolean allReserved = true;
            
            for (CartItem item : cart.getCartItems()) {
                Map<String, Object> reservation = productCatalogFeignClient.reserveQuantity(
                    item.getProductId(), item.getQuantity(), cart.getId(), ttlMinutes);
                
                reservations.add(reservation);
                
                if (!Boolean.TRUE.equals(reservation.get("success"))) {
                    allReserved = false;
                }
            }
            
            result.put("success", allReserved);
            result.put("reservations", reservations);
            result.put("ttlMinutes", ttlMinutes);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error reserving stock for cart {}: {}", cart.getId(), e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cart.getId());
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Release stock reservations for cart
     */
    public Map<String, Object> releaseCartStock(Cart cart) {
        try {
            log.info("Releasing stock reservations for cart: {}", cart.getId());
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cart.getId());
            result.put("timestamp", LocalDateTime.now());
            
            List<Map<String, Object>> releases = new ArrayList<>();
            boolean allReleased = true;
            
            for (CartItem item : cart.getCartItems()) {
                Map<String, Object> release = productCatalogFeignClient.releaseReservation(
                    item.getProductId(), cart.getId());
                
                releases.add(release);
                
                if (!Boolean.TRUE.equals(release.get("success"))) {
                    allReleased = false;
                }
            }
            
            result.put("success", allReleased);
            result.put("releases", releases);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error releasing stock for cart {}: {}", cart.getId(), e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("cartId", cart.getId());
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
}
