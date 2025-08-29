package org.de013.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that reads user context from headers set by API Gateway
 * and creates Spring Security Authentication object for @PreAuthorize to work
 */
@Component
@Slf4j
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    // Header constants - must match API Gateway
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-User-Username";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {

        try {
            // Read user context from headers
            String userId = request.getHeader(HEADER_USER_ID);
            String username = request.getHeader(HEADER_USERNAME);
            String email = request.getHeader(HEADER_USER_EMAIL);
            String roles = request.getHeader(HEADER_USER_ROLES);

            // If user context exists, create Authentication object
            if (StringUtils.hasText(userId) && StringUtils.hasText(username)) {
                Authentication auth = HeaderAuthenticationProvider.createFromHeaders(userId, username, email, roles);
                
                if (auth != null) {
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Set authentication for user: {} with roles: {}", username, roles);
                }
            } else {
                log.debug("No user context found in headers");
            }

        } catch (Exception e) {
            log.warn("Error processing authentication headers: {}", e.getMessage());
            // Don't fail the request, just continue without authentication
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Skip filter for public endpoints
        return path.contains("/auth/") || 
               path.contains("/actuator/") || 
               path.contains("/swagger-ui") || 
               path.contains("/v3/api-docs");
    }
}
