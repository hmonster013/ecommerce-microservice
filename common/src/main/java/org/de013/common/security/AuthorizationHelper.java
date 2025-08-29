package org.de013.common.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.exception.ForbiddenException;
import org.de013.common.exception.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Helper class for authorization checks using headers set by API Gateway
 * API Gateway validates JWT and sets user context in headers:
 * - X-User-Id: User ID
 * - X-User-Username: Username
 * - X-User-Email: Email
 * - X-User-Roles: Comma-separated roles
 */
@Component
@Slf4j
public class AuthorizationHelper {

    // Header constants
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-User-Username";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_USER_FIRST_NAME = "X-User-FirstName";
    public static final String HEADER_USER_LAST_NAME = "X-User-LastName";

    // Role constants
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    /**
     * Check if user has a specific role
     */
    public boolean hasRole(HttpServletRequest request, String role) {
        String roles = request.getHeader(HEADER_USER_ROLES);
        if (!StringUtils.hasText(roles)) {
            log.debug("No roles found in request headers");
            return false;
        }
        
        List<String> roleList = Arrays.asList(roles.split(","));
        boolean hasRole = roleList.contains(role);
        log.debug("User roles: {}, checking for role: {}, result: {}", roles, role, hasRole);
        return hasRole;
    }

    /**
     * Check if user has any of the specified roles
     */
    public boolean hasAnyRole(HttpServletRequest request, String... roles) {
        for (String role : roles) {
            if (hasRole(request, role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if user is admin
     */
    public boolean isAdmin(HttpServletRequest request) {
        return hasRole(request, ROLE_ADMIN);
    }

    /**
     * Check if user is authenticated (has user ID header)
     */
    public boolean isAuthenticated(HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        return StringUtils.hasText(userId);
    }

    /**
     * Get current user ID
     */
    public Long getCurrentUserId(HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            log.warn("Invalid user ID format: {}", userId);
            return null;
        }
    }

    /**
     * Get current username
     */
    public String getCurrentUsername(HttpServletRequest request) {
        return request.getHeader(HEADER_USERNAME);
    }

    /**
     * Get current user email
     */
    public String getCurrentUserEmail(HttpServletRequest request) {
        return request.getHeader(HEADER_USER_EMAIL);
    }

    /**
     * Get current user roles as list
     */
    public List<String> getCurrentUserRoles(HttpServletRequest request) {
        String roles = request.getHeader(HEADER_USER_ROLES);
        if (!StringUtils.hasText(roles)) {
            return List.of();
        }
        return Arrays.asList(roles.split(","));
    }

    /**
     * Require authentication - throw exception if not authenticated
     */
    public void requireAuthentication(HttpServletRequest request) {
        if (!isAuthenticated(request)) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    /**
     * Require admin role - throw exception if not admin
     */
    public void requireAdmin(HttpServletRequest request) {
        requireAuthentication(request);
        if (!isAdmin(request)) {
            throw new ForbiddenException("Admin role required");
        }
    }

    /**
     * Require specific role - throw exception if user doesn't have role
     */
    public void requireRole(HttpServletRequest request, String role) {
        requireAuthentication(request);
        if (!hasRole(request, role)) {
            throw new ForbiddenException("Role '" + role + "' required");
        }
    }

    /**
     * Require any of the specified roles
     */
    public void requireAnyRole(HttpServletRequest request, String... roles) {
        requireAuthentication(request);
        if (!hasAnyRole(request, roles)) {
            throw new ForbiddenException("One of the following roles required: " + Arrays.toString(roles));
        }
    }

    /**
     * Check if current user owns the resource (by user ID)
     */
    public boolean isOwner(HttpServletRequest request, Long resourceUserId) {
        Long currentUserId = getCurrentUserId(request);
        return currentUserId != null && currentUserId.equals(resourceUserId);
    }

    /**
     * Require ownership or admin role
     */
    public void requireOwnershipOrAdmin(HttpServletRequest request, Long resourceUserId) {
        requireAuthentication(request);
        if (!isOwner(request, resourceUserId) && !isAdmin(request)) {
            throw new ForbiddenException("Access denied: must be resource owner or admin");
        }
    }

    /**
     * Log current user context for debugging
     */
    public void logUserContext(HttpServletRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("User Context - ID: {}, Username: {}, Email: {}, Roles: {}", 
                getCurrentUserId(request),
                getCurrentUsername(request),
                getCurrentUserEmail(request),
                getCurrentUserRoles(request));
        }
    }
}
