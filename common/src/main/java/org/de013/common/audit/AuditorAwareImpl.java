package org.de013.common.audit;

import lombok.extern.slf4j.Slf4j;
import org.de013.common.security.UserContext;
import org.de013.common.security.UserContextHolder;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementation of AuditorAware for JPA Auditing in Microservices Architecture
 * 
 * This implementation supports multiple authentication sources:
 * 1. User context from API Gateway headers (primary method for microservices)
 * 2. Spring Security Authentication (fallback for services with direct authentication)
 * 3. System default (for background jobs or unauthenticated operations)
 * 
 * Usage in each service:
 * - Add @EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl") to main application class or config
 * - Ensure this common library is included as a dependency
 * - Ensure HeaderAuthenticationFilter is configured to populate SecurityContext
 */
@Slf4j
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    private static final String SYSTEM_USER = "system";
    private static final String ANONYMOUS_USER = "anonymousUser";

    @Override
    public Optional<String> getCurrentAuditor() {
        try {
            // Strategy 1: Try to get user from UserContextHolder (from API Gateway headers)
            // This is the primary method for microservices architecture
            String auditor = getAuditorFromUserContext();
            if (auditor != null) {
                log.debug("Auditor from UserContext: {}", auditor);
                return Optional.of(auditor);
            }

            // Strategy 2: Try to get user from Spring Security Context
            // This is a fallback for services with direct authentication
            auditor = getAuditorFromSecurityContext();
            if (auditor != null) {
                log.debug("Auditor from SecurityContext: {}", auditor);
                return Optional.of(auditor);
            }

            // Strategy 3: Default to system user
            log.debug("No authenticated user found, using system user");
            return Optional.of(SYSTEM_USER);

        } catch (Exception e) {
            log.warn("Error getting current auditor, defaulting to system: {}", e.getMessage());
            return Optional.of(SYSTEM_USER);
        }
    }

    /**
     * Get auditor from UserContextHolder (API Gateway headers)
     * This is the primary method for microservices
     */
    private String getAuditorFromUserContext() {
        try {
            UserContext userContext = UserContextHolder.getCurrentUser();
            if (userContext != null && userContext.isValid()) {
                // Return username as the auditor
                return userContext.getUsername();
            }
        } catch (Exception e) {
            log.debug("Could not get user from UserContextHolder: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get auditor from Spring Security Context
     * This is a fallback method for services with direct authentication
     */
    private String getAuditorFromSecurityContext() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            String username = authentication.getName();
            
            // Skip anonymous users
            if (ANONYMOUS_USER.equals(username)) {
                return null;
            }

            return username;
            
        } catch (Exception e) {
            log.debug("Could not get user from SecurityContext: {}", e.getMessage());
        }
        return null;
    }
}

