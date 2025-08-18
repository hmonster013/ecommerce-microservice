package org.de013.shoppingcart.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.client.UserServiceFeignClient;
import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.enums.CartType;
import org.de013.shoppingcart.repository.jpa.CartRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Cart Ownership Service
 * Handles cart ownership validation, transfer, and security
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartOwnershipService {

    private final CartRepository cartRepository;
    private final UserServiceFeignClient userServiceFeignClient;
    private final CartSecurityService cartSecurityService;

    // ==================== OWNERSHIP VALIDATION ====================

    /**
     * Validate cart ownership
     */
    public boolean validateCartOwnership(Long cartId, Authentication authentication) {
        try {
            Cart cart = cartRepository.findById(cartId).orElse(null);
            if (cart == null) {
                log.warn("Cart not found for ownership validation: {}", cartId);
                return false;
            }

            return validateCartOwnership(cart, authentication);

        } catch (Exception e) {
            log.error("Error validating cart ownership for cart {}: {}", cartId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validate cart ownership with cart entity
     */
    public boolean validateCartOwnership(Cart cart, Authentication authentication) {
        try {
            if (cart == null || authentication == null) {
                return false;
            }

            // Use simplified access control
            return cartSecurityService.canAccessCart(authentication, cart.getUserId());



        } catch (Exception e) {
            log.error("Error validating cart ownership: {}", e.getMessage(), e);
            return false;
        }
    }



    // ==================== CART TRANSFER ====================

    /**
     * Transfer cart ownership from guest to user (session managed by User Service)
     */
    @Transactional
    public Map<String, Object> transferGuestCartToUser(Long cartId, String userId, String newSessionId) {
        try {
            log.info("Transferring guest cart {} to user {}", cartId, userId);

            Cart cart = cartRepository.findById(cartId).orElse(null);
            if (cart == null) {
                return createErrorResponse("Cart not found", "CART_NOT_FOUND");
            }

            // Validate it's a guest cart
            if (cart.getCartType() != CartType.GUEST || cart.getUserId() != null) {
                return createErrorResponse("Cart is not a guest cart", "INVALID_CART_TYPE");
            }

            // Validate user exists
            if (!cartSecurityService.userExists(userId)) {
                return createErrorResponse("User not found", "USER_NOT_FOUND");
            }

            // Transfer ownership
            cart.setUserId(userId);
            cart.setCartType(CartType.USER);
            cart.setUpdatedAt(LocalDateTime.now());

            cartRepository.save(cart);

            log.info("Successfully transferred cart {} to user {}", cartId, userId);

            return Map.of(
                "success", true,
                "cartId", cartId,
                "userId", userId,
                "message", "Cart transferred successfully",
                "timestamp", LocalDateTime.now()
            );

        } catch (Exception e) {
            log.error("Error transferring guest cart {} to user {}: {}", cartId, userId, e.getMessage(), e);
            return createErrorResponse("Transfer failed: " + e.getMessage(), "TRANSFER_ERROR");
        }
    }

    /**
     * Transfer cart ownership between users (admin operation)
     */
    @Transactional
    public Map<String, Object> transferCartBetweenUsers(Long cartId, String fromUserId, String toUserId, 
                                                       Authentication authentication) {
        try {
            // Only admin can transfer carts between users
            if (!roleBasedAccessControl.hasRole(authentication, "ADMIN")) {
                return createErrorResponse("Insufficient privileges", "ACCESS_DENIED");
            }

            log.info("Admin transferring cart {} from user {} to user {}", cartId, fromUserId, toUserId);

            Cart cart = cartRepository.findById(cartId).orElse(null);
            if (cart == null) {
                return createErrorResponse("Cart not found", "CART_NOT_FOUND");
            }

            // Validate current ownership
            if (!fromUserId.equals(cart.getUserId())) {
                return createErrorResponse("Cart does not belong to source user", "INVALID_OWNERSHIP");
            }

            // Validate target user exists
            if (!validateUserExists(toUserId)) {
                return createErrorResponse("Target user not found", "USER_NOT_FOUND");
            }

            // Transfer ownership
            cart.setUserId(toUserId);
            cart.setUpdatedAt(LocalDateTime.now());

            cartRepository.save(cart);

            // Notify User Service about the transfer
            notifyUserServiceOfTransfer(cartId, fromUserId, toUserId);

            log.info("Successfully transferred cart {} from user {} to user {}", cartId, fromUserId, toUserId);

            return Map.of(
                "success", true,
                "cartId", cartId,
                "fromUserId", fromUserId,
                "toUserId", toUserId,
                "message", "Cart transferred successfully",
                "timestamp", LocalDateTime.now()
            );

        } catch (Exception e) {
            log.error("Error transferring cart {} from {} to {}: {}", cartId, fromUserId, toUserId, e.getMessage(), e);
            return createErrorResponse("Transfer failed: " + e.getMessage(), "TRANSFER_ERROR");
        }
    }

    // ==================== OWNERSHIP VERIFICATION ====================

    /**
     * Get cart ownership information
     */
    public Map<String, Object> getCartOwnershipInfo(Long cartId, Authentication authentication) {
        try {
            Cart cart = cartRepository.findById(cartId).orElse(null);
            if (cart == null) {
                return createErrorResponse("Cart not found", "CART_NOT_FOUND");
            }

            // Check if user can access this information
            if (!validateCartOwnership(cart, authentication) && 
                !roleBasedAccessControl.hasElevatedPrivileges(authentication)) {
                return createErrorResponse("Access denied", "ACCESS_DENIED");
            }

            Map<String, Object> ownershipInfo = new HashMap<>();
            ownershipInfo.put("cartId", cartId);
            ownershipInfo.put("cartType", cart.getCartType());
            ownershipInfo.put("userId", cart.getUserId());
            ownershipInfo.put("isGuest", cart.getCartType() == CartType.GUEST);
            ownershipInfo.put("createdAt", cart.getCreatedAt());
            ownershipInfo.put("lastActivity", cart.getLastActivityAt());

            // Add current user's access level
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                ownershipInfo.put("currentUserId", jwtAuth.getUserId());
                ownershipInfo.put("currentUserIsGuest", jwtAuth.isGuest());
                ownershipInfo.put("hasOwnership", validateCartOwnership(cart, authentication));
                ownershipInfo.put("hasElevatedAccess", roleBasedAccessControl.hasElevatedPrivileges(authentication));
            }

            return ownershipInfo;

        } catch (Exception e) {
            log.error("Error getting cart ownership info for {}: {}", cartId, e.getMessage(), e);
            return createErrorResponse("Error retrieving ownership info", "RETRIEVAL_ERROR");
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Validate user exists
     */
    private boolean validateUserExists(String userId) {
        try {
            Map<String, Object> userCheck = userServiceFeignClient.checkUserExists(userId);
            return Boolean.TRUE.equals(userCheck.get("exists")) && 
                   !Boolean.TRUE.equals(userCheck.get("fallback"));
        } catch (Exception e) {
            log.error("Error validating user existence {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Notify User Service of cart transfer
     */
    private void notifyUserServiceOfTransfer(Long cartId, String fromUserId, String toUserId) {
        try {
            userServiceFeignClient.transferCartOwnership(fromUserId, cartId, toUserId);
        } catch (Exception e) {
            log.warn("Failed to notify User Service of cart transfer: {}", e.getMessage());
        }
    }

    /**
     * Create error response
     */
    private Map<String, Object> createErrorResponse(String message, String errorCode) {
        return Map.of(
            "success", false,
            "error", message,
            "errorCode", errorCode,
            "timestamp", LocalDateTime.now()
        );
    }

    /**
     * Check if cart can be accessed by current user
     */
    public boolean canAccessCart(Long cartId, Authentication authentication) {
        return validateCartOwnership(cartId, authentication);
    }

    /**
     * Check if cart can be modified by current user
     */
    public boolean canModifyCart(Long cartId, Authentication authentication) {
        if (!validateCartOwnership(cartId, authentication)) {
            return false;
        }

        // Additional checks for modification permissions
        return roleBasedAccessControl.hasPermission(authentication, "cart:write") ||
               roleBasedAccessControl.hasPermission(authentication, "cart:write:guest");
    }

    /**
     * Check if cart can be deleted by current user
     */
    public boolean canDeleteCart(Long cartId, Authentication authentication) {
        if (!validateCartOwnership(cartId, authentication)) {
            return false;
        }

        return roleBasedAccessControl.hasPermission(authentication, "cart:delete") ||
               roleBasedAccessControl.hasRole(authentication, "ADMIN");
    }
}
