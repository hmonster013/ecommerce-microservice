package org.de013.shoppingcart.repository.jpa;

import org.de013.shoppingcart.entity.CartAnalytics;
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

/**
 * JPA Repository for CartAnalytics entity
 * Provides comprehensive analytics and reporting capabilities
 */
@Repository
public interface CartAnalyticsRepository extends JpaRepository<CartAnalytics, Long> {

    // ==================== EVENT TRACKING ====================

    /**
     * Find analytics by cart ID
     */
    @Query("SELECT ca FROM CartAnalytics ca WHERE ca.cartId = :cartId ORDER BY ca.eventTimestamp DESC")
    List<CartAnalytics> findByCartId(@Param("cartId") Long cartId);

    /**
     * Find analytics by user ID
     */
    @Query("SELECT ca FROM CartAnalytics ca WHERE ca.userId = :userId ORDER BY ca.eventTimestamp DESC")
    List<CartAnalytics> findByUserId(@Param("userId") String userId, Pageable pageable);

    /**
     * Find analytics by event type
     */
    @Query("SELECT ca FROM CartAnalytics ca WHERE ca.eventType = :eventType AND ca.eventTimestamp BETWEEN :startDate AND :endDate ORDER BY ca.eventTimestamp DESC")
    List<CartAnalytics> findByEventTypeAndDateRange(@Param("eventType") String eventType, 
                                                    @Param("startDate") LocalDateTime startDate, 
                                                    @Param("endDate") LocalDateTime endDate);

    // ==================== CART LIFECYCLE ANALYTICS ====================

    /**
     * Get cart conversion funnel
     */
    @Query("SELECT ca.eventType, COUNT(ca) " +
           "FROM CartAnalytics ca WHERE ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.eventType IN ('CART_CREATED', 'ITEM_ADDED', 'CHECKOUT_STARTED', 'CART_CONVERTED') " +
           "GROUP BY ca.eventType ORDER BY " +
           "CASE ca.eventType " +
           "WHEN 'CART_CREATED' THEN 1 " +
           "WHEN 'ITEM_ADDED' THEN 2 " +
           "WHEN 'CHECKOUT_STARTED' THEN 3 " +
           "WHEN 'CART_CONVERTED' THEN 4 END")
    List<Object[]> getCartConversionFunnel(@Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate);

    /**
     * Get cart abandonment analytics
     */
    @Query("SELECT COUNT(ca), AVG(ca.cartTotalAfter), AVG(ca.itemCountAfter) " +
           "FROM CartAnalytics ca WHERE ca.eventType = 'CART_ABANDONED' " +
           "AND ca.eventTimestamp BETWEEN :startDate AND :endDate")
    Object[] getCartAbandonmentAnalytics(@Param("startDate") LocalDateTime startDate, 
                                        @Param("endDate") LocalDateTime endDate);

    /**
     * Get conversion rate by time period
     */
    @Query("SELECT DATE(ca.eventTimestamp), " +
           "COUNT(CASE WHEN ca.eventType = 'CART_CREATED' THEN 1 END) as created, " +
           "COUNT(CASE WHEN ca.eventType = 'CART_CONVERTED' THEN 1 END) as converted " +
           "FROM CartAnalytics ca WHERE ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.eventType IN ('CART_CREATED', 'CART_CONVERTED') " +
           "GROUP BY DATE(ca.eventTimestamp) ORDER BY DATE(ca.eventTimestamp)")
    List<Object[]> getConversionRateByDate(@Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate);

    // ==================== PRODUCT ANALYTICS ====================

    /**
     * Get most added products
     */
    @Query("SELECT ca.productId, COUNT(ca), SUM(ca.quantityAfter) " +
           "FROM CartAnalytics ca WHERE ca.eventType = 'ITEM_ADDED' " +
           "AND ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.productId IS NOT NULL " +
           "GROUP BY ca.productId ORDER BY COUNT(ca) DESC")
    List<Object[]> getMostAddedProducts(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate, 
                                       Pageable pageable);

