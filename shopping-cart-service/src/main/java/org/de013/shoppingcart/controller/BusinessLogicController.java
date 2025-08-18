package org.de013.shoppingcart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.de013.shoppingcart.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Business Logic Controller
 * Provides endpoints for advanced business logic operations including pricing, validation, and calculations
 */
@RestController
@RequestMapping("/api/v1/business")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Business Logic", description = "APIs for advanced business logic operations")
public class BusinessLogicController {

    private final DynamicPricingService dynamicPricingService;
    private final TaxCalculationService taxCalculationService;
    private final ShippingCostService shippingCostService;
    private final ProductAvailabilityService productAvailabilityService;
    private final PriceChangeDetectionService priceChangeDetectionService;
    private final CartExpirationService cartExpirationService;
    private final CartValidationService cartValidationService;
    private final CartRepository cartRepository;

    // ==================== PRICING OPERATIONS ====================

    @Operation(summary = "Calculate dynamic pricing", description = "Calculate comprehensive dynamic pricing for a cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pricing calculated successfully"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/pricing/cart/{cartId}")
    public ResponseEntity<Map<String, Object>> calculateDynamicPricing(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId) {
        try {
            log.debug("Calculating dynamic pricing for cart: {}", cartId);
            
            Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
            
            Map<String, Object> pricing = dynamicPricingService.calculateCartPricing(cart);
            return ResponseEntity.ok(pricing);
            
        } catch (Exception e) {
            log.error("Error calculating dynamic pricing for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId
            ));
        }
    }

    @Operation(summary = "Calculate tax", description = "Calculate comprehensive tax for a cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tax calculated successfully"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/tax/cart/{cartId}")
    public ResponseEntity<Map<String, Object>> calculateTax(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId) {
        try {
            log.debug("Calculating tax for cart: {}", cartId);
            
            Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
            
            Map<String, Object> tax = taxCalculationService.calculateCartTax(cart);
            return ResponseEntity.ok(tax);
            
        } catch (Exception e) {
            log.error("Error calculating tax for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId
            ));
        }
    }

    @Operation(summary = "Calculate shipping costs", description = "Calculate comprehensive shipping costs for a cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Shipping costs calculated successfully"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/shipping/cart/{cartId}")
    public ResponseEntity<Map<String, Object>> calculateShipping(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId) {
        try {
            log.debug("Calculating shipping for cart: {}", cartId);
            
            Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
            
            Map<String, Object> shipping = shippingCostService.calculateShippingCosts(cart);
            return ResponseEntity.ok(shipping);
            
        } catch (Exception e) {
            log.error("Error calculating shipping for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId
            ));
        }
    }

    // ==================== VALIDATION OPERATIONS ====================

