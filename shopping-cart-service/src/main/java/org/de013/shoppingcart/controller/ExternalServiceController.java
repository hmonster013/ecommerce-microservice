package org.de013.shoppingcart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.client.ProductCatalogFeignClient;
import org.de013.shoppingcart.client.UserServiceFeignClient;
import org.de013.shoppingcart.service.RealTimePriceUpdateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * External Service Integration Controller
 * Provides endpoints for external service integration and webhooks
 */
@RestController
@RequestMapping("/api/v1/external")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "External Service Integration", description = "APIs for external service integration and webhooks")
public class ExternalServiceController {

    private final ProductCatalogFeignClient productCatalogFeignClient;
    private final UserServiceFeignClient userServiceFeignClient;
    private final RealTimePriceUpdateService priceUpdateService;

    // ==================== PRODUCT CATALOG WEBHOOKS ====================

    @Operation(summary = "Product price update webhook", description = "Receive price update notifications from Product Catalog Service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Price update processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/webhooks/product/price-update")
    public ResponseEntity<Map<String, Object>> handlePriceUpdate(@RequestBody Map<String, Object> priceUpdateData) {
        try {
            log.info("Received price update webhook: {}", priceUpdateData);
            
            String productId = (String) priceUpdateData.get("productId");
            BigDecimal newPrice = new BigDecimal(priceUpdateData.get("newPrice").toString());
            BigDecimal oldPrice = new BigDecimal(priceUpdateData.get("oldPrice").toString());
            
            // Process price update asynchronously
            priceUpdateService.processPriceUpdate(productId, newPrice, oldPrice);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Price update processed",
                "productId", productId,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing price update webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Product stock update webhook", description = "Receive stock update notifications from Product Catalog Service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock update processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/webhooks/product/stock-update")
    public ResponseEntity<Map<String, Object>> handleStockUpdate(@RequestBody Map<String, Object> stockUpdateData) {
        try {
            log.info("Received stock update webhook: {}", stockUpdateData);
            
            String productId = (String) stockUpdateData.get("productId");
            Integer newStock = (Integer) stockUpdateData.get("newStock");
            Integer oldStock = (Integer) stockUpdateData.get("oldStock");
            
            // Process stock update asynchronously
            priceUpdateService.processStockUpdate(productId, newStock, oldStock);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Stock update processed",
                "productId", productId,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing stock update webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Product availability update webhook", description = "Receive availability update notifications from Product Catalog Service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Availability update processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/webhooks/product/availability-update")
    public ResponseEntity<Map<String, Object>> handleAvailabilityUpdate(@RequestBody Map<String, Object> availabilityData) {
        try {
            log.info("Received availability update webhook: {}", availabilityData);
            
            String productId = (String) availabilityData.get("productId");
            Boolean isAvailable = (Boolean) availabilityData.get("isAvailable");
            String reason = (String) availabilityData.getOrDefault("reason", "Unknown");
            
            // Process availability update asynchronously
            priceUpdateService.processAvailabilityUpdate(productId, isAvailable, reason);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Availability update processed",
                "productId", productId,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing availability update webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    // ==================== USER SERVICE WEBHOOKS ====================

    @Operation(summary = "User status update webhook", description = "Receive user status update notifications from User Service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User status update processed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/webhooks/user/status-update")
    public ResponseEntity<Map<String, Object>> handleUserStatusUpdate(@RequestBody Map<String, Object> userStatusData) {
        try {
            log.info("Received user status update webhook: {}", userStatusData);
            
            String userId = (String) userStatusData.get("userId");
            String newStatus = (String) userStatusData.get("newStatus");
            String oldStatus = (String) userStatusData.get("oldStatus");
            
            // Handle user status changes that might affect cart access
            if ("SUSPENDED".equals(newStatus) || "BANNED".equals(newStatus)) {
                log.warn("User {} status changed to {}, may need to handle cart access", userId, newStatus);
                // Implement cart access restriction logic here
            }
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "User status update processed",
                "userId", userId,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing user status update webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    // ==================== SERVICE HEALTH CHECKS ====================

    @Operation(summary = "Check Product Catalog Service health", description = "Check the health of Product Catalog Service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service health retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Service unavailable")
    })
    @GetMapping("/health/product-catalog")
    public ResponseEntity<Map<String, Object>> checkProductCatalogHealth() {
        try {
            log.debug("Checking Product Catalog Service health");
            
            // Try to validate a test product
            Map<String, Object> healthCheck = productCatalogFeignClient.validateProduct("health-check");
            
            boolean isHealthy = !Boolean.TRUE.equals(healthCheck.get("fallback"));
            
            Map<String, Object> response = Map.of(
                "service", "product-catalog-service",
                "healthy", isHealthy,
                "timestamp", System.currentTimeMillis(),
                "details", healthCheck
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking Product Catalog Service health: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "service", "product-catalog-service",
                "healthy", false,
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @Operation(summary = "Check User Service health", description = "Check the health of User Service")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service health retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Service unavailable")
    })
    @GetMapping("/health/user-service")
    public ResponseEntity<Map<String, Object>> checkUserServiceHealth() {
        try {
            log.debug("Checking User Service health");
            
            // Try to check if a test user exists
            Map<String, Object> healthCheck = userServiceFeignClient.checkUserExists("health-check");
            
            boolean isHealthy = !Boolean.TRUE.equals(healthCheck.get("fallback"));
            
            Map<String, Object> response = Map.of(
                "service", "user-service",
                "healthy", isHealthy,
                "timestamp", System.currentTimeMillis(),
                "details", healthCheck
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking User Service health: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "service", "user-service",
                "healthy", false,
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @Operation(summary = "Check all external services health", description = "Check the health of all external services")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All services health retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "One or more services unavailable")
    })
    @GetMapping("/health/all")
    public ResponseEntity<Map<String, Object>> checkAllServicesHealth() {
        try {
            log.debug("Checking all external services health");
            
            Map<String, Object> productCatalogHealth = checkProductCatalogHealth().getBody();
            Map<String, Object> userServiceHealth = checkUserServiceHealth().getBody();
            
            boolean allHealthy = Boolean.TRUE.equals(productCatalogHealth.get("healthy")) &&
                               Boolean.TRUE.equals(userServiceHealth.get("healthy"));
            
            Map<String, Object> response = Map.of(
                "allHealthy", allHealthy,
                "services", Map.of(
                    "productCatalog", productCatalogHealth,
                    "userService", userServiceHealth
                ),
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking all services health: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "allHealthy", false,
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    // ==================== MANUAL SYNC OPERATIONS ====================

    @Operation(summary = "Sync product information", description = "Manually sync product information for a specific product")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product sync completed successfully"),
        @ApiResponse(responseCode = "404", description = "Product not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/sync/product/{productId}")
    public ResponseEntity<Map<String, Object>> syncProductInfo(
            @Parameter(description = "Product ID to sync", required = true)
            @PathVariable String productId) {
        try {
            log.info("Manual sync requested for product: {}", productId);
            
            // Get latest product information
            var productInfo = productCatalogFeignClient.getProductById(productId);
            var pricing = productCatalogFeignClient.getProductPrice(productId);
            var availability = productCatalogFeignClient.checkAvailability(productId);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "productId", productId,
                "productInfo", productInfo,
                "pricing", pricing,
                "availability", availability,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error syncing product info for {}: {}", productId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "productId", productId,
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
}
