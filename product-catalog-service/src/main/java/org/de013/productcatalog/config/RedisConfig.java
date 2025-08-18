package org.de013.productcatalog.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis configuration for caching and performance optimization.
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.timeout:2000}")
    private int redisTimeout;

    /**
     * Redis connection factory configuration
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        redisConfig.setDatabase(redisDatabase);
        
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }

        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(redisConfig);
        jedisConnectionFactory.getPoolConfig().setMaxTotal(20);
        jedisConnectionFactory.getPoolConfig().setMaxIdle(10);
        jedisConnectionFactory.getPoolConfig().setMinIdle(5);
        jedisConnectionFactory.getPoolConfig().setTestOnBorrow(true);
        jedisConnectionFactory.getPoolConfig().setTestOnReturn(true);
        jedisConnectionFactory.getPoolConfig().setTestWhileIdle(true);
        
        log.info("Configured Redis connection to {}:{} database {}", redisHost, redisPort, redisDatabase);
        return jedisConnectionFactory;
    }

    /**
     * Redis template with JSON serialization
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // JSON serializer configuration using GenericJackson2JsonRedisSerializer
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        objectMapper.registerModule(new JavaTimeModule());

        // Use GenericJackson2JsonRedisSerializer instead of deprecated approach
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // String serializer for keys
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // Set serializers
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setValueSerializer(jsonRedisSerializer);
        template.setHashValueSerializer(jsonRedisSerializer);
        template.setDefaultSerializer(jsonRedisSerializer);
        template.setEnableDefaultSerializer(true);

        template.afterPropertiesSet();
        
        log.info("Configured Redis template with JSON serialization");
        return template;
    }

    /**
     * Cache manager with different TTL for different cache types
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30)) // Default TTL: 30 minutes
                .disableCachingNullValues();

        // Different TTL for different cache types
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Product caches - longer TTL as products don't change frequently
        cacheConfigurations.put("products", defaultConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put("productDetails", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("productsByCategory", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("featuredProducts", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("popularProducts", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("similarProducts", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("trendingProducts", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Category caches - very long TTL as categories rarely change
        cacheConfigurations.put("categories", defaultConfig.entryTtl(Duration.ofHours(6)));
        cacheConfigurations.put("categoryTree", defaultConfig.entryTtl(Duration.ofHours(12)));
        cacheConfigurations.put("categoryHierarchy", defaultConfig.entryTtl(Duration.ofHours(6)));
        cacheConfigurations.put("categoryBySlug", defaultConfig.entryTtl(Duration.ofHours(4)));
        
        // Search caches - shorter TTL as search results can change frequently
        cacheConfigurations.put("searchResults", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("searchSuggestions", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("popularSearches", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("autocompleteSuggestions", defaultConfig.entryTtl(Duration.ofMinutes(20)));
        
        // Inventory caches - very short TTL as stock changes frequently
        cacheConfigurations.put("inventory", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigurations.put("stockStatus", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        
        // Review caches - medium TTL
        cacheConfigurations.put("reviews", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("reviewSummary", defaultConfig.entryTtl(Duration.ofMinutes(20)));
        
        // Analytics caches - short TTL for real-time data
        cacheConfigurations.put("analytics", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("metrics", defaultConfig.entryTtl(Duration.ofMinutes(3)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();

        log.info("Configured Redis cache manager with {} cache configurations", cacheConfigurations.size());
        return cacheManager;
    }

    /**
     * Redis template for string operations
     */
    @Bean("customStringRedisTemplate")
    public RedisTemplate<String, String> customStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        
        log.info("Configured string Redis template");
        return template;
    }
}
