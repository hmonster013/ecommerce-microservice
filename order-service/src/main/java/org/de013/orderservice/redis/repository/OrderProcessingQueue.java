package org.de013.orderservice.redis.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Simple Redis-backed queue for async order processing.
 */
@Component
@RequiredArgsConstructor
public class OrderProcessingQueue {

    private final RedisTemplate<String, String> redisTemplate;

    private String queueKey(String name) {
        return "order:queue:" + name;
    }

    public void enqueue(String queueName, String payload) {
        redisTemplate.opsForList().leftPush(queueKey(queueName), payload);
    }

    public String dequeue(String queueName) {
        return redisTemplate.opsForList().rightPop(queueKey(queueName));
    }

    public String blockingDequeue(String queueName, Duration timeout) {
        Long timeoutSeconds = timeout != null ? timeout.getSeconds() : 0L;
        return redisTemplate.opsForList().rightPop(queueKey(queueName), timeoutSeconds, TimeUnit.SECONDS);
    }

    public Long size(String queueName) {
        Long size = redisTemplate.opsForList().size(queueKey(queueName));
        return size != null ? size : 0L;
    }
}

