package org.de013.shoppingcart.repository.redis;

import org.de013.shoppingcart.entity.RedisCart;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Redis Repository for RedisCart entity
 * Provides high-speed cart operations using Spring Data Redis
 */
@Repository
public interface RedisCartRepository extends CrudRepository<RedisCart, String> {

    /**
     * Find cart by user ID
     */
    Optional<RedisCart> findByUserId(String userId);

    /**
     * Find cart by session ID
     */
    Optional<RedisCart> findBySessionId(String sessionId);

    /**
     * Find cart by cart ID
     */
    Optional<RedisCart> findByCartId(Long cartId);

    /**
     * Find all carts by user ID
     */
    List<RedisCart> findByUserIdOrderByUpdatedAtDesc(String userId);

    /**
     * Find carts by status
     */
    List<RedisCart> findByStatus(org.de013.shoppingcart.entity.enums.CartStatus status);

    /**
     * Find carts by cart type
     */
    List<RedisCart> findByCartType(org.de013.shoppingcart.entity.enums.CartType cartType);

    /**
     * Check if cart exists by user ID
     */
    boolean existsByUserId(String userId);

    /**
     * Check if cart exists by session ID
     */
    boolean existsBySessionId(String sessionId);

    /**
     * Delete cart by user ID
     */
    void deleteByUserId(String userId);

    /**
     * Delete cart by session ID
     */
    void deleteBySessionId(String sessionId);

    /**
     * Delete cart by cart ID
     */
    void deleteByCartId(Long cartId);
}
