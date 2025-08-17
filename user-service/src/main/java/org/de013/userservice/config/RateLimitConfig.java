package org.de013.userservice.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitConfig {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Bean
    public Map<String, Bucket> rateLimitBuckets() {
        return buckets;
    }

    public Bucket createBucket(String key, int capacity, int refillTokens, Duration refillPeriod) {
        return buckets.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(refillTokens, refillPeriod));
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }

    // Login attempts: 5 attempts per minute
    public Bucket getLoginBucket(String clientIp) {
        return createBucket("login:" + clientIp, 5, 5, Duration.ofMinutes(1));
    }

    // Registration: 3 attempts per hour
    public Bucket getRegistrationBucket(String clientIp) {
        return createBucket("register:" + clientIp, 3, 3, Duration.ofHours(1));
    }

    // General API: 100 requests per minute
    public Bucket getGeneralBucket(String clientIp) {
        return createBucket("general:" + clientIp, 100, 100, Duration.ofMinutes(1));
    }
}
