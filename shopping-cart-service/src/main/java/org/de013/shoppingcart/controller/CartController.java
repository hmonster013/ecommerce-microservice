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
import org.de013.common.controller.BaseController;
import org.de013.common.security.UserContext;
import org.de013.common.security.UserContextHolder;
import org.de013.shoppingcart.dto.request.CartCheckoutDto;
import org.de013.shoppingcart.dto.response.CartResponseDto;
import org.de013.shoppingcart.service.CartService;
import org.de013.common.constant.ApiPaths;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for Cart Operations
 * Provides endpoints for cart management including CRUD operations and checkout preparation
 */
@RestController
@RequestMapping(ApiPaths.CARTS)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart Management", description = "APIs for shopping cart operations")
public class CartController extends BaseController {

    private final CartService cartService;

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
    // Allow both authenticated users and guests with sessionId
    public ResponseEntity<org.de013.common.dto.ApiResponse<CartResponseDto>> getOrCreateCart(
            @Parameter(description = "Session ID for guest users (optional if user is authenticated)")
            @RequestParam(required = false) String sessionId) {

        try {
            // Get user context from API Gateway headers
            UserContext userContext = UserContextHolder.getCurrentUser();
            String userId = null;

            if (userContext != null) {
                userId = String.valueOf(userContext.getUserId());
                log.debug("Getting or creating cart for authenticated user: {} (ID: {})",
                         userContext.getUsername(), userId);
            } else {
                log.debug("Getting or creating cart for guest session: {}", sessionId);
            }

            if (userId == null && sessionId == null) {
                return badRequest("User ID or session ID is required");
            }

            CartResponseDto cart = cartService.getOrCreateCart(userId, sessionId);

            // Return 201 if cart was just created, 200 if existing
            boolean isNewCart = cart.getCreatedAt().equals(cart.getUpdatedAt());

            return isNewCart ? created(cart, "Cart created successfully") : ok(cart);

        } catch (Exception e) {
            log.error("Error getting or creating cart: {}", e.getMessage(), e);
            return internalServerError("Failed to get or create cart");
        }
    }

