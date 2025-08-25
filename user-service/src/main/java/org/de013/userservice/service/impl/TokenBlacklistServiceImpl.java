package org.de013.userservice.service.impl;


import org.de013.userservice.service.TokenBlacklistService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public TokenBlacklistServiceImpl(StringRedisTemplate redisTemplate,
           @Value("${spring.application.name:user-service}") String appName) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = appName + ":blacklist:";
    }

    @Override
    public void blacklistToken(String token, Instant expiryTime) {
        if (token == null || expiryTime == null || Instant.now().isAfter(expiryTime)) {
            return;
        }

        String key = keyPrefix + token;
        long ttl = Duration.between(Instant.now(), expiryTime).toSeconds();

        if (ttl > 0) {
            redisTemplate.opsForValue().set(key, "blacklisted", ttl, TimeUnit.SECONDS);
        }
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        if (token == null) {
            return false;
        }
        String key = keyPrefix + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
