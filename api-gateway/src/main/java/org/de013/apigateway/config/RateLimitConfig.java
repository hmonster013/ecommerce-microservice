package org.de013.apigateway.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global Rate Limiting Configuration for API Gateway
 * Provides infrastructure-level protection against abuse
 */
@Slf4j
@Configuration
public class RateLimitConfig {

    private final RateLimitProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitConfig(RateLimitProperties properties) {
        this.properties = properties;
        log.info("Rate limiting enabled: {}", properties.isEnabled());
        if (properties.isEnabled()) {
            log.info("Per-IP limit: {} requests per {} minute(s)", 
                properties.getPerIp().getCapacity(), 
                properties.getPerIp().getRefillPeriodMinutes());
            log.info("Per-User limit: {} requests per {} minute(s)", 
                properties.getPerUser().getCapacity(), 
                properties.getPerUser().getRefillPeriodMinutes());
        }
    }

    @Bean
    public Map<String, Bucket> rateLimitBuckets() {
        return buckets;
    }

    /**
     * Check if rate limiting is enabled
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Create or get bucket for a specific key
     */
    private Bucket createBucket(String key, int capacity, int refillTokens, Duration refillPeriod) {
        return buckets.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(refillTokens, refillPeriod));
            Bucket bucket = Bucket.builder()
                    .addLimit(limit)
                    .build();
            log.debug("Created rate limit bucket for key: {} with capacity: {}, refill: {} per {}", 
                key, capacity, refillTokens, refillPeriod);
            return bucket;
        });
    }

    /**
     * Get bucket for IP-based rate limiting
     */
    public Bucket getIpBucket(String clientIp) {
        LimitConfig config = properties.getPerIp();
        return createBucket(
            "ip:" + clientIp, 
            config.getCapacity(), 
            config.getRefillTokens(), 
            Duration.ofMinutes(config.getRefillPeriodMinutes())
        );
    }

    /**
     * Get bucket for user-based rate limiting
     */
    public Bucket getUserBucket(String userId) {
        LimitConfig config = properties.getPerUser();
        return createBucket(
            "user:" + userId, 
            config.getCapacity(), 
            config.getRefillTokens(), 
            Duration.ofMinutes(config.getRefillPeriodMinutes())
        );
    }

    /**
     * Remove bucket for a specific key (for cleanup)
     */
    public void removeBucket(String key) {
        buckets.remove(key);
        log.debug("Removed rate limit bucket for key: {}", key);
    }

    /**
     * Get current bucket count (for monitoring)
     */
    public int getBucketCount() {
        return buckets.size();
    }

    /**
     * Clear all buckets (for testing/admin purposes)
     */
    public void clearAllBuckets() {
        int count = buckets.size();
        buckets.clear();
        log.info("Cleared {} rate limit buckets", count);
    }

    /**
     * Configuration properties for rate limiting
     */
    @Component
    @ConfigurationProperties(prefix = "rate-limit")
    @Getter
    public static class RateLimitProperties {
        private boolean enabled = true;
        private LimitConfig perIp = new LimitConfig(1000, 1000, 1);
        private LimitConfig perUser = new LimitConfig(10000, 10000, 1);

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setPerIp(LimitConfig perIp) {
            this.perIp = perIp;
        }

        public void setPerUser(LimitConfig perUser) {
            this.perUser = perUser;
        }
    }

    /**
     * Individual limit configuration
     */
    @Getter
    public static class LimitConfig {
        private int capacity;
        private int refillTokens;
        private int refillPeriodMinutes;

        public LimitConfig() {
        }

        public LimitConfig(int capacity, int refillTokens, int refillPeriodMinutes) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriodMinutes = refillPeriodMinutes;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public void setRefillTokens(int refillTokens) {
            this.refillTokens = refillTokens;
        }

        public void setRefillPeriodMinutes(int refillPeriodMinutes) {
            this.refillPeriodMinutes = refillPeriodMinutes;
        }
    }
}

