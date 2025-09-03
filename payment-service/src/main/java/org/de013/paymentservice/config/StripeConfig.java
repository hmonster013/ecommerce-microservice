package org.de013.paymentservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.gateway.PaymentGatewayFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Stripe configuration
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StripeConfig {

    private final PaymentGatewayFactory paymentGatewayFactory;

    /**
     * ObjectMapper bean for JSON processing
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Initialize payment gateways after application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializePaymentGateways() {
        log.info("Initializing payment gateways after application startup...");
        try {
            paymentGatewayFactory.initializeGateways();
            log.info("Payment gateways initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize payment gateways", e);
        }
    }
}
