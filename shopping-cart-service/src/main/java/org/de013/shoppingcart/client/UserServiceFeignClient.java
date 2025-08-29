package org.de013.shoppingcart.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feign Client for User Service
 * Provides user authentication, session management, and user preferences
 */
@FeignClient(
    name = "user-service",
    path = "/users", // Service-to-service calls use internal paths
    fallback = UserServiceFallback.class
)
public interface UserServiceFeignClient {

    // ==================== USER AUTHENTICATION ====================

    /**
     * Validate user authentication token
     */
    @PostMapping("/auth/validate")
    Map<String, Object> validateAuthToken(@RequestHeader("Authorization") String authToken);

    /**
     * Get user information by ID
     */
    @GetMapping("/{userId}")
    Map<String, Object> getUserById(@PathVariable("userId") String userId);

    /**
     * Get user information by email
     */
    @GetMapping("/email/{email}")
    Map<String, Object> getUserByEmail(@PathVariable("email") String email);

    /**
     * Check if user exists
     */
    @GetMapping("/{userId}/exists")
    Map<String, Object> checkUserExists(@PathVariable("userId") String userId);

    // ==================== SESSION MANAGEMENT ====================

    /**
     * Create guest session
     */
    @PostMapping("/sessions/guest")
    Map<String, Object> createGuestSession(@RequestBody Map<String, Object> sessionData);

    /**
     * Validate session
     */
    @GetMapping("/sessions/{sessionId}/validate")
    Map<String, Object> validateSession(@PathVariable("sessionId") String sessionId);

    /**
     * Update session activity
     */
    @PutMapping("/sessions/{sessionId}/activity")
    Map<String, Object> updateSessionActivity(@PathVariable("sessionId") String sessionId);

    /**
     * Convert guest session to user session
     */
    @PostMapping("/sessions/{sessionId}/convert")
    Map<String, Object> convertGuestSession(
            @PathVariable("sessionId") String sessionId,
            @RequestParam("userId") String userId);

    /**
     * End session
     */
    @DeleteMapping("/sessions/{sessionId}")
    Map<String, Object> endSession(@PathVariable("sessionId") String sessionId);

    // ==================== USER PREFERENCES ====================

    /**
     * Get user preferences
     */
    @GetMapping("/{userId}/preferences")
    Map<String, Object> getUserPreferences(@PathVariable("userId") String userId);

    /**
     * Update user preferences
     */
    @PutMapping("/{userId}/preferences")
    Map<String, Object> updateUserPreferences(
            @PathVariable("userId") String userId,
            @RequestBody Map<String, Object> preferences);

    /**
     * Get user shopping preferences
     */
    @GetMapping("/{userId}/preferences/shopping")
    Map<String, Object> getShoppingPreferences(@PathVariable("userId") String userId);

    /**
     * Get user currency preference
     */
    @GetMapping("/{userId}/preferences/currency")
    Map<String, Object> getCurrencyPreference(@PathVariable("userId") String userId);

    /**
     * Get user language preference
     */
    @GetMapping("/{userId}/preferences/language")
    Map<String, Object> getLanguagePreference(@PathVariable("userId") String userId);

    // ==================== USER ADDRESSES ====================

    /**
     * Get user addresses
     */
    @GetMapping("/{userId}/addresses")
    List<Map<String, Object>> getUserAddresses(@PathVariable("userId") String userId);

    /**
     * Get default shipping address
     */
    @GetMapping("/{userId}/addresses/shipping/default")
    Map<String, Object> getDefaultShippingAddress(@PathVariable("userId") String userId);

    /**
     * Get default billing address
     */
    @GetMapping("/{userId}/addresses/billing/default")
    Map<String, Object> getDefaultBillingAddress(@PathVariable("userId") String userId);

    /**
     * Validate address
     */
    @PostMapping("/{userId}/addresses/validate")
    Map<String, Object> validateAddress(
            @PathVariable("userId") String userId,
            @RequestBody Map<String, Object> address);

    // ==================== USER PERMISSIONS ====================

    /**
     * Check user permissions
     */
    @GetMapping("/{userId}/permissions")
    Map<String, Object> getUserPermissions(@PathVariable("userId") String userId);

    /**
     * Check if user can purchase
     */
    @GetMapping("/{userId}/permissions/purchase")
    Map<String, Object> canUserPurchase(@PathVariable("userId") String userId);

    /**
     * Check purchase limits
     */
    @GetMapping("/{userId}/limits/purchase")
    Map<String, Object> getPurchaseLimits(@PathVariable("userId") String userId);

