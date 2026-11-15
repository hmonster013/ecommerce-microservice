package org.de013.notificationservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Filter that reads user context from headers set by API Gateway
 * <p>
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
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String keycloakId = request.getHeader(HEADER_KEYCLOAK_ID);
            String username = request.getHeader(HEADER_USERNAME);
            String email = request.getHeader(HEADER_USER_EMAIL);
            String roles = request.getHeader(HEADER_USER_ROLES);

            log.debug("Headers received - KeycloakId: {}, Username: {}, Email: {}, Roles: {}", keycloakId, username, email, roles);

            if (StringUtils.hasText(keycloakId) && StringUtils.hasText(username)) {
                Authentication auth = createAuthenticationFromHeaders(keycloakId, username, email, roles);

                if (auth != null) {
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Set authentication for user: {}", username);
                }
            } else {
                log.debug("No user context found in headers");
            }

        } catch (Exception e) {
            log.warn("Error processing authentication headers: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/actuator/") ||
                path.contains("/swagger-ui") ||
                path.contains("/v1/api-docs");
    }

    /**
     * Create Authentication object from headers
     * Authorization already handled by API Gateway, this is just for user context
     */
    private Authentication createAuthenticationFromHeaders(String keycloakId, String username, String email, String rolesHeader) {
        if (keycloakId == null || username == null) {
            return null;
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        List<String> roleList = new ArrayList<>();
        if (StringUtils.hasText(rolesHeader)) {
            String[] roles = rolesHeader.split(",");
            for (String role : roles) {
                String cleanRole = role.trim();
                if (!cleanRole.isEmpty()) {
                    roleList.add(cleanRole);
                    if (!cleanRole.startsWith("ROLE_")) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + cleanRole));
                    } else {
                        authorities.add(new SimpleGrantedAuthority(cleanRole));
                    }
                }
            }
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username,
                null,
                authorities
        );

        auth.setDetails(new HeaderUserDetails(keycloakId, username, email, roleList));

        return auth;
    }

    /**
     * Custom UserDetails to hold user information from headers
     */
    public static class HeaderUserDetails {
        private final String keycloakId;
        private final String username;
        private final String email;
        private final List<String> roles;

        public HeaderUserDetails(String keycloakId, String username, String email) {
            this(keycloakId, username, email, Collections.emptyList());
        }

        public HeaderUserDetails(String keycloakId, String username, String email, List<String> roles) {
            this.keycloakId = keycloakId;
            this.username = username;
            this.email = email;
            this.roles = roles != null ? roles : Collections.emptyList();
        }

        public String getKeycloakId() {
            return keycloakId;
        }

        public String getUsername() {
            return username;
        }

        public String getEmail() {
            return email;
        }

        public List<String> getRoles() {
            return roles;
        }
    }
}
