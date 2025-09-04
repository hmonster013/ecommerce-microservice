package org.de013.paymentservice.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.exception.ExternalServiceException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Custom Feign error decoder for handling external service errors
 */
@Slf4j
@Component
public class FeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultErrorDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String serviceName = extractServiceName(methodKey);
        String errorMessage = extractErrorMessage(response);
        
        log.error("External service error: service={}, method={}, status={}, message={}", 
                 serviceName, methodKey, response.status(), errorMessage);

        return switch (response.status()) {
            case 400 -> new ExternalServiceException(
                String.format("Bad request to %s: %s", serviceName, errorMessage));
            case 401 -> new ExternalServiceException(
                String.format("Unauthorized access to %s: %s", serviceName, errorMessage));
            case 403 -> new ExternalServiceException(
                String.format("Forbidden access to %s: %s", serviceName, errorMessage));
            case 404 -> new ExternalServiceException(
                String.format("Resource not found in %s: %s", serviceName, errorMessage));
            case 409 -> new ExternalServiceException(
                String.format("Conflict in %s: %s", serviceName, errorMessage));
            case 422 -> new ExternalServiceException(
                String.format("Validation error in %s: %s", serviceName, errorMessage));
            case 429 -> new ExternalServiceException(
                String.format("Rate limit exceeded for %s: %s", serviceName, errorMessage));
            case 500 -> new ExternalServiceException(
                String.format("Internal server error in %s: %s", serviceName, errorMessage));
            case 502 -> new ExternalServiceException(
                String.format("Bad gateway for %s: %s", serviceName, errorMessage));
            case 503 -> new ExternalServiceException(
                String.format("Service unavailable %s: %s", serviceName, errorMessage));
            case 504 -> new ExternalServiceException(
                String.format("Gateway timeout for %s: %s", serviceName, errorMessage));
            default -> new ExternalServiceException(
                String.format("External service error %s (HTTP %d): %s", serviceName, response.status(), errorMessage));
        };
    }

    private String extractServiceName(String methodKey) {
        if (methodKey.contains("OrderServiceClient")) {
            return "Order Service";
        } else if (methodKey.contains("UserServiceClient")) {
            return "User Service";
        } else {
            return "External Service";
        }
    }

    private String extractErrorMessage(Response response) {
        try {
            if (response.body() != null) {
                byte[] bodyBytes = response.body().asInputStream().readAllBytes();
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                
                // Try to extract error message from common response formats
                if (body.contains("\"message\"")) {
                    // JSON format: {"message": "error message"}
                    int start = body.indexOf("\"message\"") + 10;
                    int end = body.indexOf("\"", start + 1);
                    if (end > start) {
                        return body.substring(start + 1, end);
                    }
                } else if (body.contains("\"error\"")) {
                    // JSON format: {"error": "error message"}
                    int start = body.indexOf("\"error\"") + 8;
                    int end = body.indexOf("\"", start + 1);
                    if (end > start) {
                        return body.substring(start + 1, end);
                    }
                }
                
                // Return first 200 characters of body if no structured message found
                return body.length() > 200 ? body.substring(0, 200) + "..." : body;
            }
        } catch (IOException e) {
            log.warn("Could not read error response body", e);
        }
        
        return response.reason() != null ? response.reason() : "Unknown error";
    }
}
