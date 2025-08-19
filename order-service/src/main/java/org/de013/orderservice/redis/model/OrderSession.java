package org.de013.orderservice.redis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Temporary order data stored in Redis during processing sessions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@RedisHash("order_session")
public class OrderSession implements Serializable {
    @Id
    private String id; // key, e.g., order:session:{orderId}

    private Long orderId;
    private Long userId;

    private String stage; // INIT, VALIDATED, PAYMENT_AUTH, STOCK_RESERVED, SHIPPED, etc.

    private Map<String, String> context; // arbitrary context data

    private LocalDateTime startedAt;
    private LocalDateTime lastUpdatedAt;

    @TimeToLive
    private Long ttlSeconds; // session expiry
}

