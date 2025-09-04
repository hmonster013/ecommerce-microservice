package org.de013.paymentservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
        
        // Add correlation ID for tracing
        String correlationId = generateCorrelationId();
        template.header("X-Correlation-ID", correlationId);
        
        log.debug("Added headers to Feign request: {}", template.headers());
    }
    
    private String generateCorrelationId() {
        return "pay-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }
}
