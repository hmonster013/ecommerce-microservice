package org.de013.shoppingcart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.dto.response.CartValidationDto;
import org.de013.shoppingcart.service.CartValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Cart Validation Operations
 * Provides endpoints for comprehensive cart and item validation
 */
@RestController
@RequestMapping("/validation") // Gateway routes /api/v1/cart/** to /cart/**
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart Validation", description = "APIs for cart and item validation")
public class CartValidationController {

    private final CartValidationService validationService;

    // ==================== CART VALIDATION ====================

    @Operation(summary = "Validate cart", description = "Perform comprehensive validation of cart and all its items")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Validation completed successfully",
                content = @Content(schema = @Schema(implementation = CartValidationDto.class))),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/cart/{cartId}")
    public ResponseEntity<CartValidationDto> validateCart(
            @Parameter(description = "Cart ID to validate", required = true) 
            @PathVariable Long cartId) {
        
        try {
            log.debug("Validating cart: {}", cartId);
            
            CartValidationDto validation = validationService.validateCart(cartId);
            return ResponseEntity.ok(validation);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Error validating cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Quick cart validation", description = "Perform quick validation check for cart readiness")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quick validation completed"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/cart/{cartId}/quick")
    public ResponseEntity<Map<String, Object>> quickValidateCart(
            @Parameter(description = "Cart ID to validate", required = true) 
            @PathVariable Long cartId) {
        
        try {
            log.debug("Quick validating cart: {}", cartId);
            
            CartValidationDto validation = validationService.validateCart(cartId);
            
            Map<String, Object> quickResult = Map.of(
                "cartId", cartId,
                "isValid", validation.getIsValid(),
                "hasErrors", validation.getHasErrors(),
                "hasWarnings", validation.getHasWarnings(),
                "validationScore", validation.getValidationScore(),
                "errorCount", validation.getErrorCount(),
                "warningCount", validation.getWarningCount(),
                "isCheckoutReady", validation.isValidationPassed()
            );
            
            return ResponseEntity.ok(quickResult);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Error in quick validation for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== ITEM VALIDATION ====================

    @Operation(summary = "Validate cart item", description = "Validate a specific cart item against current product data")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item validation completed"),
        @ApiResponse(responseCode = "404", description = "Item not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/item/{itemId}")
    public ResponseEntity<CartValidationDto.ItemValidationDto> validateCartItem(
            @Parameter(description = "Cart item ID to validate", required = true) 
            @PathVariable Long itemId) {
        
        try {
            log.debug("Validating cart item: {}", itemId);
            
            // This would need to be implemented in CartValidationService
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
            
        } catch (Exception e) {
            log.error("Error validating cart item {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== BATCH VALIDATION ====================

    @Operation(summary = "Validate multiple carts", description = "Perform validation on multiple carts in batch")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Batch validation completed"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/carts/batch")
    public ResponseEntity<Map<Long, CartValidationDto>> validateMultipleCarts(
            @Parameter(description = "List of cart IDs to validate", required = true)
            @RequestBody List<Long> cartIds) {
        
        try {
            log.debug("Batch validating {} carts", cartIds.size());
            
            if (cartIds.isEmpty() || cartIds.size() > 50) {
                return ResponseEntity.badRequest().build();
            }
            
            Map<Long, CartValidationDto> results = new java.util.HashMap<>();
            
            for (Long cartId : cartIds) {
                try {
                    CartValidationDto validation = validationService.validateCart(cartId);
                    results.put(cartId, validation);
                } catch (Exception e) {
                    log.warn("Failed to validate cart {}: {}", cartId, e.getMessage());
                    // Continue with other carts
                }
            }
            
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            log.error("Error in batch cart validation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== SPECIALIZED VALIDATIONS ====================

    @Operation(summary = "Validate pricing", description = "Validate cart pricing and detect price changes")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pricing validation completed"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/cart/{cartId}/pricing")
    public ResponseEntity<CartValidationDto.PricingValidationDto> validatePricing(
            @Parameter(description = "Cart ID to validate pricing", required = true) 
            @PathVariable Long cartId) {
        
        try {
            log.debug("Validating pricing for cart: {}", cartId);
            
            CartValidationDto validation = validationService.validateCart(cartId);
            return ResponseEntity.ok(validation.getPricingValidation());
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Error validating pricing for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Validate inventory", description = "Validate cart items against current inventory levels")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Inventory validation completed"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/cart/{cartId}/inventory")
    public ResponseEntity<CartValidationDto.InventoryValidationDto> validateInventory(
            @Parameter(description = "Cart ID to validate inventory", required = true) 
            @PathVariable Long cartId) {
        
        try {
            log.debug("Validating inventory for cart: {}", cartId);
            
            CartValidationDto validation = validationService.validateCart(cartId);
            return ResponseEntity.ok(validation.getInventoryValidation());
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Error validating inventory for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Validate shipping", description = "Validate cart shipping options and availability")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Shipping validation completed"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/cart/{cartId}/shipping")
    public ResponseEntity<CartValidationDto.ShippingValidationDto> validateShipping(
            @Parameter(description = "Cart ID to validate shipping", required = true) 
            @PathVariable Long cartId) {
        
        try {
            log.debug("Validating shipping for cart: {}", cartId);
            
            CartValidationDto validation = validationService.validateCart(cartId);
            return ResponseEntity.ok(validation.getShippingValidation());
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Error validating shipping for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Validate coupon", description = "Validate applied coupon code and discount")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Coupon validation completed"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/cart/{cartId}/coupon")
    public ResponseEntity<CartValidationDto.CouponValidationDto> validateCoupon(
            @Parameter(description = "Cart ID to validate coupon", required = true) 
            @PathVariable Long cartId) {
        
        try {
            log.debug("Validating coupon for cart: {}", cartId);
            
            CartValidationDto validation = validationService.validateCart(cartId);
            
            if (validation.getCouponValidation() != null) {
                return ResponseEntity.ok(validation.getCouponValidation());
            } else {
                return ResponseEntity.ok(CartValidationDto.CouponValidationDto.builder()
                        .couponCode(null)
                        .isValid(true)
                        .discountAmount(java.math.BigDecimal.ZERO)
                        .messages(List.of())
                        .build());
            }
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Error validating coupon for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== VALIDATION UTILITIES ====================

    @Operation(summary = "Get validation rules", description = "Get current validation rules and limits")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Validation rules retrieved"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/rules")
    public ResponseEntity<Map<String, Object>> getValidationRules() {
        
        try {
            log.debug("Getting validation rules");
            
            Map<String, Object> rules = Map.of(
                "maxItemsPerCart", 100,
                "maxQuantityPerItem", 99,
                "maxCartValue", "10000.00",
                "minItemPrice", "0.01",
                "validationTimeout", "30s",
                "priceChangeThreshold", "0.01"
            );
            
            return ResponseEntity.ok(rules);
            
        } catch (Exception e) {
            log.error("Error getting validation rules: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
