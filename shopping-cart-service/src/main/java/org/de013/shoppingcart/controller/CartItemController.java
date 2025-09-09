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
import org.de013.common.security.UserContext;
import org.de013.common.security.UserContextHolder;
import org.de013.shoppingcart.dto.request.AddToCartDto;
import org.de013.shoppingcart.dto.request.RemoveFromCartDto;
import org.de013.shoppingcart.dto.request.UpdateCartItemDto;
import org.de013.shoppingcart.dto.response.CartItemResponseDto;
import org.de013.shoppingcart.dto.response.CartResponseDto;
import org.de013.shoppingcart.service.CartItemService;
import org.de013.shoppingcart.service.CartService;
import org.de013.common.constant.ApiPaths;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for Cart Item Operations
 * Provides endpoints for managing items within shopping carts
 */
@RestController
@RequestMapping(ApiPaths.CART_ITEMS) // Gateway routes /api/v1/cart/** to /cart/**
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart Items", description = "APIs for cart item management")
public class CartItemController {

    private final CartItemService cartItemService;
    private final CartService cartService;

    // ==================== ITEM ADDITION ====================

    @Operation(summary = "Add item to cart 🔐 (Authenticated)", description = "Add a product item to the shopping cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Item added successfully",
                content = @Content(schema = @Schema(implementation = CartItemResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Cart or product not found"),
        @ApiResponse(responseCode = "409", description = "Item already exists in cart"),
        @ApiResponse(responseCode = "422", description = "Validation failed"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<CartItemResponseDto> addItemToCart(
            @Parameter(description = "Add to cart request", required = true)
            @Valid @RequestBody AddToCartDto request) {
        
        try {
            log.debug("Adding item {} to cart", request.getProductId());

            // Get user context from API Gateway headers
            UserContext userContext = UserContextHolder.getCurrentUser();
            String userId = null;

            if (userContext != null) {
                userId = String.valueOf(userContext.getUserId());
                log.debug("Adding item for authenticated user: {} (ID: {})",
                         userContext.getUsername(), userId);
            } else {
                log.debug("Adding item for guest session: {}", request.getSessionId());
            }

            // Get or create cart first
            CartResponseDto cart = cartService.getOrCreateCart(userId, request.getSessionId());

            // Add item to cart
            CartItemResponseDto cartItem = cartItemService.addItemToCart(cart.getCartId(), request);

            // Update cart totals
            cartService.updateCartTotals(cart.getCartId());

            return ResponseEntity.status(HttpStatus.CREATED).body(cartItem);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("already exists")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            } else if (e.getMessage().contains("validation")) {
                return ResponseEntity.unprocessableEntity().build();
            }
            log.error("Error adding item to cart: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== ITEM UPDATES ====================

    @Operation(summary = "Update cart item 🔐 (Authenticated)", description = "Update quantity, price, or other properties of a cart item")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item updated successfully",
                content = @Content(schema = @Schema(implementation = CartItemResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Cart item not found"),
        @ApiResponse(responseCode = "422", description = "Validation failed"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping(ApiPaths.ITEM_ID_PARAM)
    public ResponseEntity<CartItemResponseDto> updateCartItem(
            @Parameter(description = "Cart item ID", required = true) 
            @PathVariable Long itemId,
            @Parameter(description = "Update cart item request", required = true)
            @Valid @RequestBody UpdateCartItemDto request) {
        
        try {
            log.debug("Updating cart item: {}", itemId);
            
            // Set item ID from path parameter
            request.setItemId(itemId);
            
            CartItemResponseDto cartItem = cartItemService.updateCartItem(request);
            
            // Update cart totals if quantity or price changed
            if (request.getQuantity() != null || request.getUnitPrice() != null) {
                // Get cart ID from the updated item (would need to be returned from service)
                // cartService.updateCartTotals(cartItem.getCartId());
            }
            
            return ResponseEntity.ok(cartItem);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("validation")) {
                return ResponseEntity.unprocessableEntity().build();
            }
            log.error("Error updating cart item {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update item quantity 🔐 (Authenticated)", description = "Update only the quantity of a cart item")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quantity updated successfully",
                content = @Content(schema = @Schema(implementation = CartItemResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid quantity"),
        @ApiResponse(responseCode = "404", description = "Cart item not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PatchMapping(ApiPaths.ITEM_ID_PARAM + ApiPaths.QUANTITY)
    public ResponseEntity<CartItemResponseDto> updateItemQuantity(
            @Parameter(description = "Cart item ID", required = true) 
            @PathVariable Long itemId,
            @Parameter(description = "New quantity", required = true)
            @RequestParam Integer quantity) {
        
        try {
            log.debug("Updating quantity for cart item {}: {}", itemId, quantity);
            
            if (quantity <= 0) {
                return ResponseEntity.badRequest().build();
            }
            
            UpdateCartItemDto request = UpdateCartItemDto.builder()
                    .itemId(itemId)
                    .quantity(quantity)
                    .build();
            
            CartItemResponseDto cartItem = cartItemService.updateCartItem(request);
            return ResponseEntity.ok(cartItem);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Error updating item quantity for {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== ITEM REMOVAL ====================

    @Operation(summary = "Remove item from cart", description = "Remove a specific item from the cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Item removed successfully"),
        @ApiResponse(responseCode = "404", description = "Cart item not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping(ApiPaths.ITEM_ID_PARAM)
    public ResponseEntity<Void> removeCartItem(
            @Parameter(description = "Cart item ID", required = true) 
            @PathVariable Long itemId) {
        
        try {
            log.debug("Removing cart item: {}", itemId);
            
            RemoveFromCartDto request = RemoveFromCartDto.builder()
                    .itemId(itemId)
                    .build();
            
            boolean removed = cartItemService.removeItemFromCart(request);
            
            return removed ? ResponseEntity.noContent().build() : 
                           ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("Error removing cart item {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Remove multiple items", description = "Remove multiple items from cart in bulk")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Items removed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping(ApiPaths.BULK)
    public ResponseEntity<Void> removeMultipleItems(
            @Parameter(description = "Bulk removal request", required = true)
            @Valid @RequestBody RemoveFromCartDto request) {
        
        try {
            log.debug("Removing multiple cart items");
            
            boolean removed = cartItemService.removeItemFromCart(request);
            
            return removed ? ResponseEntity.noContent().build() : 
                           ResponseEntity.badRequest().build();
            
        } catch (Exception e) {
            log.error("Error removing multiple cart items: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== ITEM RETRIEVAL ====================

    @Operation(summary = "Get cart items", description = "Retrieve all items in a specific cart")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Items retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(ApiPaths.CART + ApiPaths.CART_ID_PARAM)
    public ResponseEntity<List<CartItemResponseDto>> getCartItems(
            @Parameter(description = "Cart ID", required = true) 
            @PathVariable Long cartId) {
        
        try {
            log.debug("Getting items for cart: {}", cartId);
            
            List<CartItemResponseDto> items = cartItemService.getCartItems(cartId);
            return ResponseEntity.ok(items);
            
        } catch (Exception e) {
            log.error("Error getting cart items for cart {}: {}", cartId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get item by ID", description = "Retrieve a specific cart item by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Item found",
                content = @Content(schema = @Schema(implementation = CartItemResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Item not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(ApiPaths.ITEM_ID_PARAM)
    public ResponseEntity<CartItemResponseDto> getCartItemById(
            @Parameter(description = "Cart item ID", required = true)
            @PathVariable Long itemId) {

        try {
            log.debug("Getting cart item by ID: {}", itemId);

            Optional<CartItemResponseDto> cartItem = cartItemService.getCartItemById(itemId);

            return cartItem.map(ResponseEntity::ok)
                          .orElse(ResponseEntity.notFound().build());

        } catch (Exception e) {
            log.error("Error getting cart item {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== ITEM VALIDATION ====================

    @Operation(summary = "Validate cart item", description = "Validate a cart item against current product data")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Validation completed"),
        @ApiResponse(responseCode = "404", description = "Item not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{itemId}/validate")
    public ResponseEntity<Map<String, Object>> validateCartItem(
            @Parameter(description = "Cart item ID", required = true) 
            @PathVariable Long itemId) {
        
        try {
            log.debug("Validating cart item: {}", itemId);
            
            // This would need to be implemented
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
            
        } catch (Exception e) {
            log.error("Error validating cart item {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==================== GIFT OPTIONS ====================

    @Operation(summary = "Update gift options", description = "Update gift wrapping and message for a cart item")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Gift options updated successfully",
                content = @Content(schema = @Schema(implementation = CartItemResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Item not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PatchMapping(ApiPaths.ITEM_ID_PARAM + ApiPaths.GIFT)
    public ResponseEntity<CartItemResponseDto> updateGiftOptions(
            @Parameter(description = "Cart item ID", required = true) 
            @PathVariable Long itemId,
            @Parameter(description = "Gift options", required = true)
            @RequestBody Map<String, Object> giftOptions) {
        
        try {
            log.debug("Updating gift options for cart item: {}", itemId);
            
            UpdateCartItemDto request = UpdateCartItemDto.builder()
                    .itemId(itemId)
                    .isGift((Boolean) giftOptions.get("isGift"))
                    .giftMessage((String) giftOptions.get("giftMessage"))
                    .giftWrapType((String) giftOptions.get("giftWrapType"))
                    .build();
            
            CartItemResponseDto cartItem = cartItemService.updateCartItem(request);
            return ResponseEntity.ok(cartItem);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }
            log.error("Error updating gift options for item {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
