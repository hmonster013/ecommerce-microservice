package org.de013.paymentservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign request interceptor to add common headers
 */
@Slf4j
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // Add common headers
        template.header("Content-Type", "application/json");
        template.header("Accept", "application/json");
        template.header("User-Agent", "payment-service/1.0");
        template.header("X-Service-Name", "payment-service");

        // Propagate security headers from current incoming request
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                String authHeader = request.getHeader("Authorization");
                if (authHeader != null) {
                    template.header("Authorization", authHeader);
                }
                String userIdHeader = request.getHeader("X-User-Id");
                if (userIdHeader != null) {
                    template.header("X-User-Id", userIdHeader);
                }
                String userNameHeader = request.getHeader("X-User-Username");
                if (userNameHeader != null) {
                    template.header("X-User-Username", userNameHeader);
                }
                String userEmailHeader = request.getHeader("X-User-Email");
                if (userEmailHeader != null) {
                    template.header("X-User-Email", userEmailHeader);
                }
            } else {
                log.warn("RequestContextHolder.getRequestAttributes() is null in FeignRequestInterceptor!");
            }
        } catch (Exception e) {
            log.warn("Failed to propagate security headers to Feign request", e);
        }

        // Add correlation ID for tracing
        String correlationId = generateCorrelationId();
        template.header("X-Correlation-ID", correlationId);

        log.debug("Added headers to Feign request: {}", template.headers());
    }

    private String generateCorrelationId() {
        return "pay-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
}
