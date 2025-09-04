package org.de013.notificationservice.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache Configuration for Notification Service
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Cache manager for notification service
     */
    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(java.util.List.of(
                "notification-templates",
                "notification-preferences"
        ));
        return cacheManager;
    }
}