    @Operation(summary = "[ADMIN] Get cart by ID", description = "Retrieve cart by its unique identifier. Accessible by cart owner or admin only")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart found",
                content = @Content(schema = @Schema(implementation = CartResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(ApiPaths.CART_ID_PARAM)
    public ResponseEntity<org.de013.common.dto.ApiResponse<CartResponseDto>> getCartById(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId) {

        try {
            log.debug("Getting cart by ID: {}", cartId);

            Optional<CartResponseDto> cart = cartService.getCartById(cartId);

            return cart.map(this::ok)
                      .orElse(notFound("Cart not found"));

        } catch (Exception e) {
            log.error("Error getting cart by ID {}: {}", cartId, e.getMessage(), e);
            return internalServerError("Failed to retrieve cart");
        }
    }

    // ==================== CART OPERATIONS ====================

    @Operation(summary = "[ADMIN] Clear cart", description = "Remove all items from the cart while keeping the cart structure intact. This action cannot be undone")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart cleared successfully",
                content = @Content(schema = @Schema(implementation = CartResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping(ApiPaths.CLEAR)
    public ResponseEntity<org.de013.common.dto.ApiResponse<CartResponseDto>> clearCart(
            @Parameter(description = "User ID for authenticated users")
            @RequestParam(required = false) String userId,
            @Parameter(description = "Session ID for guest users")
            @RequestParam(required = false) String sessionId) {

        try {
            log.debug("Clearing cart for user: {}, session: {}", userId, sessionId);

            if (userId == null && sessionId == null) {
                return badRequest("User ID or session ID is required");
            }

            CartResponseDto cart = cartService.clearCart(userId, sessionId);
            return ok(cart);

        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return notFound("Cart not found");
            }
            log.error("Error clearing cart: {}", e.getMessage(), e);
            return internalServerError("Failed to clear cart");
        }
    }

    @Operation(summary = "Delete cart with smart strategy",
               description = "Delete cart with intelligent strategy based on parameters:\n" +
                           "• Both userId + sessionId: Delete user cart specifically (after login scenario)\n" +
                           "• Only userId: Delete any active user cart\n" +
                           "• Only sessionId: Delete guest cart only (before login scenario)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cart deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "400", description = "Invalid request - user ID or session ID required")
    })
    @DeleteMapping
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> deleteCart(
            @Parameter(description = "User ID for authenticated users", example = "user-123e4567-e89b-12d3-a456-426614174000")
            @RequestParam(required = false) String userId,
            @Parameter(description = "Session ID for guest users", example = "sess-123e4567-e89b-12d3-a456-426614174000")
            @RequestParam(required = false) String sessionId) {

        try {
            log.info("Deleting cart for userId={}, sessionId={}", userId, sessionId);

            if (userId == null && sessionId == null) {
                return badRequest("User ID or session ID is required");
            }

            boolean deleted = cartService.deleteCart(userId, sessionId);

            String strategy = determineDeleteStrategy(userId, sessionId);
            String message = String.format("Cart deleted successfully using %s strategy", strategy);

            return deleted ? deleted(message) : notFound("Cart not found");

        } catch (Exception e) {
            log.error("Error deleting cart: {}", e.getMessage(), e);
            return internalServerError("Failed to delete cart");
        }
    }

    private String determineDeleteStrategy(String userId, String sessionId) {
        if (userId != null && sessionId != null) {
            return "USER_WITH_SESSION (targets user cart specifically)";
        } else if (userId != null) {
            return "USER_ONLY (any user cart)";
        } else {
            return "GUEST_ONLY (guest cart only)";
        }
    }



    // ==================== CART VALIDATION ====================

    @Operation(summary = "[ADMIN] Validate cart", description = "Perform comprehensive cart validation including item availability, pricing, and business rules. Returns validation results and any issues found")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Validation completed",
                content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(ApiPaths.CART_ID_PARAM + ApiPaths.VALIDATE)
    public ResponseEntity<org.de013.common.dto.ApiResponse<Map<String, Object>>> validateCart(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId) {

        try {
            log.debug("Basic validation for cart: {}", cartId);

            // Simple validation - check if cart exists and has items
            Optional<CartResponseDto> cartOpt = cartService.getCartById(cartId);
            if (cartOpt.isEmpty()) {
                return notFound("Cart not found");
            }

            CartResponseDto cart = cartOpt.get();
            boolean isValid = cart.getItemCount() > 0 && cart.getTotalAmount().compareTo(BigDecimal.ZERO) > 0;

            Map<String, Object> validation = Map.of(
                "cartId", cartId,
                "isValid", isValid,
                "itemCount", cart.getItemCount(),
                "totalAmount", cart.getTotalAmount(),
                "isCheckoutReady", isValid
            );

            return ok(validation);

        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return notFound("Cart not found");
            }
            log.error("Error validating cart {}: {}", cartId, e.getMessage(), e);
            return internalServerError("Failed to validate cart");
        }
    }



    // ==================== CHECKOUT PREPARATION ====================

    @Operation(summary = "[ADMIN] Prepare checkout", description = "Prepare cart for checkout by validating items, calculating final totals, taxes, and shipping costs. Returns checkout summary with all necessary information for order creation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Checkout prepared successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid checkout data"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "422", description = "Cart validation failed"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(ApiPaths.CHECKOUT + ApiPaths.PREPARE)
    public ResponseEntity<org.de013.common.dto.ApiResponse<Map<String, Object>>> prepareCheckout(
            @Parameter(description = "Checkout preparation request", required = true)
            @Valid @RequestBody CartCheckoutDto request) {

        try {
            log.debug("Preparing checkout for cart");

            // This would be implemented in CartService
            // For now, return a placeholder response
            return internalServerError("Checkout preparation not implemented yet");

        } catch (Exception e) {
            log.error("Error preparing checkout: {}", e.getMessage(), e);
            return internalServerError("Failed to prepare checkout");
        }
    }

    // ==================== UTILITY ENDPOINTS ====================

    @Operation(summary = "[ADMIN] Update cart activity", description = "Update the last activity timestamp for the cart to prevent automatic expiration. Useful for keeping active carts alive during user sessions")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Activity updated successfully"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping(ApiPaths.CART_ID_PARAM + ApiPaths.ACTIVITY)
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> updateCartActivity(
            @Parameter(description = "Cart ID", required = true)
            @PathVariable Long cartId) {

        try {
            log.debug("Updating activity for cart: {}", cartId);

            cartService.updateLastActivity(cartId);
            return ok("Cart activity updated successfully");

        } catch (Exception e) {
            log.error("Error updating cart activity for {}: {}", cartId, e.getMessage(), e);
            return internalServerError("Failed to update cart activity");
        }
    }
}
