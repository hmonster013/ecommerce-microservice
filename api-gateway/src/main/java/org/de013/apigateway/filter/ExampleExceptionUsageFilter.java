package org.de013.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.de013.apigateway.exception.*;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Example filter demonstrating how to use custom Gateway exceptions
 * 
 * This filter is for demonstration purposes only and is disabled by default.
 * To enable it, remove the @Component annotation comment.
 */
@Slf4j
// @Component // Uncomment to enable this filter
public class ExampleExceptionUsageFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpHeaders headers = exchange.getRequest().getHeaders();
        
        // Example 1: Check for required headers
        if (requiresAuthentication(path)) {
            String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
            
            if (authHeader == null || authHeader.isEmpty()) {
                // Throw UnauthorizedAccessException when token is missing
                return Mono.error(UnauthorizedAccessException.tokenMissing());
            }
            
            // Validate token format
            if (!authHeader.startsWith("Bearer ")) {
                return Mono.error(InvalidRequestException.invalidHeader("Authorization"));
            }
        }
        
        // Example 2: Check for custom headers
        if (requiresUserContext(path)) {
            String userId = headers.getFirst("X-User-Id");
            
            if (userId == null || userId.isEmpty()) {
                return Mono.error(InvalidRequestException.missingHeader("X-User-Id"));
            }
        }
        
        // Example 3: Rate limiting check (simplified)
        if (isRateLimitExceeded(exchange)) {
            return Mono.error(new RateLimitExceededException(100, "minute"));
        }
        
        // Example 4: Route validation
        if (isInvalidRoute(path)) {
            return Mono.error(new RouteNotFoundException(path, exchange.getRequest().getMethod().name()));
        }
        
        // Continue with the filter chain
        return chain.filter(exchange)
                // Example 5: Handle downstream service errors
                .onErrorResume(java.net.ConnectException.class, ex -> {
                    log.error("Connection error: {}", ex.getMessage());
                    return Mono.error(new ServiceUnavailableException(extractServiceName(path), ex));
                })
                // Example 6: Handle timeout errors
                .onErrorResume(java.util.concurrent.TimeoutException.class, ex -> {
                    log.error("Timeout error: {}", ex.getMessage());
                    return Mono.error(new GatewayTimeoutException(extractServiceName(path), ex));
                });
    }

    /**
     * Check if path requires authentication
     */
    private boolean requiresAuthentication(String path) {
        // Public endpoints that don't require authentication
        return !path.startsWith("/api/v1/usersv/auth") &&
               !path.startsWith("/actuator") &&
               !path.startsWith("/swagger-ui") &&
               !path.startsWith("/v3/api-docs");
    }

    /**
     * Check if path requires user context headers
     */
    private boolean requiresUserContext(String path) {
        // Endpoints that require user context
        return path.startsWith("/api/v1/ordersv") ||
               path.startsWith("/api/v1/cartsv") ||
               path.startsWith("/api/v1/paymentsv");
    }

    /**
     * Check if rate limit is exceeded (simplified example)
     */
    private boolean isRateLimitExceeded(ServerWebExchange exchange) {
        // In real implementation, this would check Redis or similar
        // For demo purposes, always return false
        return false;
    }

    /**
     * Check if route is invalid
     */
    private boolean isInvalidRoute(String path) {
        // Example: block certain patterns
        return path.contains("../") || path.contains("..\\");
    }

    /**
     * Extract service name from path
     */
    private String extractServiceName(String path) {
        if (path.startsWith("/api/v1/")) {
            String[] parts = path.split("/");
            if (parts.length > 3) {
                return parts[3]; // e.g., "usersv", "productsv"
            }
        }
        return "unknown-service";
    }

    @Override
    public int getOrder() {
        return -50; // Run after authentication filter (-100) but before other filters
    }
}