    /**
     * Validate purchase amount
     */
    @PostMapping("/{userId}/limits/validate")
    Map<String, Object> validatePurchaseAmount(
            @PathVariable("userId") String userId,
            @RequestParam("amount") String amount);

    // ==================== USER LOYALTY ====================

    /**
     * Get user loyalty information
     */
    @GetMapping("/{userId}/loyalty")
    Map<String, Object> getUserLoyalty(@PathVariable("userId") String userId);

    /**
     * Get loyalty points balance
     */
    @GetMapping("/{userId}/loyalty/points")
    Map<String, Object> getLoyaltyPoints(@PathVariable("userId") String userId);

    /**
     * Calculate points for purchase
     */
    @PostMapping("/{userId}/loyalty/calculate")
    Map<String, Object> calculateLoyaltyPoints(
            @PathVariable("userId") String userId,
            @RequestParam("amount") String amount);

    /**
     * Get membership level
     */
    @GetMapping("/{userId}/loyalty/membership")
    Map<String, Object> getMembershipLevel(@PathVariable("userId") String userId);

    // ==================== USER ACTIVITY ====================

    /**
     * Track user activity
     */
    @PostMapping("/{userId}/activity")
    void trackUserActivity(
            @PathVariable("userId") String userId,
            @RequestBody Map<String, Object> activityData);

    /**
     * Get user activity summary
     */
    @GetMapping("/{userId}/activity/summary")
    Map<String, Object> getUserActivitySummary(@PathVariable("userId") String userId);

    /**
     * Track cart activity
     */
    @PostMapping("/{userId}/activity/cart")
    void trackCartActivity(
            @PathVariable("userId") String userId,
            @RequestParam("cartId") Long cartId,
            @RequestParam("action") String action);

    // ==================== USER NOTIFICATIONS ====================

    /**
     * Get notification preferences
     */
    @GetMapping("/{userId}/notifications/preferences")
    Map<String, Object> getNotificationPreferences(@PathVariable("userId") String userId);

    /**
     * Send cart notification
     */
    @PostMapping("/{userId}/notifications/cart")
    Map<String, Object> sendCartNotification(
            @PathVariable("userId") String userId,
            @RequestBody Map<String, Object> notificationData);

    /**
     * Send cart abandonment notification
     */
    @PostMapping("/{userId}/notifications/cart/abandonment")
    Map<String, Object> sendAbandonmentNotification(
            @PathVariable("userId") String userId,
            @RequestParam("cartId") Long cartId);

    // ==================== USER PROFILE ====================

    /**
     * Get user profile
     */
    @GetMapping("/{userId}/profile")
    Map<String, Object> getUserProfile(@PathVariable("userId") String userId);

    /**
     * Get user contact information
     */
    @GetMapping("/{userId}/contact")
    Map<String, Object> getUserContact(@PathVariable("userId") String userId);

    /**
     * Check user status
     */
    @GetMapping("/{userId}/status")
    Map<String, Object> getUserStatus(@PathVariable("userId") String userId);

    /**
     * Get user timezone
     */
    @GetMapping("/{userId}/timezone")
    Map<String, Object> getUserTimezone(@PathVariable("userId") String userId);

    // ==================== BULK OPERATIONS ====================

    /**
     * Get multiple users information
     */
    @PostMapping("/batch")
    Map<String, Map<String, Object>> getUsersBatch(@RequestBody List<String> userIds);

    /**
     * Validate multiple users
     */
    @PostMapping("/validate/batch")
    Map<String, Map<String, Object>> validateUsersBatch(@RequestBody List<String> userIds);

    // ==================== CART OWNERSHIP ====================

    /**
     * Verify cart ownership
     */
    @GetMapping("/{userId}/carts/{cartId}/ownership")
    Map<String, Object> verifyCartOwnership(
            @PathVariable("userId") String userId,
            @PathVariable("cartId") Long cartId);

    /**
     * Transfer cart ownership
     */
    @PostMapping("/{userId}/carts/{cartId}/transfer")
    Map<String, Object> transferCartOwnership(
            @PathVariable("userId") String userId,
            @PathVariable("cartId") Long cartId,
            @RequestParam("newUserId") String newUserId);

    /**
     * Get user's cart history
     */
    @GetMapping("/{userId}/carts/history")
    List<Map<String, Object>> getUserCartHistory(
            @PathVariable("userId") String userId,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size);
}
