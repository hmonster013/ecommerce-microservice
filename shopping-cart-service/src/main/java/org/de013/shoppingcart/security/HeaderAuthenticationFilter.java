package org.de013.shoppingcart.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filter that reads user context from headers set by API Gateway
 * 
 * Note: X-User-Id contains Keycloak UUID (sub claim), not database user ID
 * API Gateway handles all authentication & authorization, this just reads user context
 */
@Component
@Slf4j
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    // Header constants - must match API Gateway
    private static final String HEADER_KEYCLOAK_ID = "X-User-Id";      // Keycloak UUID
    private static final String HEADER_USERNAME = "X-User-Username";
    private static final String HEADER_USER_EMAIL = "X-User-Email";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {

        try {
            String keycloakId = request.getHeader(HEADER_KEYCLOAK_ID);
            String username = request.getHeader(HEADER_USERNAME);
            String email = request.getHeader(HEADER_USER_EMAIL);

            log.debug("Headers received - KeycloakId: {}, Username: {}, Email: {}", keycloakId, username, email);

            if (StringUtils.hasText(keycloakId) && StringUtils.hasText(username)) {
                Authentication auth = createAuthenticationFromHeaders(keycloakId, username, email);
                
                if (auth != null) {
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Set authentication for user: {}", username);
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
        return path.contains("/actuator/") || 
               path.contains("/swagger-ui") || 
               path.contains("/v3/api-docs");
    }

    /**
     * Create Authentication object from headers
     * Authorization already handled by API Gateway, this is just for user context
     */
    private Authentication createAuthenticationFromHeaders(String keycloakId, String username, String email) {
        if (keycloakId == null || username == null) {
            return null;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username,
                null,
                Collections.emptyList()
        );
        
        auth.setDetails(new HeaderUserDetails(keycloakId, username, email));
        
        return auth;
    }

    /**
     * Custom UserDetails to hold user information from headers
     */
    public static class HeaderUserDetails {
        private final String keycloakId;
        private final String username;
        private final String email;

        public HeaderUserDetails(String keycloakId, String username, String email) {
            this.keycloakId = keycloakId;
            this.username = username;
            this.email = email;
        }

        public String getKeycloakId() { return keycloakId; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
    }
}
