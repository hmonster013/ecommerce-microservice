package org.de013.shoppingcart.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.security.UserContext;
import org.de013.common.security.UserContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service to handle cart-specific authorization logic
 */
@Service("cartSecurity")
@RequiredArgsConstructor
@Slf4j
public class CartSecurityService {

    /**
     * Check if current user can access the cart
     * - Admins can access any cart
     * - Customers can only access their own cart
     */
    public boolean canAccessCart(Long userId) {
        UserContext userContext = UserContextHolder.getCurrentUser();
        if (userContext == null) {
            log.debug("No user context found, denying access to cart for user {}", userId);
            return false;
        }

        // Admins can access any cart
        if (userContext.isAdmin()) {
            log.debug("Admin user {} accessing cart for user {}", userContext.getUsername(), userId);
            return true;
        }

        // Customers can only access their own cart
        boolean isOwnCart = userContext.getUserId().equals(userId);
        log.debug("User {} {} access cart for user {} (own cart: {})", 
                userContext.getUsername(), 
                isOwnCart ? "granted" : "denied", 
                userId, 
                isOwnCart);
        return isOwnCart;
    }

    /**
     * Check if current user can modify the cart
     * - Admins can modify any cart
     * - Customers can only modify their own cart
     */
    public boolean canModifyCart(Long userId) {
        return canAccessCart(userId); // Same logic for now
    }



    /**
     * Check if current user can access external services
     * - Only admins can access external service endpoints
     */
    public boolean canAccessExternalServices() {
        UserContext userContext = UserContextHolder.getCurrentUser();
        if (userContext == null) {
            log.debug("No user context found, denying access to external services");
            return false;
        }

        boolean canAccess = userContext.isAdmin();
        log.debug("User {} {} access external services (admin: {})", 
                userContext.getUsername(), 
                canAccess ? "can" : "cannot", 
                canAccess);
        return canAccess;
    }

    /**
     * Check if current user can access monitoring endpoints
     * - Only admins can access monitoring
     */
    public boolean canAccessMonitoring() {
        UserContext userContext = UserContextHolder.getCurrentUser();
        if (userContext == null) {
            log.debug("No user context found, denying access to monitoring");
            return false;
        }

        boolean canAccess = userContext.isAdmin();
        log.debug("User {} {} access monitoring (admin: {})", 
                userContext.getUsername(), 
                canAccess ? "can" : "cannot", 
                canAccess);
        return canAccess;
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

    /**
     * Get current user ID for cart operations
     */
    public Long getCurrentUserId() {
        UserContext userContext = UserContextHolder.getCurrentUser();
        return userContext != null ? userContext.getUserId() : null;
    }
}
