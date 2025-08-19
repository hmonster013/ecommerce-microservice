package org.de013.orderservice.redis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Redis cache model for frequently accessed orders
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash("order_cache")
public class OrderCache implements Serializable {
    @Id
    private String id; // key, e.g., order:{orderId}

    @Indexed
    private Long orderId;

    @Indexed
    private String orderNumber;

    private Long userId;

    private String status;

    private String currency;

    private String totalAmount; // store as string to avoid BigDecimal serialization issues

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String shippingCity;
    private String shippingCountry;

    private List<String> itemSkus;

    @TimeToLive
    private Long ttlSeconds; // null = no TTL
}

