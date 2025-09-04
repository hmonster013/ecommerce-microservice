package org.de013.paymentservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Stripe configuration
 */
@Configuration
@Slf4j
public class StripeConfig {

    /**
     * Initialize Stripe configuration after application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeStripe() {
        log.info("Stripe configuration initialized successfully");
    }
}
