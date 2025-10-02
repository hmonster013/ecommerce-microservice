package org.de013.apigateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    // Infrastructure endpoints that don't require authentication
    // All business endpoints will go through JWT validation
    // Individual services decide public/protected via @PreAuthorize
    private static final List<String> GATEWAY_INFRASTRUCTURE_ENDPOINTS = Arrays.asList(
            // Core infrastructure endpoints
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui",
            "/v3/api-docs",
            "/swagger-resources",
            "/webjars",

            // Service-specific API docs
            "/api/usersv/v3/api-docs",
            "/api/productsv/v3/api-docs",
            "/api/cartsv/v3/api-docs",
            "/api/ordersv/v3/api-docs",
            "/api/paymentsv/v3/api-docs",
            "/api/notificationsv/v3/api-docs",

            // Authentication endpoints - must be public for login
            "/api/v1/usersv/auth",
            "/api/user-service/auth",

            // Service actuator endpoints
            "/api/v1/usersv/actuator/health",
            "/api/v1/usersv/actuator/info",
            "/api/v1/productsv/actuator/health",
            "/api/v1/productsv/actuator/info",
            "/api/v1/cartsv/actuator/health",
            "/api/v1/cartsv/actuator/info",
            "/api/v1/ordersv/actuator/health",
            "/api/v1/ordersv/actuator/info",
            "/api/v1/paymentsv/actuator/health",
            "/api/v1/paymentsv/actuator/info",
            "/api/v1/notificationsv/actuator/health",
            "/api/v1/notificationsv/actuator/info"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        log.debug("Processing request: {} {}", request.getMethod(), path);

        // Skip authentication for infrastructure endpoints only
        // All business endpoints will go through JWT validation
        if (isInfrastructureEndpoint(path)) {
            log.debug("Infrastructure endpoint accessed: {}", path);
            return chain.filter(exchange);
        }

        // For all business endpoints, validate JWT and forward user context
        // Individual services decide public/protected via @PreAuthorize
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = jwtUtil.extractTokenFromHeader(authHeader);

        if (StringUtils.hasText(token)) {
            try {
                // Validate JWT token (includes blacklist check, format, expiration)
                if (jwtUtil.validateToken(token)) {
                    UserContext userContext = extractUserContext(token);

                    log.debug("JWT validated, forwarding user context: {} (ID: {})", userContext.getUsername(), userContext.getUserId());

                    // Create new headers map with existing headers plus user context
                    HttpHeaders newHeaders = new HttpHeaders();
                    newHeaders.addAll(request.getHeaders());
                    newHeaders.set("X-User-Id", String.valueOf(userContext.getUserId()));
                    newHeaders.set("X-User-Username", userContext.getUsername());
                    newHeaders.set("X-User-Email", userContext.getEmail());
                    newHeaders.set("X-User-FirstName", userContext.getFirstName() != null ? userContext.getFirstName() : "");
                    newHeaders.set("X-User-LastName", userContext.getLastName() != null ? userContext.getLastName() : "");
                    newHeaders.set("X-User-Roles", String.join(",", userContext.getRoles()));

                    // Create new request with modified headers
                    ServerHttpRequest modifiedRequest = new ServerHttpRequestDecorator(request) {
                        @Override
                        public HttpHeaders getHeaders() {
                            return newHeaders;
                        }
                    };

                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                } else {
                    log.debug("Invalid JWT token, forwarding request without user context for path: {}", path);
                }
            } catch (Exception e) {
                log.debug("JWT processing error for path {}: {}, forwarding request without user context", path, e.getMessage(), e);
            }
        } else {
            log.debug("No Authorization header, forwarding request without user context for path: {}", path);
        }

        // Forward request without user context
        // Services with @PreAuthorize will reject, public endpoints will allow
        return chain.filter(exchange);
    }

    /**
     * Check if endpoint is infrastructure endpoint (doesn't require authentication)
     *
     * FAIL SECURE APPROACH:
     * - Only infrastructure endpoints are public
     * - All business endpoints go through JWT validation
     * - Individual services decide public/protected via @PreAuthorize
     */
    private boolean isInfrastructureEndpoint(String path) {
        return GATEWAY_INFRASTRUCTURE_ENDPOINTS.stream()
                .anyMatch(infraPath -> path.startsWith(infraPath));
    }

    /**
     * Extract user context from JWT token
     */
    private UserContext extractUserContext(String token) {
        try {
            Long userId = jwtUtil.extractUserId(token);
            String username = jwtUtil.extractUsername(token);
            String email = jwtUtil.extractEmail(token);
            String firstName = jwtUtil.extractFirstName(token);
            String lastName = jwtUtil.extractLastName(token);
            List<String> roles = jwtUtil.extractRoles(token);

            log.debug("Extracted user context - UserId: {}, Username: {}, Email: {}, FirstName: {}, LastName: {}, Roles: {}",
                    userId, username, email, firstName, lastName, roles);

            return UserContext.builder()
                    .userId(userId)
                    .username(username)
                    .email(email)
                    .firstName(firstName)
                    .lastName(lastName)
                    .roles(roles)
                    .build();
        } catch (Exception e) {
            log.error("Error extracting user context from token: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public int getOrder() {
        return -100; // High priority to run before other filters
    }
}
