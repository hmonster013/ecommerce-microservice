package org.de013.apigateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.de013.apigateway.exception.dto.ErrorResponse;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Global exception handler for API Gateway
 * Handles all exceptions in reactive WebFlux environment
 */
@Slf4j
@Component
@Order(-2) // Higher priority than default exception handler
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        String traceId = generateTraceId();
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        log.error("Gateway error [{}] for {} {}: {}", traceId, method, path, ex.getMessage(), ex);

        ErrorResponse errorResponse = buildErrorResponse(ex, path, method, traceId);
        
        return writeErrorResponse(exchange, errorResponse);
    }

    /**
     * Build error response based on exception type
     */
    private ErrorResponse buildErrorResponse(
            Throwable ex, 
            String path, 
            String method, 
            String traceId
    ) {
        // Handle custom Gateway exceptions
        if (ex instanceof GatewayException gatewayEx) {
            return handleGatewayException(gatewayEx, path, method, traceId);
        }
        
        // Handle Spring Cloud Gateway specific exceptions
        if (ex instanceof NotFoundException) {
            return handleNotFoundException(ex, path, method, traceId);
        }
        
        if (ex instanceof TimeoutException) {
            return handleTimeoutException(ex, path, method, traceId);
        }
        
        // Handle ResponseStatusException
        if (ex instanceof ResponseStatusException responseStatusEx) {
            return handleResponseStatusException(responseStatusEx, path, method, traceId);
        }
        
        // Handle connection errors
        if (isConnectionError(ex)) {
            return handleConnectionError(ex, path, method, traceId);
        }
        
        // Handle generic exceptions
        return handleGenericException(ex, path, method, traceId);
    }

    /**
     * Handle custom GatewayException
     */
    private ErrorResponse handleGatewayException(
            GatewayException ex,
            String path,
            String method,
            String traceId
    ) {
        HttpStatus status = ex.getHttpStatus();
        
        if (status.is5xxServerError()) {
            log.error("Gateway error [{}]: {}", traceId, ex.getMessage(), ex);
        } else {
            log.warn("Gateway error [{}]: {}", traceId, ex.getMessage());
        }
        
        return ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getErrorCode(),
                ex.getMessage(),
                path,
                method,
                traceId
        );
    }

    /**
     * Handle NotFoundException (route not found)
     */
    private ErrorResponse handleNotFoundException(
            Throwable ex,
            String path,
            String method,
            String traceId
    ) {
        log.warn("Route not found [{}]: {} {}", traceId, method, path);
        
        return ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "ROUTE_NOT_FOUND",
                String.format("No route found for %s %s", method, path),
                path,
                method,
                traceId
        );
    }

    /**
     * Handle TimeoutException
     */
    private ErrorResponse handleTimeoutException(
            Throwable ex,
            String path,
            String method,
            String traceId
    ) {
        log.error("Gateway timeout [{}]: {} {}", traceId, method, path);
        
        return ErrorResponse.withDetails(
                HttpStatus.GATEWAY_TIMEOUT.value(),
                HttpStatus.GATEWAY_TIMEOUT.getReasonPhrase(),
                "GATEWAY_TIMEOUT",
                "Request to downstream service timed out",
                "The service is taking too long to respond. Please try again later",
                path,
                method,
                traceId
        );
    }

    /**
     * Handle ResponseStatusException
     */
    private ErrorResponse handleResponseStatusException(
            ResponseStatusException ex,
            String path,
            String method,
            String traceId
    ) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        
        log.warn("Response status exception [{}]: {} - {}", traceId, status, ex.getReason());
        
        return ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                "RESPONSE_STATUS_ERROR",
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(),
                path,
                method,
                traceId
        );
    }

    /**
     * Handle connection errors (service unavailable)
     */
    private ErrorResponse handleConnectionError(
            Throwable ex,
            String path,
            String method,
            String traceId
    ) {
        log.error("Connection error [{}]: {}", traceId, ex.getMessage());
        
        return ErrorResponse.withDetails(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "SERVICE_UNAVAILABLE",
                "Unable to connect to downstream service",
                "The service is temporarily unavailable. Please try again later",
                path,
                method,
                traceId
        );
    }

    /**
     * Handle generic exceptions
     */
    private ErrorResponse handleGenericException(
            Throwable ex,
            String path,
            String method,
            String traceId
    ) {
        log.error("Unexpected error [{}]: {}", traceId, ex.getMessage(), ex);
        
        return ErrorResponse.withDetails(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                "Please contact support if the problem persists",
                path,
                method,
                traceId
        );
    }

    /**
     * Check if exception is a connection error
     */
    private boolean isConnectionError(Throwable ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        
        return message.contains("Connection refused") ||
               message.contains("Connection reset") ||
               message.contains("No route to host") ||
               message.contains("Network is unreachable") ||
               ex instanceof java.net.ConnectException ||
               ex instanceof java.io.IOException;
    }

    /**
     * Write error response to client
     */
    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, ErrorResponse errorResponse) {
        ServerHttpResponse response = exchange.getResponse();
        
        // Set status code
        response.setStatusCode(HttpStatus.resolve(errorResponse.getStatus()));
        
        // Set content type
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        // Serialize error response
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error serializing error response: {}", e.getMessage());
            
            // Fallback to simple error message
            String fallbackMessage = String.format(
                    "{\"success\":false,\"message\":\"Internal server error\",\"code\":\"SERIALIZATION_ERROR\",\"traceId\":\"%s\"}",
                    errorResponse.getTraceId()
            );
            byte[] bytes = fallbackMessage.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        }
    }

    /**
     * Generate unique trace ID for error tracking
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

