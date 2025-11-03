package org.de013.apigateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.de013.apigateway.constant.ApiPaths;
import org.de013.apigateway.exception.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.time.LocalDateTime;

@RestController
@RequestMapping(ApiPaths.FALLBACK)
@Slf4j
public class FallbackController {
    
    @GetMapping("/{serviceName}")
    public ResponseEntity<ErrorResponse> serviceFallback(
            @PathVariable String serviceName,
            ServerWebExchange exchange) {
        
        log.warn("Circuit breaker activated for service: {} - Path: {}", 
                serviceName, exchange.getRequest().getPath().value());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .success(false)
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .error(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase())
            .code("SERVICE_UNAVAILABLE")
            .message(String.format("Service '%s' is temporarily unavailable. Please try again later.", serviceName))
            .path(exchange.getRequest().getPath().value())
            .method(exchange.getRequest().getMethod().name())
            .timestamp(LocalDateTime.now())
            .traceId(exchange.getRequest().getId())
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(errorResponse);
    }
}
