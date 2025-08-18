package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.client.ProductCatalogFeignClient;
import org.de013.shoppingcart.dto.ProductInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Product Catalog Client
 * Integrates with Product Catalog Service for product information, pricing, and availability
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCatalogClient {

    private final ProductCatalogFeignClient productCatalogFeignClient;
    private final RestTemplate restTemplate;

    @Value("${app.services.product-catalog.url:http://localhost:8081}")
    private String productCatalogServiceUrl;

    @Value("${app.services.product-catalog.timeout:5000}")
    private int timeoutMs;

    // ==================== PRODUCT INFORMATION ====================

    /**
     * Get product information by ID
     */
    @Cacheable(value = "productInfo", key = "#productId", unless = "#result == null")
    public ProductInfo getProductInfo(String productId) {
        try {
            log.debug("Fetching product info for product: {}", productId);

            // Use Feign client for primary call
            ProductInfo productInfo = productCatalogFeignClient.getProductById(productId);

            if (productInfo != null && !"UNAVAILABLE".equals(productInfo.getStatus())) {
                return productInfo;
            }

            log.warn("Product not found or unavailable: {}", productId);
            return null;

        } catch (Exception e) {
            log.error("Error fetching product info for {}: {}", productId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get multiple products information
     */
    public Map<String, ProductInfo> getProductsInfo(List<String> productIds) {
        try {
            log.debug("Fetching product info for {} products", productIds.size());
            
            String url = productCatalogServiceUrl + "/api/products/batch";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, productIds, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Map<String, ProductInfo> result = new java.util.HashMap<>();
                
                for (Map.Entry<String, Object> entry : responseBody.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        ProductInfo productInfo = mapToProductInfo((Map<String, Object>) entry.getValue());
                        if (productInfo != null) {
                            result.put(entry.getKey(), productInfo);
                        }
                    }
                }
                
                return result;
            }
            
            return new java.util.HashMap<>();
            
        } catch (Exception e) {
            log.error("Error fetching multiple products info: {}", e.getMessage(), e);
            return new java.util.HashMap<>();
        }
    }

    // ==================== PRICING INFORMATION ====================

    /**
     * Get current product price
     */
    @Cacheable(value = "productPrice", key = "#productId", unless = "#result == null")
    public BigDecimal getCurrentPrice(String productId) {
        try {
            log.debug("Fetching current price for product: {}", productId);
            
            String url = productCatalogServiceUrl + "/api/products/" + productId + "/price";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> priceData = response.getBody();
                Object priceObj = priceData.get("currentPrice");
                
                if (priceObj instanceof Number) {
                    return BigDecimal.valueOf(((Number) priceObj).doubleValue());
                } else if (priceObj instanceof String) {
                    return new BigDecimal((String) priceObj);
                }
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error fetching current price for {}: {}", productId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get product pricing with discounts
     */
    public PricingInfo getProductPricing(String productId, String userId) {
        try {
            log.debug("Fetching pricing info for product: {} and user: {}", productId, userId);
            
            String url = productCatalogServiceUrl + "/api/products/" + productId + "/pricing";
            if (userId != null) {
                url += "?userId=" + userId;
            }
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return mapToPricingInfo(response.getBody());
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error fetching pricing info for {}: {}", productId, e.getMessage(), e);
            return null;
        }
    }

    // ==================== INVENTORY INFORMATION ====================

    /**
     * Check product availability
     */
    @Cacheable(value = "productAvailability", key = "#productId", unless = "#result == null")
    public AvailabilityInfo getProductAvailability(String productId) {
        try {
            log.debug("Checking availability for product: {}", productId);
            
            String url = productCatalogServiceUrl + "/api/products/" + productId + "/availability";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return mapToAvailabilityInfo(response.getBody());
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error checking availability for {}: {}", productId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Reserve product quantity
     */
    public boolean reserveProductQuantity(String productId, int quantity, String reservationId) {
        try {
            log.debug("Reserving {} units of product {} with reservation {}", quantity, productId, reservationId);
            
            String url = productCatalogServiceUrl + "/api/products/" + productId + "/reserve";
            Map<String, Object> request = Map.of(
                "quantity", quantity,
                "reservationId", reservationId
            );
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                return Boolean.TRUE.equals(responseBody.get("success"));
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error reserving product quantity for {}: {}", productId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Release product reservation
     */
    public boolean releaseProductReservation(String productId, String reservationId) {
        try {
            log.debug("Releasing reservation {} for product {}", reservationId, productId);
            
            String url = productCatalogServiceUrl + "/api/products/" + productId + "/release";
            Map<String, Object> request = Map.of("reservationId", reservationId);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                return Boolean.TRUE.equals(responseBody.get("success"));
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error releasing product reservation for {}: {}", productId, e.getMessage(), e);
            return false;
        }
    }

    // ==================== PRODUCT VALIDATION ====================

    /**
     * Validate product exists and is active
     */
    public boolean validateProduct(String productId) {
        try {
            ProductInfo productInfo = getProductInfo(productId);
            return productInfo != null && "ACTIVE".equals(productInfo.getStatus());
        } catch (Exception e) {
            log.error("Error validating product {}: {}", productId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validate product variant
     */
    public boolean validateProductVariant(String productId, String variantId) {
        try {
            log.debug("Validating variant {} for product {}", variantId, productId);
            
            String url = productCatalogServiceUrl + "/api/products/" + productId + "/variants/" + variantId;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            return response.getStatusCode() == HttpStatus.OK;
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            log.error("Error validating product variant: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Error validating product variant: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== HELPER METHODS ====================

    private ProductInfo mapToProductInfo(Map<String, Object> productData) {
        try {
            return ProductInfo.builder()
                    .sku(getStringValue(productData, "sku"))
                    .name(getStringValue(productData, "name"))
                    .description(getStringValue(productData, "description"))
                    .imageUrl(getStringValue(productData, "imageUrl"))
                    .categoryId(getStringValue(productData, "categoryId"))
                    .categoryName(getStringValue(productData, "categoryName"))
                    .price(getBigDecimalValue(productData, "price"))
                    .originalPrice(getBigDecimalValue(productData, "originalPrice"))
                    .stockQuantity(getIntegerValue(productData, "stockQuantity"))
                    .status(getStringValue(productData, "status"))
                    .build();
            
        } catch (Exception e) {
            log.error("Error mapping product data: {}", e.getMessage(), e);
            return null;
        }
    }

    private PricingInfo mapToPricingInfo(Map<String, Object> pricingData) {
        return PricingInfo.builder()
                .basePrice(getBigDecimalValue(pricingData, "basePrice"))
                .currentPrice(getBigDecimalValue(pricingData, "currentPrice"))
                .discountAmount(getBigDecimalValue(pricingData, "discountAmount"))
                .discountPercentage(getBigDecimalValue(pricingData, "discountPercentage"))
                .isOnSale(getBooleanValue(pricingData, "isOnSale"))
                .build();
    }

    private AvailabilityInfo mapToAvailabilityInfo(Map<String, Object> availabilityData) {
        return AvailabilityInfo.builder()
                .isAvailable(getBooleanValue(availabilityData, "isAvailable"))
                .stockQuantity(getIntegerValue(availabilityData, "stockQuantity"))
                .maxQuantityPerOrder(getIntegerValue(availabilityData, "maxQuantityPerOrder"))
                .estimatedRestockDate(getStringValue(availabilityData, "estimatedRestockDate"))
                .build();
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        } else if (value instanceof String) {
            try {
                return new BigDecimal((String) value);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private Boolean getBooleanValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    private Integer getIntegerValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    // ==================== INNER CLASSES ====================

    @lombok.Builder
    @lombok.Data
    public static class PricingInfo {
        private BigDecimal basePrice;
        private BigDecimal currentPrice;
        private BigDecimal discountAmount;
        private BigDecimal discountPercentage;
        private Boolean isOnSale;
    }

    @lombok.Builder
    @lombok.Data
    public static class AvailabilityInfo {
        private Boolean isAvailable;
        private Integer stockQuantity;
        private Integer maxQuantityPerOrder;
        private String estimatedRestockDate;
    }
}
