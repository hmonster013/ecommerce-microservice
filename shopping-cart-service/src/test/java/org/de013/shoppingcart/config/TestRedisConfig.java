package org.de013.shoppingcart.config;

import org.de013.shoppingcart.repository.redis.RedisCartRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@TestConfiguration
public class TestRedisConfig {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    @Primary
    public RedisCartRepository redisCartRepository() {
        return Mockito.mock(RedisCartRepository.class);
    }

    @Bean("cartRedisTemplate")
    @Primary
    public RedisTemplate<String, Object> cartRedisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }

    @Bean
    @Primary
    public CacheManager cacheManager() {
        return Mockito.mock(CacheManager.class);
    }
}
