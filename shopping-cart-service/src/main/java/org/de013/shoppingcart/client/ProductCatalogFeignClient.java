package org.de013.shoppingcart.client;

import org.de013.common.dto.ProductDetailDto;
import org.de013.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feign Client for Product Catalog Service
 * Provides real-time product information, pricing, and stock data
 */
@FeignClient(
    name = "product-catalog-service",
    path = "/products", // Service-to-service calls use internal paths
    fallback = ProductCatalogFallback.class
)
public interface ProductCatalogFeignClient {

    // ==================== PRODUCT INFORMATION ====================

    /**
     * Get product information by ID
     */
    @GetMapping("/{productId}")
    ApiResponse<ProductDetailDto> getProductById(@PathVariable("productId") String productId);

    /**
     * Get multiple products by IDs
     */
    @PostMapping("/batch")
    Map<String, ProductDetailDto> getProductsByIds(@RequestBody List<String> productIds);

    /**
     * Get product by SKU
     */
    @GetMapping("/sku/{sku}")
    ProductDetailDto getProductBySku(@PathVariable("sku") String sku);

    // ==================== PRICING & AVAILABILITY ====================

    /**
     * Get current product price
     */
    @GetMapping("/{productId}/price")
    Map<String, Object> getProductPrice(@PathVariable("productId") String productId);

    /**
     * Get bulk pricing for multiple products
     */
    @PostMapping("/pricing/batch")
    Map<String, Map<String, Object>> getBulkPricing(@RequestBody List<String> productIds);

    /**
     * Check product availability
     */
    @GetMapping("/{productId}/availability")
    Map<String, Object> checkAvailability(@PathVariable("productId") String productId);

    /**
     * Check bulk availability
     */
    @PostMapping("/availability/batch")
    Map<String, Map<String, Object>> checkBulkAvailability(@RequestBody List<String> productIds);

    // ==================== STOCK MANAGEMENT ====================

    /**
     * Get current stock level
     */
    @GetMapping("/{productId}/stock")
    Map<String, Object> getStockLevel(@PathVariable("productId") String productId);

    /**
     * Reserve product quantity for cart
     */
    @PostMapping("/{productId}/reserve")
    Map<String, Object> reserveQuantity(
            @PathVariable("productId") String productId,
            @RequestParam("quantity") Integer quantity,
            @RequestParam("cartId") Long cartId,
            @RequestParam(value = "ttlMinutes", defaultValue = "30") Integer ttlMinutes);

    /**
     * Release reserved quantity
     */
    @DeleteMapping("/{productId}/reserve")
    Map<String, Object> releaseReservation(
            @PathVariable("productId") String productId,
            @RequestParam("cartId") Long cartId);

    /**
     * Update reservation quantity
     */
    @PutMapping("/{productId}/reserve")
    Map<String, Object> updateReservation(
            @PathVariable("productId") String productId,
            @RequestParam("quantity") Integer quantity,
            @RequestParam("cartId") Long cartId);

    // ==================== PRODUCT VALIDATION ====================

    /**
     * Validate product exists and is active
     */
    @GetMapping("/{productId}/validate")
    Map<String, Object> validateProduct(@PathVariable("productId") String productId);

    /**
     * Validate multiple products
     */
    @PostMapping("/validate/batch")
    Map<String, Map<String, Object>> validateProducts(@RequestBody List<String> productIds);

    /**
     * Check if product is purchasable
     */
    @GetMapping("/{productId}/purchasable")
    Map<String, Object> isPurchasable(
            @PathVariable("productId") String productId,
            @RequestParam(value = "quantity", defaultValue = "1") Integer quantity);

    // ==================== PRODUCT VARIANTS ====================

    /**
     * Get product variants
     */
    @GetMapping("/{productId}/variants")
    List<Map<String, Object>> getProductVariants(@PathVariable("productId") String productId);

    /**
     * Get specific variant information
     */
    @GetMapping("/{productId}/variants/{variantId}")
    Map<String, Object> getVariantInfo(
            @PathVariable("productId") String productId,
            @PathVariable("variantId") String variantId);

    // ==================== CATEGORY & SEARCH ====================

    /**
     * Get products by category
     */
    @GetMapping("/category/{categoryId}")
    List<ProductDetailDto> getProductsByCategory(
            @PathVariable("categoryId") String categoryId,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size);

    /**
     * Search products
     */
    @GetMapping("/search")
    List<ProductDetailDto> searchProducts(
            @RequestParam("query") String query,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size);

    // ==================== PROMOTIONS & DISCOUNTS ====================

    /**
     * Get active promotions for product
     */
    @GetMapping("/{productId}/promotions")
    List<Map<String, Object>> getActivePromotions(@PathVariable("productId") String productId);

    /**
     * Calculate discount for product
     */
    @PostMapping("/{productId}/discount")
    Map<String, Object> calculateDiscount(
            @PathVariable("productId") String productId,
            @RequestBody Map<String, Object> discountRequest);

    // ==================== PRODUCT RECOMMENDATIONS ====================

    /**
     * Get related products
     */
    @GetMapping("/{productId}/related")
    List<ProductDetailDto> getRelatedProducts(
            @PathVariable("productId") String productId,
            @RequestParam(value = "limit", defaultValue = "5") Integer limit);

    /**
     * Get frequently bought together
     */
    @GetMapping("/{productId}/frequently-bought-together")
    List<ProductDetailDto> getFrequentlyBoughtTogether(
            @PathVariable("productId") String productId,
            @RequestParam(value = "limit", defaultValue = "3") Integer limit);



    // ==================== PRODUCT ANALYTICS ====================

    /**
     * Track product view
     */
    @PostMapping("/{productId}/track/view")
    void trackProductView(
            @PathVariable("productId") String productId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "sessionId", required = false) String sessionId);

    /**
     * Track add to cart
     */
    @PostMapping("/{productId}/track/add-to-cart")
    void trackAddToCart(
            @PathVariable("productId") String productId,
            @RequestParam("quantity") Integer quantity,
            @RequestParam("cartId") Long cartId,
            @RequestParam(value = "userId", required = false) String userId);

    // ==================== PRODUCT CONFIGURATION ====================

    /**
     * Get product configuration options
     */
    @GetMapping("/{productId}/configuration")
    Map<String, Object> getProductConfiguration(@PathVariable("productId") String productId);

    /**
     * Validate product configuration
     */
    @PostMapping("/{productId}/configuration/validate")
    Map<String, Object> validateConfiguration(
            @PathVariable("productId") String productId,
            @RequestBody Map<String, Object> configuration);
}
