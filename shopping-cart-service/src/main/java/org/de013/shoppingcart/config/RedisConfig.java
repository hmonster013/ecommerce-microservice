package org.de013.shoppingcart.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Configuration for Shopping Cart Service
 * Configures Redis connection, serialization, and caching strategies
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${shopping-cart.cache.ttl.active-cart:86400}")
    private long activeCartTtl;

    @Value("${shopping-cart.cache.ttl.guest-cart:3600}")
    private long guestCartTtl;

    @Value("${shopping-cart.cache.ttl.session-cart:1800}")
    private long sessionCartTtl;

    /**
     * Configure ObjectMapper for Redis serialization
     */
    @Bean("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Configure ObjectMapper for HTTP requests (without type information)
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Configure RedisTemplate with JSON serialization
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Use JSON serializer for values
        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(redisObjectMapper(), Object.class);
        
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.setDefaultSerializer(jsonSerializer);
        template.afterPropertiesSet();
        
        return template;
    }

    /**
     * Configure specialized RedisTemplate for cart operations
     */
    @Bean("cartRedisTemplate")
    public RedisTemplate<String, Object> cartRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Generic JSON serializer for cart objects
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Configure Cache Manager with different TTL for different cache types
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(activeCartTtl))
                .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(redisObjectMapper(), Object.class)));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Active user carts - 24 hours
        cacheConfigurations.put("active-carts", defaultConfig.entryTtl(Duration.ofSeconds(activeCartTtl)));
        
        // Guest carts - 1 hour
        cacheConfigurations.put("guest-carts", defaultConfig.entryTtl(Duration.ofSeconds(guestCartTtl)));
        
        // Session carts - 30 minutes
        cacheConfigurations.put("session-carts", defaultConfig.entryTtl(Duration.ofSeconds(sessionCartTtl)));
        
        // Product info cache - 5 minutes
        cacheConfigurations.put("product-info", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Cart validation cache - 2 minutes
        cacheConfigurations.put("cart-validation", defaultConfig.entryTtl(Duration.ofMinutes(2)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Configure Redis Message Listener Container for pub/sub
     */
    @Bean
    public org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        org.springframework.data.redis.listener.RedisMessageListenerContainer container =
            new org.springframework.data.redis.listener.RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    /**
     * Configure Redis Script for atomic operations
     */
    @Bean
    public org.springframework.data.redis.core.script.DefaultRedisScript<Long> cartLockScript() {
        org.springframework.data.redis.core.script.DefaultRedisScript<Long> script =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>();
        script.setScriptText(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else " +
            "return 0 " +
            "end"
        );
        script.setResultType(Long.class);
        return script;
    }

    /**
     * Configure Redis Script for cart TTL extension
     */
    @Bean
    public org.springframework.data.redis.core.script.DefaultRedisScript<Boolean> extendTtlScript() {
        org.springframework.data.redis.core.script.DefaultRedisScript<Boolean> script =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>();
        script.setScriptText(
            "if redis.call('exists', KEYS[1]) == 1 then " +
            "redis.call('expire', KEYS[1], ARGV[1]) " +
            "return true " +
            "else " +
            "return false " +
            "end"
        );
        script.setResultType(Boolean.class);
        return script;
    }
}