    @Operation(summary = "Enforce quantity limits", description = "Enforce quantity limits for cart item")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quantity limits validation completed"),
        @ApiResponse(responseCode = "404", description = "Cart or item not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/validation/cart/{cartId}/item/{itemId}/quantity-limits")
    public ResponseEntity<Map<String, Object>> enforceQuantityLimits(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId,
            @Parameter(description = "Item ID", required = true)
            @PathVariable Long itemId,
            @Parameter(description = "Requested quantity", required = true)
            @RequestParam Integer quantity) {
        try {
            log.debug("Enforcing quantity limits for cart: {} item: {} quantity: {}", cartId, itemId, quantity);

            Map<String, Object> validation = cartValidationService.enforceQuantityLimits(cartId, itemId, quantity);
            return ResponseEntity.ok(validation);

        } catch (Exception e) {
            log.error("Error enforcing quantity limits for cart {} item {}: {}", cartId, itemId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId,
                "itemId", itemId
            ));
        }
    }

    @Operation(summary = "Validate cart availability", description = "Validate product availability and pricing for entire cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart validation completed"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/validation/cart/{cartId}/availability")
    public ResponseEntity<Map<String, Object>> validateCartAvailability(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId) {
        try {
            log.debug("Validating availability for cart: {}", cartId);
            
            Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
            
            Map<String, Object> validation = productAvailabilityService.validateCartAvailability(cart);
            return ResponseEntity.ok(validation);
            
        } catch (Exception e) {
            log.error("Error validating cart availability for {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId
            ));
        }
    }

    @Operation(summary = "Detect price changes", description = "Detect price changes for cart items")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Price change detection completed"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/validation/cart/{cartId}/price-changes")
    public ResponseEntity<Map<String, Object>> detectPriceChanges(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId) {
        try {
            log.debug("Detecting price changes for cart: {}", cartId);
            
            Map<String, Object> priceChanges = priceChangeDetectionService.detectCartPriceChanges(cartId);
            return ResponseEntity.ok(priceChanges);
            
        } catch (Exception e) {
            log.error("Error detecting price changes for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId
            ));
        }
    }

    @Operation(summary = "Apply price changes", description = "Apply detected price changes to cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Price changes applied successfully"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/validation/cart/{cartId}/apply-price-changes")
    public ResponseEntity<Map<String, Object>> applyPriceChanges(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId,
            @Parameter(description = "Auto-approve significant changes")
            @RequestParam(value = "autoApprove", defaultValue = "false") boolean autoApprove) {
        try {
            log.info("Applying price changes for cart: {} (auto-approve: {})", cartId, autoApprove);
            
            Map<String, Object> result = priceChangeDetectionService.applyPriceChangesToCart(cartId, autoApprove);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error applying price changes for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId
            ));
        }
    }

    // ==================== STOCK OPERATIONS ====================

    @Operation(summary = "Reserve cart stock", description = "Reserve stock for all items in cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock reservation completed"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/stock/cart/{cartId}/reserve")
    public ResponseEntity<Map<String, Object>> reserveCartStock(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId,
            @Parameter(description = "Reservation TTL in minutes")
            @RequestParam(value = "ttlMinutes", defaultValue = "30") int ttlMinutes) {
        try {
            log.info("Reserving stock for cart: {} with TTL: {} minutes", cartId, ttlMinutes);
            
            Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
            
            Map<String, Object> result = productAvailabilityService.reserveCartStock(cart, ttlMinutes);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error reserving stock for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId
            ));
        }
    }

    @Operation(summary = "Release cart stock", description = "Release stock reservations for cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock reservation released"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/stock/cart/{cartId}/reserve")
    public ResponseEntity<Map<String, Object>> releaseCartStock(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId) {
        try {
            log.info("Releasing stock reservations for cart: {}", cartId);
            
            Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
            
            Map<String, Object> result = productAvailabilityService.releaseCartStock(cart);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error releasing stock for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId
            ));
        }
    }

    // ==================== EXPIRATION OPERATIONS ====================

    @Operation(summary = "Get cart expiration status", description = "Get expiration status and time remaining for cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Expiration status retrieved"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/expiration/cart/{cartId}")
    public ResponseEntity<Map<String, Object>> getCartExpirationStatus(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId) {
        try {
            log.debug("Getting expiration status for cart: {}", cartId);
            
            Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new RuntimeException("Cart not found: " + cartId));
            
            Map<String, Object> status = cartExpirationService.getTimeUntilExpiration(cart);
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error getting expiration status for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId
            ));
        }
    }

    @Operation(summary = "Extend cart expiration", description = "Extend cart expiration time")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Expiration extended successfully"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/expiration/cart/{cartId}/extend")
    public ResponseEntity<Map<String, Object>> extendCartExpiration(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId,
            @Parameter(description = "Additional hours to extend")
            @RequestParam(value = "additionalHours", defaultValue = "24") int additionalHours) {
        try {
            log.info("Extending expiration for cart: {} by {} hours", cartId, additionalHours);
            
            Map<String, Object> result = cartExpirationService.extendCartExpiration(cartId, additionalHours);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error extending expiration for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId
            ));
        }
    }

    @Operation(summary = "Refresh cart activity", description = "Refresh cart activity and extend expiration")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Activity refreshed successfully"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/expiration/cart/{cartId}/refresh")
    public ResponseEntity<Map<String, Object>> refreshCartActivity(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId) {
        try {
            log.debug("Refreshing activity for cart: {}", cartId);
            
            Map<String, Object> result = cartExpirationService.refreshCartActivity(cartId);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error refreshing activity for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartId", cartId
            ));
        }
    }

    // ==================== BATCH OPERATIONS ====================

    @Operation(summary = "Monitor multiple cart prices", description = "Monitor price changes for multiple carts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Price monitoring completed"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/validation/batch/price-monitoring")
    public ResponseEntity<Map<String, Object>> monitorMultipleCartPrices(
            @Parameter(description = "List of cart IDs to monitor")
            @RequestBody List<Long> cartIds) {
        try {
            log.info("Monitoring price changes for {} carts", cartIds.size());
            
            var result = priceChangeDetectionService.monitorMultipleCartPrices(cartIds);
            return ResponseEntity.ok(result.join());
            
        } catch (Exception e) {
            log.error("Error monitoring multiple cart prices: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage(),
                "cartCount", cartIds != null ? cartIds.size() : 0
            ));
        }
    }

    @Operation(summary = "Get expiration statistics", description = "Get comprehensive cart expiration statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/expiration/statistics")
    public ResponseEntity<Map<String, Object>> getExpirationStatistics() {
        try {
            log.debug("Getting cart expiration statistics");
            
            Map<String, Object> stats = cartExpirationService.getExpirationStatistics();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting expiration statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage()
            ));
        }
    }
}
