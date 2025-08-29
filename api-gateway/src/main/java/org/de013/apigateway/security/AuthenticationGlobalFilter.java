package org.de013.apigateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
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

    // Public endpoints that don't require authentication
    private static final List<String> GATEWAY_PUBLIC_ENDPOINTS = Arrays.asList(
            // Infrastructure endpoints
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui",
            "/v3/api-docs",
            "/api/users/v3/api-docs",
            "/api/productsv/v3/api-docs",
            "/api/cart/v3/api-docs",
            "/api/orders/v3/api-docs",
            "/api/payments/v3/api-docs",
            "/api/notifications/v3/api-docs",
            "/swagger-resources",
            "/webjars",

            // Authentication endpoints
            "/api/v1/users/auth",
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

            // HealthController - Public endpoint
            "/api/v1/productsv/health"                    // GET health check
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
                // Validate JWT token format and extract user context
                if (jwtUtil.validateToken(token)) {
                    UserContext userContext = extractUserContext(token);

                    // Add user context headers for downstream services
                    ServerHttpRequest modifiedRequest = request.mutate()
                            .header("X-User-Id", String.valueOf(userContext.getUserId()))
                            .header("X-User-Username", userContext.getUsername())
                            .header("X-User-Email", userContext.getEmail())
                            .header("X-User-FirstName", userContext.getFirstName() != null ? userContext.getFirstName() : "")
                            .header("X-User-LastName", userContext.getLastName() != null ? userContext.getLastName() : "")
                            .header("X-User-Roles", String.join(",", userContext.getRoles()))
                            .build();

                    log.debug("JWT validated, forwarding user context: {} (ID: {})", userContext.getUsername(), userContext.getUserId());

                    // Continue with user context
                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                } else {
                    log.debug("Invalid JWT token, forwarding request without user context for path: {}", path);
                }
            } catch (Exception e) {
                log.debug("JWT processing error for path {}: {}, forwarding request without user context", path, e.getMessage());
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
        return UserContext.builder()
                .userId(jwtUtil.extractUserId(token))
                .username(jwtUtil.extractUsername(token))
                .email(jwtUtil.extractEmail(token))
                .firstName(jwtUtil.extractFirstName(token))
                .lastName(jwtUtil.extractLastName(token))
                .roles(jwtUtil.extractRoles(token))
                .build();
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
