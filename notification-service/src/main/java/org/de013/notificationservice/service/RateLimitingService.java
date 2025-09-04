package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Service for rate limiting notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitingService {

    private final RedisTemplate<String, String> redisTemplate;

    // Rate limit configurations
    private static final int DEFAULT_USER_RATE_LIMIT_PER_MINUTE = 10;
    private static final int DEFAULT_USER_RATE_LIMIT_PER_HOUR = 100;
    private static final int DEFAULT_USER_RATE_LIMIT_PER_DAY = 500;
    
    private static final int EMAIL_PROVIDER_RATE_LIMIT_PER_MINUTE = 100;
    private static final int SMS_PROVIDER_RATE_LIMIT_PER_MINUTE = 50;
    private static final int PUSH_PROVIDER_RATE_LIMIT_PER_MINUTE = 200;
    private static final int INAPP_PROVIDER_RATE_LIMIT_PER_MINUTE = 500;

    /**
     * Check if user is within rate limits
     */
    public boolean isUserWithinRateLimit(Long userId, NotificationChannel channel, NotificationType type) {
        try {
            // Check per-minute limit
            if (!checkUserRateLimit(userId, channel, type, "minute", DEFAULT_USER_RATE_LIMIT_PER_MINUTE, 60)) {
                log.warn("User rate limit exceeded (per minute): userId={}, channel={}, type={}", userId, channel, type);
                return false;
            }

            // Check per-hour limit
            if (!checkUserRateLimit(userId, channel, type, "hour", DEFAULT_USER_RATE_LIMIT_PER_HOUR, 3600)) {
                log.warn("User rate limit exceeded (per hour): userId={}, channel={}, type={}", userId, channel, type);
                return false;
            }

            // Check per-day limit
            if (!checkUserRateLimit(userId, channel, type, "day", DEFAULT_USER_RATE_LIMIT_PER_DAY, 86400)) {
                log.warn("User rate limit exceeded (per day): userId={}, channel={}, type={}", userId, channel, type);
                return false;
            }

            return true;
            
        } catch (Exception e) {
            log.error("Error checking user rate limit: userId={}, error={}", userId, e.getMessage(), e);
            // In case of Redis error, allow the notification (fail open)
            return true;
        }
    }

    /**
     * Check if channel provider is within rate limits
     */
    public boolean isProviderWithinRateLimit(NotificationChannel channel) {
        try {
            int rateLimit = getProviderRateLimit(channel);
            String key = "rate_limit:provider:" + channel.name().toLowerCase() + ":" + getCurrentMinute();
            
            String currentCount = redisTemplate.opsForValue().get(key);
            int count = currentCount != null ? Integer.parseInt(currentCount) : 0;
            
            if (count >= rateLimit) {
                log.warn("Provider rate limit exceeded: channel={}, count={}, limit={}", channel, count, rateLimit);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error checking provider rate limit: channel={}, error={}", channel, e.getMessage(), e);
            // In case of Redis error, allow the notification (fail open)
            return true;
        }
    }

    /**
     * Check burst protection - prevent too many notifications in a short time
     */
    public boolean isBurstProtectionTriggered(Long userId, NotificationChannel channel) {
        try {
            // Check if user sent more than 5 notifications in the last 10 seconds
            String key = "burst_protection:" + userId + ":" + channel.name().toLowerCase() + ":" + getCurrentTenSeconds();
            String currentCount = redisTemplate.opsForValue().get(key);
            int count = currentCount != null ? Integer.parseInt(currentCount) : 0;
            
            if (count >= 5) {
                log.warn("Burst protection triggered: userId={}, channel={}, count={}", userId, channel, count);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking burst protection: userId={}, channel={}, error={}", userId, channel, e.getMessage(), e);
            // In case of Redis error, don't trigger burst protection
            return false;
        }
    }

    /**
     * Record notification attempt for rate limiting
     */
    public void recordNotificationAttempt(Long userId, NotificationChannel channel, NotificationType type) {
        try {
            // Record user rate limiting counters
            recordUserRateLimit(userId, channel, type, "minute", 60);
            recordUserRateLimit(userId, channel, type, "hour", 3600);
            recordUserRateLimit(userId, channel, type, "day", 86400);
            
            // Record provider rate limiting counter
            recordProviderRateLimit(channel);
            
            // Record burst protection counter
            recordBurstProtection(userId, channel);
            
            log.debug("Recorded notification attempt: userId={}, channel={}, type={}", userId, channel, type);
            
        } catch (Exception e) {
            log.error("Error recording notification attempt: userId={}, channel={}, type={}, error={}", 
                    userId, channel, type, e.getMessage(), e);
        }
    }

    /**
     * Get current rate limit status for user
     */
    public RateLimitStatus getUserRateLimitStatus(Long userId, NotificationChannel channel, NotificationType type) {
        try {
            int minuteCount = getUserRateLimitCount(userId, channel, type, "minute");
            int hourCount = getUserRateLimitCount(userId, channel, type, "hour");
            int dayCount = getUserRateLimitCount(userId, channel, type, "day");
            
            return RateLimitStatus.builder()
                    .userId(userId)
                    .channel(channel)
                    .type(type)
                    .minuteCount(minuteCount)
                    .minuteLimit(DEFAULT_USER_RATE_LIMIT_PER_MINUTE)
                    .hourCount(hourCount)
                    .hourLimit(DEFAULT_USER_RATE_LIMIT_PER_HOUR)
                    .dayCount(dayCount)
                    .dayLimit(DEFAULT_USER_RATE_LIMIT_PER_DAY)
                    .withinLimits(minuteCount < DEFAULT_USER_RATE_LIMIT_PER_MINUTE &&
                                 hourCount < DEFAULT_USER_RATE_LIMIT_PER_HOUR &&
                                 dayCount < DEFAULT_USER_RATE_LIMIT_PER_DAY)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error getting user rate limit status: userId={}, error={}", userId, e.getMessage(), e);
            return RateLimitStatus.builder()
                    .userId(userId)
                    .channel(channel)
                    .type(type)
                    .withinLimits(true) // Fail open
                    .build();
        }
    }

    /**
     * Check user rate limit for specific time window
     */
    private boolean checkUserRateLimit(Long userId, NotificationChannel channel, NotificationType type, 
                                     String timeWindow, int limit, int ttlSeconds) {
        String key = getUserRateLimitKey(userId, channel, type, timeWindow);
        String currentCount = redisTemplate.opsForValue().get(key);
        int count = currentCount != null ? Integer.parseInt(currentCount) : 0;
        return count < limit;
    }

    /**
     * Record user rate limit attempt
     */
    private void recordUserRateLimit(Long userId, NotificationChannel channel, NotificationType type, 
                                   String timeWindow, int ttlSeconds) {
        String key = getUserRateLimitKey(userId, channel, type, timeWindow);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * Get user rate limit count
     */
    private int getUserRateLimitCount(Long userId, NotificationChannel channel, NotificationType type, String timeWindow) {
        String key = getUserRateLimitKey(userId, channel, type, timeWindow);
        String count = redisTemplate.opsForValue().get(key);
        return count != null ? Integer.parseInt(count) : 0;
    }

    /**
     * Record provider rate limit attempt
     */
    private void recordProviderRateLimit(NotificationChannel channel) {
        String key = "rate_limit:provider:" + channel.name().toLowerCase() + ":" + getCurrentMinute();
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofSeconds(60));
    }

    /**
     * Record burst protection attempt
     */
    private void recordBurstProtection(Long userId, NotificationChannel channel) {
        String key = "burst_protection:" + userId + ":" + channel.name().toLowerCase() + ":" + getCurrentTenSeconds();
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofSeconds(10));
    }

    /**
     * Get user rate limit key
     */
    private String getUserRateLimitKey(Long userId, NotificationChannel channel, NotificationType type, String timeWindow) {
        String timeKey = switch (timeWindow) {
            case "minute" -> getCurrentMinute();
            case "hour" -> getCurrentHour();
            case "day" -> getCurrentDay();
            default -> getCurrentMinute();
        };
        return "rate_limit:user:" + userId + ":" + channel.name().toLowerCase() + ":" + type.name().toLowerCase() + ":" + timeKey;
    }

    /**
     * Get provider rate limit based on channel
     */
    private int getProviderRateLimit(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> EMAIL_PROVIDER_RATE_LIMIT_PER_MINUTE;
            case SMS -> SMS_PROVIDER_RATE_LIMIT_PER_MINUTE;
            case PUSH -> PUSH_PROVIDER_RATE_LIMIT_PER_MINUTE;
            case IN_APP -> INAPP_PROVIDER_RATE_LIMIT_PER_MINUTE;
            default -> 100;
        };
    }

    /**
     * Get current minute for rate limiting key
     */
    private String getCurrentMinute() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"));
    }

    /**
     * Get current hour for rate limiting key
     */
    private String getCurrentHour() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
    }

    /**
     * Get current day for rate limiting key
     */
    private String getCurrentDay() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /**
     * Get current 10-second window for burst protection
     */
    private String getCurrentTenSeconds() {
        LocalDateTime now = LocalDateTime.now();
        int tenSecondWindow = now.getSecond() / 10;
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")) + "-" + tenSecondWindow;
    }

    /**
     * Rate limit status DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class RateLimitStatus {
        private Long userId;
        private NotificationChannel channel;
        private NotificationType type;
        private int minuteCount;
        private int minuteLimit;
        private int hourCount;
        private int hourLimit;
        private int dayCount;
        private int dayLimit;
        private boolean withinLimits;
    }
}
