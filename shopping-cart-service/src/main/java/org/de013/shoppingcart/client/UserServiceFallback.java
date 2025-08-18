package org.de013.shoppingcart.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Fallback implementation for User Service
 * Provides default responses when the service is unavailable
 */
@Component
@Slf4j
public class UserServiceFallback implements UserServiceFeignClient {

    // ==================== USER AUTHENTICATION ====================

    @Override
    public Map<String, Object> validateAuthToken(String authToken) {
        log.warn("User Service unavailable, using fallback for auth token validation");
        return Map.of(
            "valid", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getUserById(String userId) {
        log.warn("User Service unavailable, using fallback for user: {}", userId);
        return Map.of(
            "userId", userId,
            "exists", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getUserByEmail(String email) {
        log.warn("User Service unavailable, using fallback for email: {}", email);
        return Map.of(
            "email", email,
            "exists", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> checkUserExists(String userId) {
        log.warn("User Service unavailable, using fallback for user existence check: {}", userId);
        return Map.of(
            "userId", userId,
            "exists", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    // ==================== SESSION MANAGEMENT ====================

    @Override
    public Map<String, Object> createGuestSession(Map<String, Object> sessionData) {
        log.warn("User Service unavailable, cannot create guest session");
        return Map.of(
            "success", false,
            "sessionId", null,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> validateSession(String sessionId) {
        log.warn("User Service unavailable, using fallback for session validation: {}", sessionId);
        return Map.of(
            "sessionId", sessionId,
            "valid", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> updateSessionActivity(String sessionId) {
        log.warn("User Service unavailable, cannot update session activity: {}", sessionId);
        return Map.of(
            "success", false,
            "sessionId", sessionId,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> convertGuestSession(String sessionId, String userId) {
        log.warn("User Service unavailable, cannot convert guest session: {}", sessionId);
        return Map.of(
            "success", false,
            "sessionId", sessionId,
            "userId", userId,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> endSession(String sessionId) {
        log.warn("User Service unavailable, cannot end session: {}", sessionId);
        return Map.of(
            "success", false,
            "sessionId", sessionId,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    // ==================== USER PREFERENCES ====================

    @Override
    public Map<String, Object> getUserPreferences(String userId) {
        log.warn("User Service unavailable, using default preferences for user: {}", userId);
        return Map.of(
            "userId", userId,
            "currency", "USD",
            "language", "en",
            "timezone", "UTC",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> updateUserPreferences(String userId, Map<String, Object> preferences) {
        log.warn("User Service unavailable, cannot update preferences for user: {}", userId);
        return Map.of(
            "success", false,
            "userId", userId,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getShoppingPreferences(String userId) {
        log.warn("User Service unavailable, using default shopping preferences for user: {}", userId);
        return Map.of(
            "userId", userId,
            "autoSave", true,
            "notifications", false,
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getCurrencyPreference(String userId) {
        log.warn("User Service unavailable, using default currency for user: {}", userId);
        return Map.of(
            "userId", userId,
            "currency", "USD",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getLanguagePreference(String userId) {
        log.warn("User Service unavailable, using default language for user: {}", userId);
        return Map.of(
            "userId", userId,
            "language", "en",
            "fallback", true
        );
    }

    // ==================== USER ADDRESSES ====================

    @Override
    public List<Map<String, Object>> getUserAddresses(String userId) {
        log.warn("User Service unavailable, returning empty addresses for user: {}", userId);
        return List.of();
    }

    @Override
    public Map<String, Object> getDefaultShippingAddress(String userId) {
        log.warn("User Service unavailable, no default shipping address for user: {}", userId);
        return Map.of(
            "userId", userId,
            "hasAddress", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getDefaultBillingAddress(String userId) {
        log.warn("User Service unavailable, no default billing address for user: {}", userId);
        return Map.of(
            "userId", userId,
            "hasAddress", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> validateAddress(String userId, Map<String, Object> address) {
        log.warn("User Service unavailable, cannot validate address for user: {}", userId);
        return Map.of(
            "valid", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    // ==================== USER PERMISSIONS ====================

    @Override
    public Map<String, Object> getUserPermissions(String userId) {
        log.warn("User Service unavailable, using default permissions for user: {}", userId);
        return Map.of(
            "userId", userId,
            "canPurchase", false,
            "canModifyCart", true,
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> canUserPurchase(String userId) {
        log.warn("User Service unavailable, denying purchase permission for user: {}", userId);
        return Map.of(
            "userId", userId,
            "canPurchase", false,
            "reason", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getPurchaseLimits(String userId) {
        log.warn("User Service unavailable, using default purchase limits for user: {}", userId);
        return Map.of(
            "userId", userId,
            "dailyLimit", 0,
            "monthlyLimit", 0,
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> validatePurchaseAmount(String userId, String amount) {
        log.warn("User Service unavailable, denying purchase amount validation for user: {}", userId);
        return Map.of(
            "valid", false,
            "amount", amount,
            "reason", "Service unavailable",
            "fallback", true
        );
    }

    // ==================== USER LOYALTY ====================

    @Override
    public Map<String, Object> getUserLoyalty(String userId) {
        log.warn("User Service unavailable, using default loyalty info for user: {}", userId);
        return Map.of(
            "userId", userId,
            "points", 0,
            "level", "BASIC",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getLoyaltyPoints(String userId) {
        log.warn("User Service unavailable, using default loyalty points for user: {}", userId);
        return Map.of(
            "userId", userId,
            "points", 0,
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> calculateLoyaltyPoints(String userId, String amount) {
        log.warn("User Service unavailable, cannot calculate loyalty points for user: {}", userId);
        return Map.of(
            "userId", userId,
            "amount", amount,
            "points", 0,
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getMembershipLevel(String userId) {
        log.warn("User Service unavailable, using default membership level for user: {}", userId);
        return Map.of(
            "userId", userId,
            "level", "BASIC",
            "fallback", true
        );
    }

    // ==================== USER ACTIVITY ====================

    @Override
    public void trackUserActivity(String userId, Map<String, Object> activityData) {
        log.warn("User Service unavailable, cannot track activity for user: {}", userId);
    }

    @Override
    public Map<String, Object> getUserActivitySummary(String userId) {
        log.warn("User Service unavailable, returning empty activity summary for user: {}", userId);
        return Map.of(
            "userId", userId,
            "totalActivities", 0,
            "fallback", true
        );
    }

    @Override
    public void trackCartActivity(String userId, Long cartId, String action) {
        log.warn("User Service unavailable, cannot track cart activity for user: {}", userId);
    }

    // ==================== USER NOTIFICATIONS ====================

    @Override
    public Map<String, Object> getNotificationPreferences(String userId) {
        log.warn("User Service unavailable, using default notification preferences for user: {}", userId);
        return Map.of(
            "userId", userId,
            "email", false,
            "sms", false,
            "push", false,
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> sendCartNotification(String userId, Map<String, Object> notificationData) {
        log.warn("User Service unavailable, cannot send cart notification to user: {}", userId);
        return Map.of(
            "success", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> sendAbandonmentNotification(String userId, Long cartId) {
        log.warn("User Service unavailable, cannot send abandonment notification to user: {}", userId);
        return Map.of(
            "success", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    // ==================== USER PROFILE ====================

    @Override
    public Map<String, Object> getUserProfile(String userId) {
        log.warn("User Service unavailable, using minimal profile for user: {}", userId);
        return Map.of(
            "userId", userId,
            "name", "Unknown User",
            "email", "unknown@example.com",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getUserContact(String userId) {
        log.warn("User Service unavailable, no contact info for user: {}", userId);
        return Map.of(
            "userId", userId,
            "hasContact", false,
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getUserStatus(String userId) {
        log.warn("User Service unavailable, using default status for user: {}", userId);
        return Map.of(
            "userId", userId,
            "status", "UNKNOWN",
            "active", false,
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> getUserTimezone(String userId) {
        log.warn("User Service unavailable, using default timezone for user: {}", userId);
        return Map.of(
            "userId", userId,
            "timezone", "UTC",
            "fallback", true
        );
    }

    // ==================== BULK OPERATIONS ====================

    @Override
    public Map<String, Map<String, Object>> getUsersBatch(List<String> userIds) {
        log.warn("User Service unavailable, using fallback for {} users", userIds.size());
        Map<String, Map<String, Object>> result = new java.util.HashMap<>();
        for (String userId : userIds) {
            result.put(userId, getUserById(userId));
        }
        return result;
    }

    @Override
    public Map<String, Map<String, Object>> validateUsersBatch(List<String> userIds) {
        log.warn("User Service unavailable, using fallback validation for {} users", userIds.size());
        Map<String, Map<String, Object>> result = new java.util.HashMap<>();
        for (String userId : userIds) {
            result.put(userId, checkUserExists(userId));
        }
        return result;
    }

    // ==================== CART OWNERSHIP ====================

    @Override
    public Map<String, Object> verifyCartOwnership(String userId, Long cartId) {
        log.warn("User Service unavailable, cannot verify cart ownership for user: {}", userId);
        return Map.of(
            "userId", userId,
            "cartId", cartId,
            "isOwner", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public Map<String, Object> transferCartOwnership(String userId, Long cartId, String newUserId) {
        log.warn("User Service unavailable, cannot transfer cart ownership for user: {}", userId);
        return Map.of(
            "success", false,
            "error", "Service unavailable",
            "fallback", true
        );
    }

    @Override
    public List<Map<String, Object>> getUserCartHistory(String userId, Integer page, Integer size) {
        log.warn("User Service unavailable, returning empty cart history for user: {}", userId);
        return List.of();
    }
}
