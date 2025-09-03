package org.de013.paymentservice.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.exception.PaymentGatewayException;
import org.de013.paymentservice.gateway.stripe.StripePaymentGateway;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating and managing payment gateways
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayFactory {

    private final PaymentGatewayConfig config;
    private final StripePaymentGateway stripePaymentGateway;
    
    private final Map<String, PaymentGateway> gateways = new HashMap<>();

    /**
     * Initialize all available gateways
     */
    public void initializeGateways() {
        log.info("Initializing payment gateways...");
        
        // Initialize Stripe gateway
        if (config.getGateways().getStripe().isEnabled()) {
            try {
                if (stripePaymentGateway.isHealthy()) {
                    gateways.put("STRIPE", stripePaymentGateway);
                    log.info("Stripe payment gateway initialized successfully");
                } else {
                    log.warn("Stripe payment gateway is not healthy, skipping initialization");
                }
            } catch (Exception e) {
                log.error("Failed to initialize Stripe payment gateway", e);
            }
        } else {
            log.info("Stripe payment gateway is disabled");
        }

        log.info("Payment gateway initialization completed. Available gateways: {}", gateways.keySet());
    }

    /**
     * Get payment gateway by provider name
     */
    public PaymentGateway getGateway(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            throw new PaymentGatewayException("Provider name cannot be null or empty");
        }

        String upperProvider = provider.toUpperCase();
        PaymentGateway gateway = gateways.get(upperProvider);
        
        if (gateway == null) {
            throw new PaymentGatewayException("Payment gateway not found or not enabled: " + provider);
        }

        if (!gateway.isEnabled()) {
            throw new PaymentGatewayException("Payment gateway is disabled: " + provider);
        }

        return gateway;
    }

    /**
     * Get default payment gateway (Stripe)
     */
    public PaymentGateway getDefaultGateway() {
        return getGateway("STRIPE");
    }

    /**
     * Get Stripe payment gateway specifically
     */
    public StripePaymentGateway getStripeGateway() {
        PaymentGateway gateway = getGateway("STRIPE");
        if (!(gateway instanceof StripePaymentGateway)) {
            throw new PaymentGatewayException("Stripe gateway is not available");
        }
        return (StripePaymentGateway) gateway;
    }

    /**
     * Check if a gateway is available
     */
    public boolean isGatewayAvailable(String provider) {
        try {
            PaymentGateway gateway = getGateway(provider);
            return gateway.isEnabled() && gateway.isHealthy();
        } catch (Exception e) {
            log.debug("Gateway {} is not available: {}", provider, e.getMessage());
            return false;
        }
    }

    /**
     * Get all available gateway names
     */
    public List<String> getAvailableGateways() {
        return gateways.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Get all healthy gateways
     */
    public List<String> getHealthyGateways() {
        return gateways.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled() && entry.getValue().isHealthy())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Perform health check on all gateways
     */
    public Map<String, Boolean> performHealthCheck() {
        Map<String, Boolean> healthStatus = new HashMap<>();
        
        for (Map.Entry<String, PaymentGateway> entry : gateways.entrySet()) {
            String provider = entry.getKey();
            PaymentGateway gateway = entry.getValue();
            
            try {
                boolean isHealthy = gateway.isHealthy();
                healthStatus.put(provider, isHealthy);
                log.debug("Gateway {} health check: {}", provider, isHealthy ? "HEALTHY" : "UNHEALTHY");
            } catch (Exception e) {
                healthStatus.put(provider, false);
                log.warn("Gateway {} health check failed", provider, e);
            }
        }
        
        return healthStatus;
    }

    /**
     * Get gateway statistics
     */
    public Map<String, Object> getGatewayStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalGateways", gateways.size());
        stats.put("enabledGateways", getAvailableGateways().size());
        stats.put("healthyGateways", getHealthyGateways().size());
        stats.put("availableGateways", getAvailableGateways());
        stats.put("healthyGatewaysList", getHealthyGateways());
        stats.put("healthStatus", performHealthCheck());
        
        return stats;
    }

    /**
     * Refresh gateway configurations
     */
    public void refreshGateways() {
        log.info("Refreshing payment gateways...");
        gateways.clear();
        initializeGateways();
    }

    /**
     * Validate gateway configuration
     */
    public void validateGatewayConfiguration(String provider) {
        PaymentGateway gateway = getGateway(provider);
        
        if (!gateway.isEnabled()) {
            throw new PaymentGatewayException("Gateway is not enabled: " + provider);
        }
        
        if (!gateway.isHealthy()) {
            throw new PaymentGatewayException("Gateway is not healthy: " + provider);
        }
        
        log.info("Gateway {} configuration is valid", provider);
    }
}
