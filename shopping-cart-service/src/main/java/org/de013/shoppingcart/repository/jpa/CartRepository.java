package org.de013.shoppingcart.repository.jpa;

import org.de013.shoppingcart.entity.Cart;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.entity.enums.CartType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for Cart entity
 * Provides comprehensive cart data access with custom queries and batch operations
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    // ==================== BASIC FINDER METHODS ====================

    /**
     * Find active cart by user ID
     */
    @Query("SELECT c FROM Cart c WHERE c.userId = :userId AND c.status = :status AND c.deleted = false")
    Optional<Cart> findByUserIdAndStatus(@Param("userId") String userId, @Param("status") CartStatus status);

    /**
     * Find active cart by session ID
     */
    @Query("SELECT c FROM Cart c WHERE c.sessionId = :sessionId AND c.status = :status AND c.deleted = false")
    Optional<Cart> findBySessionIdAndStatus(@Param("sessionId") String sessionId, @Param("status") CartStatus status);

    /**
     * Find cart by user ID and cart type
     */
    @Query("SELECT c FROM Cart c WHERE c.userId = :userId AND c.cartType = :cartType AND c.deleted = false")
    List<Cart> findByUserIdAndCartType(@Param("userId") String userId, @Param("cartType") CartType cartType);

    /**
     * Find all carts by user ID
     */
    @Query("SELECT c FROM Cart c WHERE c.userId = :userId AND c.deleted = false ORDER BY c.lastActivityAt DESC")
    List<Cart> findByUserIdOrderByLastActivityAtDesc(@Param("userId") String userId);

    /**
     * Find cart with items by cart ID
     */
    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.cartItems WHERE c.id = :cartId AND c.deleted = false")
    Optional<Cart> findByIdWithItems(@Param("cartId") Long cartId);

    /**
     * Find cart by user ID with items
     */
    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.cartItems WHERE c.userId = :userId AND c.status = :status AND c.deleted = false")
    Optional<Cart> findByUserIdWithItems(@Param("userId") String userId, @Param("status") CartStatus status);

    // ==================== EXPIRATION & CLEANUP QUERIES ====================

    /**
     * Find expired carts
     */
    @Query("SELECT c FROM Cart c WHERE c.expiresAt < :currentTime AND c.status IN :statuses AND c.deleted = false")
    List<Cart> findExpiredCarts(@Param("currentTime") LocalDateTime currentTime, @Param("statuses") List<CartStatus> statuses);

    /**
     * Find carts to cleanup (old abandoned carts)
     */
    @Query("SELECT c FROM Cart c WHERE c.lastActivityAt < :cutoffTime AND c.status = :status AND c.deleted = false")
    List<Cart> findCartsForCleanup(@Param("cutoffTime") LocalDateTime cutoffTime, @Param("status") CartStatus status);

    /**
     * Mark expired carts as expired
     */
    @Modifying
    @Query("UPDATE Cart c SET c.status = :expiredStatus, c.updatedAt = :currentTime WHERE c.expiresAt < :currentTime AND c.status IN :activeStatuses AND c.deleted = false")
    int markExpiredCarts(@Param("expiredStatus") CartStatus expiredStatus, 
                        @Param("currentTime") LocalDateTime currentTime, 
                        @Param("activeStatuses") List<CartStatus> activeStatuses);

    /**
     * Soft delete old carts
     */
    @Modifying
    @Query("UPDATE Cart c SET c.deleted = true, c.deletedAt = :currentTime, c.deletedBy = 'SYSTEM_CLEANUP' WHERE c.lastActivityAt < :cutoffTime AND c.status IN :statuses")
    int softDeleteOldCarts(@Param("cutoffTime") LocalDateTime cutoffTime, 
                          @Param("currentTime") LocalDateTime currentTime, 
                          @Param("statuses") List<CartStatus> statuses);

    // ==================== ANALYTICS & REPORTING QUERIES ====================

    /**
     * Count active carts by user
     */
    @Query("SELECT COUNT(c) FROM Cart c WHERE c.userId = :userId AND c.status = :status AND c.deleted = false")
    long countActiveCartsByUser(@Param("userId") String userId, @Param("status") CartStatus status);







    // ==================== BUSINESS LOGIC QUERIES ====================

    /**
     * Find carts for merge (guest to user)
     */
    @Query("SELECT c FROM Cart c WHERE c.sessionId = :sessionId AND c.status = :status AND c.deleted = false")
    List<Cart> findCartsForMerge(@Param("sessionId") String sessionId, @Param("status") CartStatus status);

    /**
     * Update cart last activity
     */
    @Modifying
    @Query("UPDATE Cart c SET c.lastActivityAt = :currentTime, c.updatedAt = :currentTime WHERE c.id = :cartId")
    int updateLastActivity(@Param("cartId") Long cartId, @Param("currentTime") LocalDateTime currentTime);

    /**
     * Update cart totals
     */
    @Modifying
    @Query("UPDATE Cart c SET c.subtotal = :subtotal, c.totalAmount = :totalAmount, c.itemCount = :itemCount, c.totalQuantity = :totalQuantity, c.updatedAt = :currentTime WHERE c.id = :cartId")
    int updateCartTotals(@Param("cartId") Long cartId, 
                        @Param("subtotal") BigDecimal subtotal, 
                        @Param("totalAmount") BigDecimal totalAmount, 
                        @Param("itemCount") Integer itemCount, 
                        @Param("totalQuantity") Integer totalQuantity, 
                        @Param("currentTime") LocalDateTime currentTime);

    /**
     * Apply coupon to cart
     */
    @Modifying
    @Query("UPDATE Cart c SET c.couponCode = :couponCode, c.discountAmount = :discountAmount, c.updatedAt = :currentTime WHERE c.id = :cartId")
    int applyCoupon(@Param("cartId") Long cartId, 
                   @Param("couponCode") String couponCode, 
                   @Param("discountAmount") BigDecimal discountAmount, 
                   @Param("currentTime") LocalDateTime currentTime);

    /**
     * Remove coupon from cart
     */
    @Modifying
    @Query("UPDATE Cart c SET c.couponCode = null, c.discountAmount = 0, c.updatedAt = :currentTime WHERE c.id = :cartId")
    int removeCoupon(@Param("cartId") Long cartId, @Param("currentTime") LocalDateTime currentTime);

    /**
     * Convert cart to order
     */
    @Modifying
    @Query("UPDATE Cart c SET c.status = :convertedStatus, c.convertedToOrderId = :orderId, c.updatedAt = :currentTime WHERE c.id = :cartId")
    int convertToOrder(@Param("cartId") Long cartId, 
                      @Param("convertedStatus") CartStatus convertedStatus, 
                      @Param("orderId") String orderId, 
                      @Param("currentTime") LocalDateTime currentTime);

    // ==================== SEARCH & FILTERING ====================

    /**
     * Search carts by criteria
     */
    @Query("SELECT c FROM Cart c WHERE " +
           "(:userId IS NULL OR c.userId = :userId) AND " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:cartType IS NULL OR c.cartType = :cartType) AND " +
           "(:minAmount IS NULL OR c.totalAmount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR c.totalAmount <= :maxAmount) AND " +
           "(:startDate IS NULL OR c.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR c.createdAt <= :endDate) AND " +
           "c.deleted = false " +
           "ORDER BY c.lastActivityAt DESC")
    Page<Cart> searchCarts(@Param("userId") String userId,
                          @Param("status") CartStatus status,
                          @Param("cartType") CartType cartType,
                          @Param("minAmount") BigDecimal minAmount,
                          @Param("maxAmount") BigDecimal maxAmount,
                          @Param("startDate") LocalDateTime startDate,
                          @Param("endDate") LocalDateTime endDate,
                          Pageable pageable);

    /**
     * Find carts by product ID (for product impact analysis)
     */
    @Query("SELECT DISTINCT c FROM Cart c JOIN c.cartItems ci WHERE ci.productId = :productId AND c.deleted = false")
    List<Cart> findCartsByProductId(@Param("productId") String productId);

    /**
     * Count carts by status and date range
     */
    @Query("SELECT COUNT(c) FROM Cart c WHERE c.status = :status AND c.createdAt BETWEEN :startDate AND :endDate AND c.deleted = false")
    long countCartsByStatusAndDateRange(@Param("status") CartStatus status, 
                                       @Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate);

    // ==================== BATCH OPERATIONS ====================

    /**
     * Batch update cart status
     */
    @Modifying
    @Query("UPDATE Cart c SET c.status = :newStatus, c.updatedAt = :currentTime WHERE c.id IN :cartIds")
    int batchUpdateStatus(@Param("cartIds") List<Long> cartIds, 
                         @Param("newStatus") CartStatus newStatus, 
                         @Param("currentTime") LocalDateTime currentTime);

    /**
     * Batch soft delete carts
     */
    @Modifying
    @Query("UPDATE Cart c SET c.deleted = true, c.deletedAt = :currentTime, c.deletedBy = :deletedBy WHERE c.id IN :cartIds")
    int batchSoftDelete(@Param("cartIds") List<Long> cartIds, 
                       @Param("currentTime") LocalDateTime currentTime, 
                       @Param("deletedBy") String deletedBy);

    // ==================== EXISTENCE CHECKS ====================

    /**
     * Check if user has active cart
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Cart c WHERE c.userId = :userId AND c.status = :status AND c.deleted = false")
    boolean existsByUserIdAndStatus(@Param("userId") String userId, @Param("status") CartStatus status);

    /**
     * Check if session has active cart
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Cart c WHERE c.sessionId = :sessionId AND c.status = :status AND c.deleted = false")
    boolean existsBySessionIdAndStatus(@Param("sessionId") String sessionId, @Param("status") CartStatus status);
}
