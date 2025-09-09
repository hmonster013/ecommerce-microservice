package org.de013.orderservice.controller;

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
import org.de013.common.constant.ApiPaths;
import org.de013.common.security.UserContext;
import org.de013.common.security.UserContextHolder;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.request.UpdateOrderRequest;
import org.de013.orderservice.dto.request.CancelOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Basic Order Controller - Core CRUD operations only
 */
@RestController
@RequestMapping(ApiPaths.ORDERS)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Order Management", description = "APIs for order management including creation, retrieval, updates, and cancellation")
public class OrderController {

    private final OrderService orderService;

    /**
     * Create a new order
     */
    @Operation(summary = "Create order", description = "Create a new order from shopping cart. Converts cart items to order items and initializes order with PENDING status. **🔐 (Authenticated Users)** - Requires valid JWT token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Order created successfully",
                content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data or empty cart"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "404", description = "Cart not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    @PreAuthorize("@orderSecurity.isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @Parameter(description = "Order creation request", required = true)
            @Valid @RequestBody CreateOrderRequest request) {
        UserContext userContext = UserContextHolder.getCurrentUser();
        if (userContext != null) {
            log.info("Creating order for user: {} (ID: {})", userContext.getUsername(), userContext.getUserId());
        }
        return orderService.createOrder(request);
    }

    /**
     * Get order by ID
     */
    @Operation(summary = "Get order by ID", description = "Retrieve order details by its unique identifier. **🔐 (Order Owner or Admin)** - Accessible by order owner or admin only")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order found",
                content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied - not order owner or admin"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(ApiPaths.ORDER_ID_PARAM)
    @PreAuthorize("@orderSecurity.canAccessOrder(#orderId)")
    public OrderResponse getOrder(
            @Parameter(description = "Order ID", required = true, example = "1")
            @PathVariable Long orderId) {
        log.debug("Getting order {}", orderId);
        return orderService.getOrderById(orderId);
    }

    /**
     * Get current user's orders
     */
    @Operation(summary = "Get my orders", description = "Retrieve paginated list of orders for the authenticated user. Returns orders sorted by creation date (newest first). **🔐 (Authenticated Users)** - Requires valid JWT token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(ApiPaths.MY_ORDERS)
    @PreAuthorize("@orderSecurity.isAuthenticated()")
    public Page<OrderResponse> getMyOrders(
            @Parameter(description = "Pagination parameters") Pageable pageable) {
        UserContext userContext = UserContextHolder.requireAuthenticated();
        log.info("User {} requesting their orders", userContext.getUsername());
        return orderService.listOrdersByUser(userContext.getUserId(), pageable);
    }

    /**
     * Get orders by user ID (admin only)
     */
    @Operation(summary = "Get user orders", description = "Retrieve paginated list of orders for a specific user. **🔐 (Admin or Own Orders)** - Admin access required or user accessing their own orders")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin required"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM)
    @PreAuthorize("@orderSecurity.canAccessUserOrders(#userId)")
    public Page<OrderResponse> getUserOrders(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "Pagination parameters") Pageable pageable) {
        log.debug("Getting orders for user {}", userId);
        return orderService.listOrdersByUser(userId, pageable);
    }

    /**
     * Get all orders (admin only)
     */
    @Operation(summary = "Get all orders", description = "Retrieve paginated list of all orders in the system. **🔐 (Admin Only)** - Requires ADMIN role. Useful for order management and reporting")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied - admin required"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping
    @PreAuthorize("@orderSecurity.isAdmin()")
    public Page<OrderResponse> getAllOrders(
            @Parameter(description = "Pagination parameters") Pageable pageable) {
        log.debug("Admin getting all orders");
        return orderService.listAllOrders(pageable);
    }

    /**
     * Get order by order number
     */
    @Operation(summary = "Get order by number", description = "Retrieve order details by its unique order number (e.g., ORD-12345678). **🔐 (Authenticated Users)** - Accessible by order owner or admin")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order found",
                content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied - not order owner or admin"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(ApiPaths.NUMBER + ApiPaths.ORDER_NUMBER_PARAM)
    @PreAuthorize("@orderSecurity.isAuthenticated()")
    public OrderResponse getOrderByNumber(
            @Parameter(description = "Order number", required = true, example = "ORD-12345678")
            @PathVariable String orderNumber) {
        log.debug("Getting order by number: {}", orderNumber);
        return orderService.getOrderByNumber(orderNumber);
    }

    /**
     * Update order (admin only for now)
     */
    @Operation(summary = "Update order", description = "Update order details such as status, addresses, or customer notes. **🔐 (Admin or Order Owner)** - Admin access required or order owner for limited updates")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order updated successfully",
                content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data or update not allowed for current status"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied - insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping(ApiPaths.ORDER_ID_PARAM)
    @PreAuthorize("@orderSecurity.canModifyOrder(#orderId)")
    public OrderResponse updateOrder(
            @Parameter(description = "Order ID", required = true, example = "1")
            @PathVariable Long orderId,
            @Parameter(description = "Order update request", required = true)
            @Valid @RequestBody UpdateOrderRequest request) {
        log.debug("Updating order {}", orderId);
        return orderService.updateOrder(orderId, request);
    }

    /**
     * Cancel order
     */
    @Operation(summary = "Cancel order", description = "Cancel an existing order. Only allowed for orders in PENDING, CONFIRMED, or PAID status. **🔐 (Admin or Order Owner)** - Cancelled orders cannot be restored")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Order cancelled successfully"),
        @ApiResponse(responseCode = "400", description = "Order cannot be cancelled in current status"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied - insufficient permissions"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping(ApiPaths.ORDER_ID_PARAM)
    @PreAuthorize("@orderSecurity.canModifyOrder(#orderId)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(
            @Parameter(description = "Order ID", required = true, example = "1")
            @PathVariable Long orderId,
            @Parameter(description = "Cancellation request with optional reason")
            @RequestBody(required = false) CancelOrderRequest request) {
        log.debug("Cancelling order {}", orderId);
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "Cancelled by user";
        orderService.cancelOrder(orderId, reason);
    }
}

