package org.de013.orderservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.security.UserContext;
import org.de013.common.security.UserContextHolder;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.service.OrderService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service to handle order-specific authorization logic
 */
@Service("orderSecurity")
@RequiredArgsConstructor
@Slf4j
public class OrderSecurityService {

    private final OrderService orderService;

    /**
     * Check if current user can access the order
     * - Admins can access any order
     * - Customers can only access their own orders
     */
    public boolean canAccessOrder(Long orderId) {
        UserContext userContext = UserContextHolder.getCurrentUser();
        if (userContext == null) {
            log.debug("No user context found, denying access to order {}", orderId);
            return false;
        }

        // Admins can access any order
        if (userContext.isAdmin()) {
            log.debug("Admin user {} accessing order {}", userContext.getUsername(), orderId);
            return true;
        }

        // For customers, check if they own the order
        try {
            OrderResponse order = orderService.getOrderById(orderId);
            boolean isOwner = order.getUserId().equals(userContext.getUserId());
            log.debug("User {} {} access order {} (owner: {})", 
                    userContext.getUsername(), 
                    isOwner ? "granted" : "denied", 
                    orderId, 
                    isOwner);
            return isOwner;
        } catch (Exception e) {
            log.warn("Error checking order ownership for order {}: {}", orderId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if current user can access orders for a specific user
     * - Admins can access any user's orders
     * - Customers can only access their own orders
     */
    public boolean canAccessUserOrders(Long userId) {
        UserContext userContext = UserContextHolder.getCurrentUser();
        if (userContext == null) {
            log.debug("No user context found, denying access to user {} orders", userId);
            return false;
        }

        // Admins can access any user's orders
        if (userContext.isAdmin()) {
            log.debug("Admin user {} accessing orders for user {}", userContext.getUsername(), userId);
            return true;
        }

        // Customers can only access their own orders
        boolean isOwnOrders = userContext.getUserId().equals(userId);
        log.debug("User {} {} access orders for user {} (own orders: {})", 
                userContext.getUsername(), 
                isOwnOrders ? "granted" : "denied", 
                userId, 
                isOwnOrders);
        return isOwnOrders;
    }

    /**
     * Check if current user can modify the order
     * - Only admins can modify orders
     * - In the future, might allow customers to modify their own pending orders
     */
    public boolean canModifyOrder(Long orderId) {
        UserContext userContext = UserContextHolder.getCurrentUser();
        if (userContext == null) {
            log.debug("No user context found, denying modify access to order {}", orderId);
            return false;
        }

        // Currently only admins can modify orders
        boolean canModify = userContext.isAdmin();
        log.debug("User {} {} modify order {} (admin: {})", 
                userContext.getUsername(), 
                canModify ? "can" : "cannot", 
                orderId, 
                canModify);
        return canModify;
    }

    /**
     * Check if current user is authenticated
     */
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }

    /**
     * Check if current user has admin role
     */
    public boolean isAdmin() {
        UserContext userContext = UserContextHolder.getCurrentUser();
        return userContext != null && userContext.isAdmin();
    }
}
