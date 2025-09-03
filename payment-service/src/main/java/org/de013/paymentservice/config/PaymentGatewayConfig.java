package org.de013.paymentservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for payment gateways
 */
@Configuration
@ConfigurationProperties(prefix = "payment")
@Data
public class PaymentGatewayConfig {

    private Gateways gateways = new Gateways();
    private Processing processing = new Processing();
    private Security security = new Security();

    @Data
    public static class Gateways {
        private Stripe stripe = new Stripe();
    }

    @Data
    public static class Stripe {
        private boolean enabled = true;
        private String apiKey;
        private String webhookSecret;
        private String apiVersion = "2023-10-16";
        private String successUrl;
        private String cancelUrl;
    }

    @Data
    public static class Processing {
        private long timeout = 30000; // 30 seconds
        private int retryAttempts = 3;
        private long retryDelay = 1000; // 1 second
    }

    @Data
    public static class Security {
        private String encryptionKey;
        private long webhookTimeout = 10000; // 10 seconds
    }
}
