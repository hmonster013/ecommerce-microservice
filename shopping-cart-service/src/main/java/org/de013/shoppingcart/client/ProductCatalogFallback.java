package org.de013.shoppingcart.client;

import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.dto.ProductInfo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback implementation for Product Catalog Service
 * Provides default responses when the service is unavailable
 */
@Component
@Slf4j
public class ProductCatalogFallback implements ProductCatalogFeignClient {

    // ==================== PRODUCT INFORMATION ====================

    @Override
    public ProductInfo getProductById(String productId) {
        log.warn("Product Catalog Service unavailable, using fallback for product: {}", productId);
        return createFallbackProductInfo(productId);
    }

    @Override
    public Map<String, ProductInfo> getProductsByIds(List<String> productIds) {
        log.warn("Product Catalog Service unavailable, using fallback for {} products", productIds.size());
        Map<String, ProductInfo> result = new HashMap<>();
        for (String productId : productIds) {
            result.put(productId, createFallbackProductInfo(productId));
        }
        return result;
    }

    @Override
    public ProductInfo getProductBySku(String sku) {
        log.warn("Product Catalog Service unavailable, using fallback for SKU: {}", sku);
        return createFallbackProductInfo("unknown-" + sku);
    }

    // ==================== PRICING & AVAILABILITY ====================

    @Override
    public Map<String, Object> getProductPrice(String productId) {
        log.warn("Product Catalog Service unavailable, using fallback pricing for: {}", productId);
        return Map.of(
            "productId", productId,
            "price", BigDecimal.ZERO,
            "currency", "USD",
            "available", false,
            "fallback", true
        );
    }

    @Override
    public Map<String, Map<String, Object>> getBulkPricing(List<String> productIds) {
        log.warn("Product Catalog Service unavailable, using fallback bulk pricing for {} products", productIds.size());
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (String productId : productIds) {
            result.put(productId, getProductPrice(productId));
        }
        return result;
    }

    @Override
    public Map<String, Object> checkAvailability(String productId) {
        log.warn("Product Catalog Service unavailable, using fallback availability for: {}", productId);
        return Map.of(
            "productId", productId,
            "available", false,
            "stockQuantity", 0,
            "status", "UNAVAILABLE",
            "fallback", true
        );
    }

    @Override
    public Map<String, Map<String, Object>> checkBulkAvailability(List<String> productIds) {
        log.warn("Product Catalog Service unavailable, using fallback bulk availability for {} products", productIds.size());
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (String productId : productIds) {
            result.put(productId, checkAvailability(productId));
        }
        return result;
    }

    // ==================== STOCK MANAGEMENT ====================

