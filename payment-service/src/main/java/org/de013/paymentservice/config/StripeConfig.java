package org.de013.paymentservice.config;

import com.stripe.Stripe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Stripe configuration
 * Centralizes Stripe SDK initialization and configuration
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StripeConfig {

    private final PaymentGatewayConfig paymentGatewayConfig;

    /**
     * Initialize Stripe configuration after application startup
     * This ensures all beans are loaded before Stripe initialization
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeStripe() {
        PaymentGatewayConfig.Stripe stripeConfig = paymentGatewayConfig.getGateways().getStripe();

        if (stripeConfig.isEnabled()) {
            try {
                // Set Stripe API key globally
                Stripe.apiKey = stripeConfig.getApiKey();

                // Note: API version is set per request in Stripe SDK, not globally
                // It will be used in individual API calls when needed

                // Validate configuration
                validateStripeConfiguration(stripeConfig);

                log.info("Stripe configuration initialized successfully");
                log.info("Stripe API Version: {}", stripeConfig.getApiVersion());
                log.info("Stripe Webhook Secret configured: {}",
                        stripeConfig.getWebhookSecret() != null && !stripeConfig.getWebhookSecret().isEmpty());

            } catch (Exception e) {
                log.error("Failed to initialize Stripe configuration", e);
                throw new IllegalStateException("Stripe configuration failed", e);
            }
        } else {
            log.info("Stripe is disabled in configuration");
        }
    }

    /**
     * Validate Stripe configuration
     */
    private void validateStripeConfiguration(PaymentGatewayConfig.Stripe stripeConfig) {
        if (stripeConfig.getApiKey() == null || stripeConfig.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Stripe API key is required when Stripe is enabled");
        }

        if (!stripeConfig.getApiKey().startsWith("sk_")) {
            throw new IllegalArgumentException("Invalid Stripe API key format. Must start with 'sk_'");
        }

        if (stripeConfig.getApiKey().contains("your_stripe_secret_key")) {
            log.warn("Using default/placeholder Stripe API key. Please configure a real API key for production!");
        }

        if (stripeConfig.getWebhookSecret() != null &&
            !stripeConfig.getWebhookSecret().isEmpty() &&
            !stripeConfig.getWebhookSecret().startsWith("whsec_")) {
            throw new IllegalArgumentException("Invalid Stripe webhook secret format. Must start with 'whsec_'");
        }
    }
}