    /**
     * Get most removed products
     */
    @Query("SELECT ca.productId, COUNT(ca), SUM(ca.quantityBefore) " +
           "FROM CartAnalytics ca WHERE ca.eventType = 'ITEM_REMOVED' " +
           "AND ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.productId IS NOT NULL " +
           "GROUP BY ca.productId ORDER BY COUNT(ca) DESC")
    List<Object[]> getMostRemovedProducts(@Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate, 
                                         Pageable pageable);

    /**
     * Get product performance in carts
     */
    @Query("SELECT ca.productId, ca.productSku, " +
           "COUNT(CASE WHEN ca.eventType = 'ITEM_ADDED' THEN 1 END) as timesAdded, " +
           "COUNT(CASE WHEN ca.eventType = 'ITEM_REMOVED' THEN 1 END) as timesRemoved, " +
           "COUNT(DISTINCT ca.cartId) as uniqueCarts " +
           "FROM CartAnalytics ca WHERE ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.productId IS NOT NULL " +
           "GROUP BY ca.productId, ca.productSku ORDER BY timesAdded DESC")
    List<Object[]> getProductPerformanceInCarts(@Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate);

    // ==================== USER BEHAVIOR ANALYTICS ====================

    /**
     * Get user engagement metrics
     */
    @Query("SELECT ca.userId, COUNT(ca) as totalEvents, " +
           "COUNT(DISTINCT ca.cartId) as uniqueCarts, " +
           "MAX(ca.eventTimestamp) as lastActivity " +
           "FROM CartAnalytics ca WHERE ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.userId IS NOT NULL " +
           "GROUP BY ca.userId ORDER BY totalEvents DESC")
    List<Object[]> getUserEngagementMetrics(@Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate, 
                                           Pageable pageable);

    /**
     * Get session analytics
     */
    @Query("SELECT ca.sessionId, COUNT(ca) as events, " +
           "MIN(ca.eventTimestamp) as sessionStart, " +
           "MAX(ca.eventTimestamp) as sessionEnd, " +
           "COUNT(DISTINCT ca.cartId) as cartsInSession " +
           "FROM CartAnalytics ca WHERE ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.sessionId IS NOT NULL " +
           "GROUP BY ca.sessionId")
    List<Object[]> getSessionAnalytics(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);

    /**
     * Get returning user analytics
     */
    @Query("SELECT COUNT(CASE WHEN ca.isReturningUser = true THEN 1 END) as returningUsers, " +
           "COUNT(CASE WHEN ca.isReturningUser = false THEN 1 END) as newUsers, " +
           "AVG(CASE WHEN ca.isReturningUser = true THEN ca.cartTotalAfter END) as avgReturningUserCart, " +
           "AVG(CASE WHEN ca.isReturningUser = false THEN ca.cartTotalAfter END) as avgNewUserCart " +
           "FROM CartAnalytics ca WHERE ca.eventType = 'CART_CREATED' " +
           "AND ca.eventTimestamp BETWEEN :startDate AND :endDate")
    Object[] getReturningUserAnalytics(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);

    // ==================== DEVICE & PLATFORM ANALYTICS ====================

    /**
     * Get device type analytics
     */
    @Query("SELECT ca.deviceType, COUNT(ca), COUNT(DISTINCT ca.userId), COUNT(DISTINCT ca.sessionId) " +
           "FROM CartAnalytics ca WHERE ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.deviceType IS NOT NULL " +
           "GROUP BY ca.deviceType ORDER BY COUNT(ca) DESC")
    List<Object[]> getDeviceTypeAnalytics(@Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate);

    /**
     * Get browser analytics
     */
    @Query("SELECT ca.browser, COUNT(ca), COUNT(DISTINCT ca.userId) " +
           "FROM CartAnalytics ca WHERE ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.browser IS NOT NULL " +
           "GROUP BY ca.browser ORDER BY COUNT(ca) DESC")
    List<Object[]> getBrowserAnalytics(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);

    /**
     * Get geographic analytics
     */
    @Query("SELECT ca.countryCode, ca.region, COUNT(ca), COUNT(DISTINCT ca.userId) " +
           "FROM CartAnalytics ca WHERE ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.countryCode IS NOT NULL " +
           "GROUP BY ca.countryCode, ca.region ORDER BY COUNT(ca) DESC")
    List<Object[]> getGeographicAnalytics(@Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate);

