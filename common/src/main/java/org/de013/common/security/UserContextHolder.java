package org.de013.common.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
public class UserContextHolder {
    
    // Header names that API Gateway will set
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-User-Username";
    private static final String EMAIL_HEADER = "X-User-Email";
    private static final String FIRST_NAME_HEADER = "X-User-FirstName";
    private static final String LAST_NAME_HEADER = "X-User-LastName";
    private static final String ROLES_HEADER = "X-User-Roles";
    
    /**
     * Get current user context from request headers
     */
    public static UserContext getCurrentUser() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                log.debug("No current request found");
                return null;
            }
            
            return extractUserContextFromRequest(request);
            
        } catch (Exception e) {
            log.error("Error getting current user context", e);
            return null;
        }
    }
    
    /**
     * Get current user ID
     */
    public static Long getCurrentUserId() {
        UserContext userContext = getCurrentUser();
        return userContext != null ? userContext.getUserId() : null;
    }
    
    /**
     * Get current username
     */
    public static String getCurrentUsername() {
        UserContext userContext = getCurrentUser();
        return userContext != null ? userContext.getUsername() : null;
    }
    
    /**
     * Check if current user has specific role
     */
    public static boolean hasRole(String role) {
        UserContext userContext = getCurrentUser();
        return userContext != null && userContext.hasRole(role);
    }
    
    /**
     * Check if current user is admin
     */
    public static boolean isAdmin() {
        UserContext userContext = getCurrentUser();
        return userContext != null && userContext.isAdmin();
    }
    
    /**
     * Check if current user is customer
     */
    public static boolean isCustomer() {
        UserContext userContext = getCurrentUser();
        return userContext != null && userContext.isCustomer();
    }
    
    /**
     * Extract user context from HTTP request headers
     */
    public static UserContext extractUserContextFromRequest(HttpServletRequest request) {
        try {
            String userIdStr = request.getHeader(USER_ID_HEADER);
            String username = request.getHeader(USERNAME_HEADER);
            String email = request.getHeader(EMAIL_HEADER);
            String firstName = request.getHeader(FIRST_NAME_HEADER);
            String lastName = request.getHeader(LAST_NAME_HEADER);
            String rolesStr = request.getHeader(ROLES_HEADER);
            
            // Check if we have minimum required information
            if (!StringUtils.hasText(userIdStr) || !StringUtils.hasText(username)) {
                log.debug("Missing required user context headers");
                return null;
            }
            
            Long userId;
            try {
                userId = Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                log.warn("Invalid user ID format: {}", userIdStr);
                return null;
            }
            
            // Parse roles
            List<String> roles = Collections.emptyList();
            if (StringUtils.hasText(rolesStr)) {
                roles = Arrays.asList(rolesStr.split(","));
            }
            
            // Handle empty strings
            firstName = StringUtils.hasText(firstName) ? firstName : null;
            lastName = StringUtils.hasText(lastName) ? lastName : null;
            
            UserContext userContext = UserContext.builder()
                    .userId(userId)
                    .username(username)
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .roles(roles)
                    .build();
            
            log.debug("Extracted user context: userId={}, username={}, roles={}", 
                    userId, username, roles);
            
            return userContext;
            
        } catch (Exception e) {
            log.error("Error extracting user context from request", e);
            return null;
        }
    }
    
    /**
     * Get current HTTP request
     */
    private static HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = 
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (IllegalStateException e) {
            // No request context available (e.g., in async processing)
            log.debug("No request context available");
            return null;
        }
    }
    
    /**
     * Require authenticated user (throws exception if not authenticated)
     */
    public static UserContext requireAuthenticated() {
        UserContext userContext = getCurrentUser();
        if (userContext == null || !userContext.isValid()) {
            throw new SecurityException("Authentication required");
        }
        return userContext;
    }
    
    /**
     * Require specific role (throws exception if user doesn't have role)
     */
    public static UserContext requireRole(String role) {
        UserContext userContext = requireAuthenticated();
        if (!userContext.hasRole(role)) {
            throw new SecurityException("Role '" + role + "' required");
        }
        return userContext;
    }
    
    /**
     * Require admin role
     */
    public static UserContext requireAdmin() {
        return requireRole("ADMIN");
    }
}
