package org.de013.shoppingcart.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.security.UserContext;
import org.de013.common.security.UserContextHolder;
import org.de013.shoppingcart.service.CartService;
import org.de013.shoppingcart.dto.response.CartResponseDto;
import java.util.Optional;
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

    private final CartService cartService;

    /**
     * Check if current user can access the cart by cartId
     */
    public boolean canAccessCart(Long cartId) {
        UserContext userContext = UserContextHolder.getCurrentUser();
        if (userContext == null) {
            log.debug("No user context found, denying access to cart {}", cartId);
            return false;
        }

        try {
            Optional<CartResponseDto> cartOpt = cartService.getCartById(cartId);
            if (cartOpt.isEmpty()) {
                return true; // Let the service handle 404
            }
            
            CartResponseDto cart = cartOpt.get();
            // Allow if guest cart (no userId) or if user owns the cart
            boolean isOwner = cart.getUserId() == null || cart.getUserId().equals(userContext.getUserId());
            log.debug("User {} {} access cart {} (owner: {})",
                    userContext.getUsername(),
                    isOwner ? "granted" : "denied",
                    cartId,
                    isOwner);
            return isOwner;
        } catch (Exception e) {
            log.warn("Error checking cart ownership for cart {}: {}", cartId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if current user can access the cart by userId
     * - Admins can access any cart
     * - Customers can only access their own cart
     */
    public boolean canAccessCart(String userId) {
        UserContext userContext = UserContextHolder.getCurrentUser();
        if (userContext == null) {
            log.debug("No user context found, denying access to cart for user {}", userId);
            return false;
        }

        // Only access own cart
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
    public boolean canModifyCart(String userId) {
        return canAccessCart(userId); // Same logic for now
    }


    /**
     * Check if current user is authenticated
     */
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }

    /**
     * Get current user ID for cart operations
     */
    public String getCurrentUserId() {
        UserContext userContext = UserContextHolder.getCurrentUser();
        return userContext != null ? userContext.getUserId() : null;
    }
}