    // ==================== MARKETING ANALYTICS ====================

    /**
     * Get UTM campaign analytics
     */
    @Query("SELECT ca.utmCampaign, ca.utmSource, ca.utmMedium, " +
           "COUNT(ca) as events, COUNT(DISTINCT ca.userId) as uniqueUsers, " +
           "COUNT(CASE WHEN ca.eventType = 'CART_CONVERTED' THEN 1 END) as conversions " +
           "FROM CartAnalytics ca WHERE ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.utmCampaign IS NOT NULL " +
           "GROUP BY ca.utmCampaign, ca.utmSource, ca.utmMedium " +
           "ORDER BY events DESC")
    List<Object[]> getUTMCampaignAnalytics(@Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate);

    /**
     * Get referrer analytics
     */
    @Query("SELECT ca.referrerUrl, COUNT(ca), COUNT(DISTINCT ca.userId) " +
           "FROM CartAnalytics ca WHERE ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.referrerUrl IS NOT NULL " +
           "GROUP BY ca.referrerUrl ORDER BY COUNT(ca) DESC")
    List<Object[]> getReferrerAnalytics(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate, 
                                       Pageable pageable);

    // ==================== PERFORMANCE ANALYTICS ====================

    /**
     * Get average processing time by event type
     */
    @Query("SELECT ca.eventType, AVG(ca.processingTimeMs), COUNT(ca) " +
           "FROM CartAnalytics ca WHERE ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "AND ca.processingTimeMs IS NOT NULL " +
           "GROUP BY ca.eventType ORDER BY AVG(ca.processingTimeMs) DESC")
    List<Object[]> getProcessingTimeAnalytics(@Param("startDate") LocalDateTime startDate, 
                                             @Param("endDate") LocalDateTime endDate);

    /**
     * Find slow operations
     */
    @Query("SELECT ca FROM CartAnalytics ca WHERE ca.processingTimeMs > :threshold " +
           "AND ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY ca.processingTimeMs DESC")
    List<CartAnalytics> findSlowOperations(@Param("threshold") Long threshold, 
                                          @Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate, 
                                          Pageable pageable);

    // ==================== ERROR ANALYTICS ====================

    /**
     * Get error analytics
     */
    @Query("SELECT ca.eventType, COUNT(ca), ca.errorMessage " +
           "FROM CartAnalytics ca WHERE ca.errorMessage IS NOT NULL " +
           "AND ca.eventTimestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY ca.eventType, ca.errorMessage ORDER BY COUNT(ca) DESC")
    List<Object[]> getErrorAnalytics(@Param("startDate") LocalDateTime startDate, 
                                    @Param("endDate") LocalDateTime endDate);

    // ==================== CLEANUP OPERATIONS ====================

    /**
     * Clean up old analytics data
     */
    @Modifying
    @Query("DELETE FROM CartAnalytics ca WHERE ca.eventTimestamp < :cutoffDate")
    int cleanupOldAnalytics(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Archive analytics data
     */
    @Modifying
    @Query("UPDATE CartAnalytics ca SET ca.additionalData = CONCAT(COALESCE(ca.additionalData, ''), ',archived=true') WHERE ca.eventTimestamp < :cutoffDate")
    int archiveOldAnalytics(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ==================== SEARCH & FILTERING ====================

    /**
     * Search analytics by criteria
     */
    @Query("SELECT ca FROM CartAnalytics ca WHERE " +
           "(:cartId IS NULL OR ca.cartId = :cartId) AND " +
           "(:userId IS NULL OR ca.userId = :userId) AND " +
           "(:eventType IS NULL OR ca.eventType = :eventType) AND " +
           "(:productId IS NULL OR ca.productId = :productId) AND " +
           "(:startDate IS NULL OR ca.eventTimestamp >= :startDate) AND " +
           "(:endDate IS NULL OR ca.eventTimestamp <= :endDate) " +
           "ORDER BY ca.eventTimestamp DESC")
    Page<CartAnalytics> searchAnalytics(@Param("cartId") Long cartId,
                                       @Param("userId") String userId,
                                       @Param("eventType") String eventType,
                                       @Param("productId") String productId,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate,
                                       Pageable pageable);
}
