package org.de013.common.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
public class UserContextHolder {

    // Header names that API Gateway will set
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USERNAME_HEADER = "X-User-Username";
    private static final String EMAIL_HEADER = "X-User-Email";

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
    public static String getCurrentUserId() {
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
     * Extract user context from HTTP request headers
     */
    public static UserContext extractUserContextFromRequest(HttpServletRequest request) {
        try {
            String userIdStr = request.getHeader(USER_ID_HEADER);
            String username = request.getHeader(USERNAME_HEADER);
            String email = request.getHeader(EMAIL_HEADER);

            // Check if we have minimum required information
            if (!StringUtils.hasText(userIdStr) || !StringUtils.hasText(username)) {
                log.debug("Missing required user context headers");
                return null;
            }

            String userId = userIdStr;

            UserContext userContext = UserContext.builder()
                    .userId(userId)
                    .username(username)
                    .email(email)
                    .build();

            log.debug("Extracted user context: userId={}, username={}",
                    userId, username);

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
}
