package org.de013.shoppingcart.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.client.UserServiceFeignClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cart Security Service
 * Lightweight security service focused on cart-specific operations
 * Delegates user authentication to User Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartSecurityService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserServiceFeignClient userServiceFeignClient;

    private static final String GUEST_SESSION_PREFIX = "cart_guest_session:";
    private static final Duration GUEST_SESSION_TTL = Duration.ofHours(2);

    // ==================== GUEST SESSION MANAGEMENT ====================

    /**
     * Create guest session for anonymous cart access
     */
    public String createGuestSession() {
        try {
            String sessionId = UUID.randomUUID().toString();
            String key = GUEST_SESSION_PREFIX + sessionId;
            
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("sessionId", sessionId);
            sessionData.put("createdAt", LocalDateTime.now().toString());
            sessionData.put("isGuest", true);
            sessionData.put("lastActivity", LocalDateTime.now().toString());
            
            redisTemplate.opsForValue().set(key, sessionData, GUEST_SESSION_TTL);
            
            log.debug("Created guest session for cart access: {}", sessionId);
            return sessionId;
            
        } catch (Exception e) {
            log.error("Error creating guest session: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Validate guest session
     */
    public boolean isValidGuestSession(String sessionId) {
        try {
            if (sessionId == null) {
                return false;
            }
            
            String key = GUEST_SESSION_PREFIX + sessionId;
            Object sessionData = redisTemplate.opsForValue().get(key);
            
            if (sessionData != null) {
                updateGuestSessionActivity(sessionId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error validating guest session {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Update guest session activity
     */
    public void updateGuestSessionActivity(String sessionId) {
        try {
            String key = GUEST_SESSION_PREFIX + sessionId;
            Object sessionData = redisTemplate.opsForValue().get(key);
            
            if (sessionData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) sessionData;
                data.put("lastActivity", LocalDateTime.now().toString());
                redisTemplate.opsForValue().set(key, data, GUEST_SESSION_TTL);
            }
            
        } catch (Exception e) {
            log.error("Error updating guest session activity {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Remove guest session
     */
    public void removeGuestSession(String sessionId) {
        try {
            String key = GUEST_SESSION_PREFIX + sessionId;
            redisTemplate.delete(key);
            log.debug("Removed guest session: {}", sessionId);
        } catch (Exception e) {
            log.error("Error removing guest session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Get guest session info
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getGuestSessionInfo(String sessionId) {
        try {
            String key = GUEST_SESSION_PREFIX + sessionId;
            Object sessionData = redisTemplate.opsForValue().get(key);
            
            if (sessionData instanceof Map) {
                return (Map<String, Object>) sessionData;
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error getting guest session info for {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    // ==================== CART ACCESS CONTROL ====================

    /**
     * Check if user can access cart (simplified)
     */
    public boolean canAccessCart(Authentication authentication, String cartUserId) {
        if (authentication == null) {
            return false;
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            // Admin can access any cart (delegate to User Service for role checking)
            if (hasAdminRole(jwtAuth)) {
                return true;
            }
            
            // Guest users can access carts with no userId
            if (jwtAuth.isGuest()) {
                return cartUserId == null;
            }
            
            // Regular users can access their own carts
            return jwtAuth.getUserId() != null && jwtAuth.getUserId().equals(cartUserId);
        }

        return false;
    }

    /**
     * Check if user can modify cart
     */
    public boolean canModifyCart(Authentication authentication, String cartUserId) {
        return canAccessCart(authentication, cartUserId);
    }

    /**
     * Check if user can delete cart
     */
    public boolean canDeleteCart(Authentication authentication, String cartUserId) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            // Admin can delete any cart
            if (hasAdminRole(jwtAuth)) {
                return true;
            }
            
            // Users can delete their own carts
            return canAccessCart(authentication, cartUserId);
        }
        
        return false;
    }

    // ==================== USER SERVICE INTEGRATION ====================

    /**
     * Validate user session with User Service
     */
    public boolean validateUserSession(String sessionId, String userId) {
        try {
            if (sessionId == null || userId == null) {
                return false;
            }

            Map<String, Object> validation = userServiceFeignClient.validateSession(sessionId);
            return Boolean.TRUE.equals(validation.get("valid")) && 
                   !Boolean.TRUE.equals(validation.get("fallback"));
            
        } catch (Exception e) {
            log.error("Error validating user session {} with User Service: {}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if user exists
     */
    public boolean userExists(String userId) {
        try {
            Map<String, Object> userCheck = userServiceFeignClient.checkUserExists(userId);
            return Boolean.TRUE.equals(userCheck.get("exists")) && 
                   !Boolean.TRUE.equals(userCheck.get("fallback"));
        } catch (Exception e) {
            log.error("Error checking user existence {}: {}", userId, e.getMessage());
            return false;
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Get current user ID from authentication
     */
    public String getCurrentUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getUserId();
        }
        return null;
    }

    /**
     * Get current session ID from authentication
     */
    public String getCurrentSessionId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getSessionId();
        }
        return null;
    }

    /**
     * Check if current user is guest
     */
    public boolean isCurrentUserGuest(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.isGuest();
        }
        return false;
    }

    /**
     * Simple admin role check (delegate complex role logic to User Service)
     */
    private boolean hasAdminRole(JwtAuthenticationToken jwtAuth) {
        return jwtAuth.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Validate session (guest or user)
     */
    public boolean validateSession(String sessionId, boolean isGuest, String userId) {
        if (isGuest) {
            return isValidGuestSession(sessionId);
        } else {
            return validateUserSession(sessionId, userId);
        }
    }

    /**
     * Create error response
     */
    public Map<String, Object> createErrorResponse(String message, String errorCode) {
        return Map.of(
            "success", false,
            "error", message,
            "errorCode", errorCode,
            "timestamp", LocalDateTime.now()
        );
    }

    /**
     * Create success response
     */
    public Map<String, Object> createSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("timestamp", LocalDateTime.now());
        if (data != null) {
            response.put("data", data);
        }
        return response;
    }
}
