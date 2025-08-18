package org.de013.shoppingcart.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.shoppingcart.client.UserServiceFeignClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * User Service Client
 * Integrates with User Service for user validation, preferences, and profile information
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final UserServiceFeignClient userServiceFeignClient;
    private final RestTemplate restTemplate;

    @Value("${app.services.user-service.url:http://localhost:8080}")
    private String userServiceUrl;

    @Value("${app.services.user-service.timeout:5000}")
    private int timeoutMs;

    // ==================== USER VALIDATION ====================

    /**
     * Validate user exists and is active
     */
    @Cacheable(value = "userValidation", key = "#userId", unless = "#result == false")
    public boolean validateUser(String userId) {
        try {
            log.debug("Validating user: {}", userId);
            
            String url = userServiceUrl + "/api/users/" + userId + "/validate";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                return Boolean.TRUE.equals(responseBody.get("isValid"));
            }
            
            return false;
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("User not found: {}", userId);
                return false;
            }
            log.error("Error validating user {}: {}", userId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error validating user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get user information
     */
    @Cacheable(value = "userInfo", key = "#userId", unless = "#result == null")
    public UserInfo getUserInfo(String userId) {
        try {
            log.debug("Fetching user info for: {}", userId);
            
            String url = userServiceUrl + "/api/users/" + userId;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return mapToUserInfo(response.getBody());
            }
            
            return null;
            
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("User not found: {}", userId);
                return null;
            }
            log.error("Error fetching user info for {}: {}", userId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error fetching user info for {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    // ==================== USER PREFERENCES ====================

    /**
     * Get user shopping preferences
     */
    @Cacheable(value = "userPreferences", key = "#userId", unless = "#result == null")
    public UserPreferences getUserPreferences(String userId) {
        try {
            log.debug("Fetching user preferences for: {}", userId);
            
            String url = userServiceUrl + "/api/users/" + userId + "/preferences";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return mapToUserPreferences(response.getBody());
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error fetching user preferences for {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get user's default shipping address
     */
    public ShippingAddress getDefaultShippingAddress(String userId) {
        try {
            log.debug("Fetching default shipping address for user: {}", userId);
            
            String url = userServiceUrl + "/api/users/" + userId + "/addresses/default";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return mapToShippingAddress(response.getBody());
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error fetching default shipping address for {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    // ==================== USER LOYALTY ====================

    /**
     * Get user loyalty information
     */
    public LoyaltyInfo getUserLoyaltyInfo(String userId) {
        try {
            log.debug("Fetching loyalty info for user: {}", userId);
            
            String url = userServiceUrl + "/api/users/" + userId + "/loyalty";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return mapToLoyaltyInfo(response.getBody());
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Error fetching loyalty info for {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Update user loyalty points
     */
    public boolean updateLoyaltyPoints(String userId, int pointsToAdd, String reason) {
        try {
            log.debug("Updating loyalty points for user {}: {} points, reason: {}", userId, pointsToAdd, reason);
            
            String url = userServiceUrl + "/api/users/" + userId + "/loyalty/points";
            Map<String, Object> request = Map.of(
                "pointsToAdd", pointsToAdd,
                "reason", reason
            );
            
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                return Boolean.TRUE.equals(responseBody.get("success"));
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error updating loyalty points for {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    // ==================== USER PERMISSIONS ====================

    /**
     * Check if user has permission for specific action
     */
    public boolean hasPermission(String userId, String permission) {
        try {
            log.debug("Checking permission {} for user: {}", permission, userId);
            
            String url = userServiceUrl + "/api/users/" + userId + "/permissions/" + permission;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                return Boolean.TRUE.equals(responseBody.get("hasPermission"));
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking permission for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if user can make purchases
     */
    public boolean canMakePurchases(String userId) {
        try {
            UserInfo userInfo = getUserInfo(userId);
            if (userInfo == null) {
                return false;
            }
            
            return "ACTIVE".equals(userInfo.getStatus()) && 
                   !Boolean.TRUE.equals(userInfo.getIsSuspended()) &&
                   hasPermission(userId, "MAKE_PURCHASES");
            
        } catch (Exception e) {
            log.error("Error checking purchase permission for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    // ==================== USER ACTIVITY ====================

    /**
     * Record user cart activity
     */
    public void recordCartActivity(String userId, String activityType, Map<String, Object> activityData) {
        try {
            log.debug("Recording cart activity for user {}: {}", userId, activityType);
            
            String url = userServiceUrl + "/api/users/" + userId + "/activity";
            Map<String, Object> request = Map.of(
                "activityType", activityType,
                "activityData", activityData,
                "timestamp", System.currentTimeMillis()
            );
            
            restTemplate.postForEntity(url, request, Map.class);
            
        } catch (Exception e) {
            log.error("Error recording cart activity for user {}: {}", userId, e.getMessage(), e);
            // Don't throw exception as this is not critical
        }
    }

    // ==================== HELPER METHODS ====================

    private UserInfo mapToUserInfo(Map<String, Object> userData) {
        return UserInfo.builder()
                .userId(getStringValue(userData, "userId"))
                .email(getStringValue(userData, "email"))
                .firstName(getStringValue(userData, "firstName"))
                .lastName(getStringValue(userData, "lastName"))
                .status(getStringValue(userData, "status"))
                .membershipLevel(getStringValue(userData, "membershipLevel"))
                .isSuspended(getBooleanValue(userData, "isSuspended"))
                .isEmailVerified(getBooleanValue(userData, "isEmailVerified"))
                .preferredCurrency(getStringValue(userData, "preferredCurrency"))
                .build();
    }

    private UserPreferences mapToUserPreferences(Map<String, Object> preferencesData) {
        return UserPreferences.builder()
                .preferredCurrency(getStringValue(preferencesData, "preferredCurrency"))
                .preferredLanguage(getStringValue(preferencesData, "preferredLanguage"))
                .emailNotifications(getBooleanValue(preferencesData, "emailNotifications"))
                .smsNotifications(getBooleanValue(preferencesData, "smsNotifications"))
                .marketingEmails(getBooleanValue(preferencesData, "marketingEmails"))
                .autoSaveCart(getBooleanValue(preferencesData, "autoSaveCart"))
                .cartExpirationDays(getIntegerValue(preferencesData, "cartExpirationDays"))
                .build();
    }

    private ShippingAddress mapToShippingAddress(Map<String, Object> addressData) {
        return ShippingAddress.builder()
                .addressId(getStringValue(addressData, "addressId"))
                .firstName(getStringValue(addressData, "firstName"))
                .lastName(getStringValue(addressData, "lastName"))
                .streetAddress(getStringValue(addressData, "streetAddress"))
                .city(getStringValue(addressData, "city"))
                .state(getStringValue(addressData, "state"))
                .postalCode(getStringValue(addressData, "postalCode"))
                .country(getStringValue(addressData, "country"))
                .phone(getStringValue(addressData, "phone"))
                .isDefault(getBooleanValue(addressData, "isDefault"))
                .build();
    }

    private LoyaltyInfo mapToLoyaltyInfo(Map<String, Object> loyaltyData) {
        return LoyaltyInfo.builder()
                .currentPoints(getIntegerValue(loyaltyData, "currentPoints"))
                .totalEarnedPoints(getIntegerValue(loyaltyData, "totalEarnedPoints"))
                .membershipLevel(getStringValue(loyaltyData, "membershipLevel"))
                .nextLevelPoints(getIntegerValue(loyaltyData, "nextLevelPoints"))
                .discountPercentage(getDoubleValue(loyaltyData, "discountPercentage"))
                .canUsePoints(getBooleanValue(loyaltyData, "canUsePoints"))
                .build();
    }

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    private Boolean getBooleanValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    private Integer getIntegerValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private Double getDoubleValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    // ==================== INNER CLASSES ====================

    @lombok.Builder
    @lombok.Data
    public static class UserInfo {
        private String userId;
        private String email;
        private String firstName;
        private String lastName;
        private String status;
        private String membershipLevel;
        private Boolean isSuspended;
        private Boolean isEmailVerified;
        private String preferredCurrency;
    }

    @lombok.Builder
    @lombok.Data
    public static class UserPreferences {
        private String preferredCurrency;
        private String preferredLanguage;
        private Boolean emailNotifications;
        private Boolean smsNotifications;
        private Boolean marketingEmails;
        private Boolean autoSaveCart;
        private Integer cartExpirationDays;
    }

    @lombok.Builder
    @lombok.Data
    public static class ShippingAddress {
        private String addressId;
        private String firstName;
        private String lastName;
        private String streetAddress;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private String phone;
        private Boolean isDefault;
    }

    @lombok.Builder
    @lombok.Data
    public static class LoyaltyInfo {
        private Integer currentPoints;
        private Integer totalEarnedPoints;
        private String membershipLevel;
        private Integer nextLevelPoints;
        private Double discountPercentage;
        private Boolean canUsePoints;
    }
}
