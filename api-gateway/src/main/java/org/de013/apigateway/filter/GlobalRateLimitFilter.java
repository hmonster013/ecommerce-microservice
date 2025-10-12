package org.de013.apigateway.filter;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.extern.slf4j.Slf4j;
import org.de013.apigateway.config.RateLimitConfig;
import org.de013.apigateway.exception.dto.ErrorResponse;
import org.de013.apigateway.security.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Global Rate Limiting Filter for API Gateway
 * Provides infrastructure-level protection against abuse and DDoS attacks
 * 
 * This filter runs early in the filter chain to protect the system before
 * expensive operations like authentication and routing.
 */
@Slf4j
@Component
public class GlobalRateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimitConfig rateLimitConfig;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    public GlobalRateLimitFilter(RateLimitConfig rateLimitConfig, ObjectMapper objectMapper, JwtUtil jwtUtil) {
        this.rateLimitConfig = rateLimitConfig;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip if rate limiting is disabled
        if (!rateLimitConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip rate limiting for infrastructure endpoints
        if (isInfrastructureEndpoint(path)) {
            return chain.filter(exchange);
        }

        // Get client IP
        String clientIp = getClientIp(request);
        
        // Check IP-based rate limit first (coarse-grained protection)
        Bucket ipBucket = rateLimitConfig.getIpBucket(clientIp);
        ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);
        
        if (!ipProbe.isConsumed()) {
            log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
            return handleRateLimitExceeded(
                exchange, 
                "IP rate limit exceeded", 
                ipProbe.getNanosToWaitForRefill()
            );
        }

        // Check user-based rate limit if authenticated (fine-grained protection)
        String userId = getUserId(request);
        if (userId != null) {
            Bucket userBucket = rateLimitConfig.getUserBucket(userId);
            ConsumptionProbe userProbe = userBucket.tryConsumeAndReturnRemaining(1);
            
            if (!userProbe.isConsumed()) {
                log.warn("Rate limit exceeded for user: {} on path: {}", userId, path);
                return handleRateLimitExceeded(
                    exchange, 
                    "User rate limit exceeded", 
                    userProbe.getNanosToWaitForRefill()
                );
            }

            // Add rate limit headers for authenticated users
            addRateLimitHeaders(exchange.getResponse(), userProbe);
        } else {
            // Add rate limit headers for IP-based limiting
            addRateLimitHeaders(exchange.getResponse(), ipProbe);
        }

        return chain.filter(exchange);
    }

    /**
     * Handle rate limit exceeded scenario
     */
    private Mono<Void> handleRateLimitExceeded(
            ServerWebExchange exchange, 
            String message, 
            long nanosToWait) {
        
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        // Add Retry-After header (in seconds)
        long secondsToWait = (nanosToWait / 1_000_000_000) + 1;
        response.getHeaders().add("Retry-After", String.valueOf(secondsToWait));
        
        // Add rate limit headers
        response.getHeaders().add("X-RateLimit-Limit", "See configuration");
        response.getHeaders().add("X-RateLimit-Remaining", "0");
        response.getHeaders().add("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + secondsToWait));

        String traceId = generateTraceId();
        ServerHttpRequest request = exchange.getRequest();
        
        ErrorResponse errorResponse = ErrorResponse.of(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
            "RATE_LIMIT_EXCEEDED",
            message + ". Please try again later.",
            request.getURI().getPath(),
            request.getMethod().name(),
            traceId
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Error serializing rate limit error response: {}", e.getMessage());
            String fallbackMessage = String.format(
                "{\"success\":false,\"message\":\"%s\",\"code\":\"RATE_LIMIT_EXCEEDED\",\"traceId\":\"%s\"}",
                message, traceId
            );
            byte[] bytes = fallbackMessage.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        }
    }

    /**
     * Add rate limit headers to response
     */
    private void addRateLimitHeaders(ServerHttpResponse response, ConsumptionProbe probe) {
        response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        
        if (probe.getNanosToWaitForRefill() > 0) {
            long secondsToReset = (probe.getNanosToWaitForRefill() / 1_000_000_000) + 1;
            response.getHeaders().add("X-RateLimit-Reset", 
                String.valueOf(System.currentTimeMillis() / 1000 + secondsToReset));
        }
    }

    /**
     * Extract client IP from request
     */
    private String getClientIp(ServerHttpRequest request) {
        // Check common proxy headers
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return ip.split(",")[0].trim();
        }

        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }

        // Fallback to remote address
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * Extract user ID from JWT token in Authorization header
     */
    private String getUserId(ServerHttpRequest request) {
        try {
            // Get Authorization header
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return null;
            }

            // Extract token
            String token = authHeader.substring(7);

            // Extract userId from token
            Long userId = jwtUtil.extractUserId(token);
            return userId != null ? userId.toString() : null;
        } catch (Exception e) {
            // If token is invalid, return null (will use IP-based rate limiting)
            log.debug("Could not extract userId from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if the path is an infrastructure endpoint that should skip rate limiting
     */
    private boolean isInfrastructureEndpoint(String path) {
        return path.startsWith("/actuator") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/webjars/");
    }

    /**
     * Generate unique trace ID for error tracking
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public int getOrder() {
        // Run early in the filter chain, but after CORS (-200)
        // and before authentication (-100)
        return -150;
    }
}

