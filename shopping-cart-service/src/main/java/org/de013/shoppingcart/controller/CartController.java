package org.de013.shoppingcart.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.dto.request.ApplyCouponDto;
import org.de013.shoppingcart.dto.request.CartCheckoutDto;
import org.de013.shoppingcart.dto.response.CartResponseDto;
import org.de013.shoppingcart.dto.response.CartSummaryDto;
import org.de013.shoppingcart.dto.response.CartValidationDto;
import org.de013.shoppingcart.service.CartService;
import org.de013.shoppingcart.service.CartValidationService;
import org.de013.shoppingcart.service.PricingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for Cart Operations
 * Provides endpoints for cart management including CRUD operations, coupon management, and checkout preparation
 */
@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart Management", description = "APIs for shopping cart operations")
public class CartController {

    private final CartService cartService;
    private final CartValidationService validationService;
    private final PricingService pricingService;

    // ==================== CART RETRIEVAL ====================

    @Operation(summary = "Get or create cart", description = "Retrieve existing cart or create new one for user/session")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart retrieved successfully",
                content = @Content(schema = @Schema(implementation = CartResponseDto.class))),
        @ApiResponse(responseCode = "201", description = "New cart created",
                content = @Content(schema = @Schema(implementation = CartResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    public ResponseEntity<CartResponseDto> getOrCreateCart(
            @Parameter(description = "User ID for authenticated users") 
            @RequestParam(required = false) String userId,
            @Parameter(description = "Session ID for guest users") 
            @RequestParam(required = false) String sessionId) {
        
        try {
            log.debug("Getting or creating cart for user: {}, session: {}", userId, sessionId);
            
            if (userId == null && sessionId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            CartResponseDto cart = cartService.getOrCreateCart(userId, sessionId);
            
            // Return 201 if cart was just created, 200 if existing
            HttpStatus status = cart.getCreatedAt().equals(cart.getUpdatedAt()) ? 
                HttpStatus.CREATED : HttpStatus.OK;
            
            return ResponseEntity.status(status).body(cart);
            
        } catch (Exception e) {
            log.error("Error getting or creating cart: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get cart by ID", description = "Retrieve cart by its unique identifier")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart found",
                content = @Content(schema = @Schema(implementation = CartResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{cartId}")
    public ResponseEntity<CartResponseDto> getCartById(
            @Parameter(description = "Cart ID", required = true) 
            @PathVariable Long cartId) {
        
        try {
            log.debug("Getting cart by ID: {}", cartId);
            
            Optional<CartResponseDto> cart = cartService.getCartById(cartId);
            
            return cart.map(ResponseEntity::ok)
                      .orElse(ResponseEntity.notFound().build());
            
        } catch (Exception e) {
            log.error("Error getting cart by ID {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== CART OPERATIONS ====================

    @Operation(summary = "Clear cart", description = "Remove all items from the cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart cleared successfully",
                content = @Content(schema = @Schema(implementation = CartResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/clear")
    public ResponseEntity<CartResponseDto> clearCart(
            @Parameter(description = "User ID for authenticated users") 
            @RequestParam(required = false) String userId,
            @Parameter(description = "Session ID for guest users") 
            @RequestParam(required = false) String sessionId) {
        
        try {
            log.debug("Clearing cart for user: {}, session: {}", userId, sessionId);
            
            if (userId == null && sessionId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            CartResponseDto cart = cartService.clearCart(userId, sessionId);
            return ResponseEntity.ok(cart);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Error clearing cart: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete cart", description = "Permanently delete the cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Cart deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping
    public ResponseEntity<Void> deleteCart(
            @Parameter(description = "User ID for authenticated users") 
            @RequestParam(required = false) String userId,
            @Parameter(description = "Session ID for guest users") 
            @RequestParam(required = false) String sessionId) {
        
        try {
            log.debug("Deleting cart for user: {}, session: {}", userId, sessionId);
            
            if (userId == null && sessionId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            boolean deleted = cartService.deleteCart(userId, sessionId);
            
            return deleted ? ResponseEntity.noContent().build() : 
                           ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("Error deleting cart: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== COUPON MANAGEMENT ====================

    @Operation(summary = "Apply coupon", description = "Apply a coupon code to the cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Coupon applied successfully",
                content = @Content(schema = @Schema(implementation = CartResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid coupon or request"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/coupon")
    public ResponseEntity<CartResponseDto> applyCoupon(
            @Parameter(description = "Coupon application request", required = true)
            @Valid @RequestBody ApplyCouponDto request) {
        
        try {
            log.debug("Applying coupon {} to cart", request.getCouponCode());
            
            CartResponseDto cart = cartService.applyCoupon(request);
            return ResponseEntity.ok(cart);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Error applying coupon: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Remove coupon", description = "Remove applied coupon from the cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Coupon removed successfully",
                content = @Content(schema = @Schema(implementation = CartResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/coupon")
    public ResponseEntity<CartResponseDto> removeCoupon(
            @Parameter(description = "User ID for authenticated users") 
            @RequestParam(required = false) String userId,
            @Parameter(description = "Session ID for guest users") 
            @RequestParam(required = false) String sessionId) {
        
        try {
            log.debug("Removing coupon from cart for user: {}, session: {}", userId, sessionId);
            
            if (userId == null && sessionId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            CartResponseDto cart = cartService.removeCoupon(userId, sessionId);
            return ResponseEntity.ok(cart);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Error removing coupon: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== CART VALIDATION ====================

    @Operation(summary = "Validate cart", description = "Perform comprehensive cart validation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Validation completed",
                content = @Content(schema = @Schema(implementation = CartValidationDto.class))),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{cartId}/validate")
    public ResponseEntity<CartValidationDto> validateCart(
            @Parameter(description = "Cart ID", required = true) 
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

    // ==================== CART SUMMARY & PRICING ====================

    @Operation(summary = "Get cart summary", description = "Get detailed cart summary with pricing breakdown")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart summary retrieved",
                content = @Content(schema = @Schema(implementation = CartSummaryDto.class))),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{cartId}/summary")
    public ResponseEntity<CartSummaryDto> getCartSummary(
            @Parameter(description = "Cart ID", required = true) 
            @PathVariable Long cartId) {
        
        try {
            log.debug("Getting cart summary for cart: {}", cartId);
            
            // This would be implemented in CartService
            // For now, return a placeholder response
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
            
        } catch (Exception e) {
            log.error("Error getting cart summary for {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== CHECKOUT PREPARATION ====================

    @Operation(summary = "Prepare checkout", description = "Prepare cart for checkout with shipping and billing information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Checkout prepared successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid checkout data"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "422", description = "Cart validation failed"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/checkout/prepare")
    public ResponseEntity<Map<String, Object>> prepareCheckout(
            @Parameter(description = "Checkout preparation request", required = true)
            @Valid @RequestBody CartCheckoutDto request) {
        
        try {
            log.debug("Preparing checkout for cart");
            
            // This would be implemented in CartService
            // For now, return a placeholder response
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
            
        } catch (Exception e) {
            log.error("Error preparing checkout: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== UTILITY ENDPOINTS ====================

    @Operation(summary = "Update cart activity", description = "Update last activity timestamp for the cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Activity updated successfully"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{cartId}/activity")
    public ResponseEntity<Void> updateCartActivity(
            @Parameter(description = "Cart ID", required = true) 
            @PathVariable Long cartId) {
        
        try {
            log.debug("Updating activity for cart: {}", cartId);
            
            cartService.updateLastActivity(cartId);
            return ResponseEntity.noContent().build();
            
        } catch (Exception e) {
            log.error("Error updating cart activity for {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