    @Override
    public Map<String, Object> getStockLevel(String productId) {
        log.warn("Product Catalog Service unavailable, using fallback stock level for: {}", productId);
        return Map.of(
            "productId", productId,
            "stockQuantity", 0,
            "reserved", 0,
            "available", 0,
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> reserveQuantity(String productId, Integer quantity, Long cartId, Integer ttlMinutes) {
        log.warn("Product Catalog Service unavailable, cannot reserve quantity for product: {}", productId);
        return Map.of(
            "success", false,
            "productId", productId,
            "quantity", quantity,
            "cartId", cartId,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> releaseReservation(String productId, Long cartId) {
        log.warn("Product Catalog Service unavailable, cannot release reservation for product: {}", productId);
        return Map.of(
            "success", false,
            "productId", productId,
            "cartId", cartId,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> updateReservation(String productId, Integer quantity, Long cartId) {
        log.warn("Product Catalog Service unavailable, cannot update reservation for product: {}", productId);
        return Map.of(
            "success", false,
            "productId", productId,
            "quantity", quantity,
            "cartId", cartId,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    // ==================== PRODUCT VALIDATION ====================

    @Override
    public Map<String, Object> validateProduct(String productId) {
        log.warn("Product Catalog Service unavailable, using fallback validation for: {}", productId);
        return Map.of(
            "productId", productId,
            "valid", false,
            "active", false,
            "purchasable", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Map<String, Object>> validateProducts(List<String> productIds) {
        log.warn("Product Catalog Service unavailable, using fallback validation for {} products", productIds.size());
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (String productId : productIds) {
            result.put(productId, validateProduct(productId));
        }
        return result;
    }

    @Override
    public Map<String, Object> isPurchasable(String productId, Integer quantity) {
        log.warn("Product Catalog Service unavailable, using fallback purchasable check for: {}", productId);
        return Map.of(
            "productId", productId,
            "quantity", quantity,
            "purchasable", false,
            "reason", "Service unavailable",
            "fallback", true
        );
    }

    // ==================== PRODUCT VARIANTS ====================

    @Override
    public List<Map<String, Object>> getProductVariants(String productId) {
        log.warn("Product Catalog Service unavailable, returning empty variants for: {}", productId);
        return List.of();
    }

    @Override
    public Map<String, Object> getVariantInfo(String productId, String variantId) {
        log.warn("Product Catalog Service unavailable, using fallback variant info for: {}/{}", productId, variantId);
        return Map.of(
            "productId", productId,
            "variantId", variantId,
            "available", false,
            "fallback", true
        );
    }

    // ==================== CATEGORY & SEARCH ====================

    @Override
    public List<ProductInfo> getProductsByCategory(String categoryId, Integer page, Integer size) {
        log.warn("Product Catalog Service unavailable, returning empty category products for: {}", categoryId);
        return List.of();
    }

    @Override
    public List<ProductInfo> searchProducts(String query, Integer page, Integer size) {
        log.warn("Product Catalog Service unavailable, returning empty search results for: {}", query);
        return List.of();
    }

    // ==================== PROMOTIONS & DISCOUNTS ====================

    @Override
    public List<Map<String, Object>> getActivePromotions(String productId) {
        log.warn("Product Catalog Service unavailable, returning empty promotions for: {}", productId);
        return List.of();
    }

    @Override
    public Map<String, Object> calculateDiscount(String productId, Map<String, Object> discountRequest) {
        log.warn("Product Catalog Service unavailable, using fallback discount calculation for: {}", productId);
        return Map.of(
            "productId", productId,
            "discountAmount", BigDecimal.ZERO,
            "discountPercent", 0.0,
            "fallback", true
        );
    }

    // ==================== PRODUCT RECOMMENDATIONS ====================

    @Override
    public List<ProductInfo> getRelatedProducts(String productId, Integer limit) {
        log.warn("Product Catalog Service unavailable, returning empty related products for: {}", productId);
        return List.of();
    }

    @Override
    public List<ProductInfo> getFrequentlyBoughtTogether(String productId, Integer limit) {
        log.warn("Product Catalog Service unavailable, returning empty frequently bought together for: {}", productId);
        return List.of();
    }

    // ==================== PRODUCT REVIEWS & RATINGS ====================

    @Override
    public Map<String, Object> getProductRating(String productId) {
        log.warn("Product Catalog Service unavailable, using fallback rating for: {}", productId);
        return Map.of(
            "productId", productId,
            "averageRating", 0.0,
            "totalReviews", 0,
            "fallback", true
        );
    }

    @Override
    public List<Map<String, Object>> getProductReviews(String productId, Integer page, Integer size) {
        log.warn("Product Catalog Service unavailable, returning empty reviews for: {}", productId);
        return List.of();
    }

    // ==================== PRODUCT ANALYTICS ====================

    @Override
    public void trackProductView(String productId, String userId, String sessionId) {
        log.warn("Product Catalog Service unavailable, cannot track product view for: {}", productId);
    }

    @Override
    public void trackAddToCart(String productId, Integer quantity, Long cartId, String userId) {
        log.warn("Product Catalog Service unavailable, cannot track add to cart for: {}", productId);
    }

    // ==================== PRODUCT CONFIGURATION ====================

    @Override
    public Map<String, Object> getProductConfiguration(String productId) {
        log.warn("Product Catalog Service unavailable, returning empty configuration for: {}", productId);
        return Map.of(
            "productId", productId,
            "configuration", Map.of(),
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> validateConfiguration(String productId, Map<String, Object> configuration) {
        log.warn("Product Catalog Service unavailable, using fallback configuration validation for: {}", productId);
        return Map.of(
            "productId", productId,
            "valid", false,
            "errors", List.of("Service unavailable"),
            "fallback", true
        );
    }

    // ==================== HELPER METHODS ====================

    /**
     * Create fallback product info
     */
    private ProductInfo createFallbackProductInfo(String productId) {
        return ProductInfo.builder()
                .sku(productId)
                .name("Product Unavailable")
                .description("Product information is currently unavailable")
                .price(BigDecimal.ZERO)
                .originalPrice(BigDecimal.ZERO)
                .stockQuantity(0)
                .status("UNAVAILABLE")
                .build();
    }
}
