package org.de013.apigateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.apigateway.service.TokenBlacklistService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
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
    private final TokenBlacklistService tokenBlacklistService;

    // Public endpoints that don't require authentication
    private static final List<String> GATEWAY_PUBLIC_ENDPOINTS = Arrays.asList(
            // Infrastructure endpoints
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui",
            "/v3/api-docs",
            "/api/usersv/v3/api-docs",
            "/api/productsv/v3/api-docs",
            "/api/cartsv/v3/api-docs",
            "/api/ordersv/v3/api-docs",
            "/api/paymentsv/v3/api-docs",
            "/api/notificationsv/v3/api-docs",
            "/swagger-resources",
            "/webjars",

            // Authentication endpoints
            "/api/v1/usersv/auth",
            "/api/user-service/auth",

            // Product Catalog Service - Public endpoints
            // ProductController - Public endpoints (no @PreAuthorize)
            "/api/v1/productsv",                          // GET products list
            "/api/v1/productsv/featured",                 // GET featured products
            "/api/v1/productsv/search",                   // GET/POST product search
            "/api/v1/productsv/sku",                      // GET product by SKU (with path param)

            // CategoryController - Public endpoints (no @PreAuthorize)
            "/api/v1/productsv/categories",               // GET categories list
            "/api/v1/productsv/categories/tree",          // GET category tree
            "/api/v1/productsv/categories/root",          // GET root categories
            "/api/v1/productsv/categories/search",        // GET category search
            "/api/v1/productsv/categories/slug",          // GET category by slug (with path param)

            // ReviewController - Public read endpoints (no @PreAuthorize)
            "/api/v1/productsv/reviews",                  // GET reviews (with path params)

            // InventoryController - Public read endpoints (no @PreAuthorize)
            "/api/v1/productsv/inventory",                // GET inventory (with path params)

            // SearchController - Public endpoints (no @PreAuthorize)
            "/api/v1/productsv/search/suggestions",       // GET search suggestions

            // Actuator endpoints - Public endpoints
            "/api/v1/productsv/actuator/health",          // Product service health
            "/api/v1/productsv/actuator/info",            // Product service info

            // Other services - Actuator endpoints
            "/api/v1/usersv/actuator/health",             // User service health
            "/api/v1/usersv/actuator/info",               // User service info
            "/api/v1/cartsv/actuator/health",             // Cart service health
            "/api/v1/cartsv/actuator/info",               // Cart service info
            "/api/v1/ordersv/actuator/health",            // Order service health
            "/api/v1/ordersv/actuator/info",              // Order service info
            "/api/v1/paymentsv/actuator/health",          // Payment service health
            "/api/v1/paymentsv/actuator/info",            // Payment service info
            "/api/v1/notificationsv/actuator/health",     // Notification service health
            "/api/v1/notificationsv/actuator/info"        // Notification service info
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        log.debug("Processing request: {} {}", request.getMethod(), path);

        // Skip authentication for Gateway infrastructure endpoints only
        if (isGatewayPublicEndpoint(path)) {
            log.debug("Gateway public endpoint accessed: {}", path);
            return chain.filter(exchange);
        }

        // For all business endpoints, extract JWT if present and forward user context
        // Let individual services handle their own authorization
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = jwtUtil.extractTokenFromHeader(authHeader);

        if (StringUtils.hasText(token)) {
            try {
                // Validate JWT token format and check blacklist
                if (jwtUtil.validateToken(token) && !isTokenBlacklisted(token)) {
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

        // Forward request without user context - let services handle authorization
        return chain.filter(exchange);
    }

    /**
     * Check if endpoint is public (doesn't require authentication)
     */
    private boolean isGatewayPublicEndpoint(String path) {
        return GATEWAY_PUBLIC_ENDPOINTS.stream()
                .anyMatch(publicPath -> path.startsWith(publicPath));
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

    /**
     * Check if token is blacklisted
     */
    private boolean isTokenBlacklisted(String token) {
        try {
            return tokenBlacklistService.isTokenBlacklisted(token);
        } catch (Exception e) {
            log.warn("Error checking token blacklist status: {}", e.getMessage());
            // In case of error, allow the token (fail open)
            return false;
        }
    }

    /**
     * Handle unauthorized access
     */
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");

        String body = String.format("{\"error\": \"Unauthorized\", \"message\": \"%s\"}", message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        return -100; // High priority to run before other filters
    }
}
