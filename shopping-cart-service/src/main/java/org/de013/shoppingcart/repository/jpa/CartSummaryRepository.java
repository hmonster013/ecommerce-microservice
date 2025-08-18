package org.de013.shoppingcart.repository.jpa;

import org.de013.shoppingcart.entity.CartSummary;
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
 * JPA Repository for CartSummary entity
 * Provides cart summary data access and pricing calculations
 */
@Repository
public interface CartSummaryRepository extends JpaRepository<CartSummary, Long> {

    // ==================== BASIC FINDER METHODS ====================

    /**
     * Find summary by cart ID
     */
    @Query("SELECT cs FROM CartSummary cs WHERE cs.cart.id = :cartId AND cs.deleted = false")
    Optional<CartSummary> findByCartId(@Param("cartId") Long cartId);

    /**
     * Find summary with cart details
     */
    @Query("SELECT cs FROM CartSummary cs JOIN FETCH cs.cart WHERE cs.cart.id = :cartId AND cs.deleted = false")
    Optional<CartSummary> findByCartIdWithCart(@Param("cartId") Long cartId);

    // ==================== PRICING ANALYTICS ====================

    /**
     * Get average order value by date range
     */
    @Query("SELECT AVG(cs.totalAmount) FROM CartSummary cs WHERE cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false")
    BigDecimal getAverageOrderValue(@Param("startDate") LocalDateTime startDate, 
                                   @Param("endDate") LocalDateTime endDate);

    /**
     * Get total revenue by date range
     */
    @Query("SELECT SUM(cs.totalAmount) FROM CartSummary cs WHERE cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false")
    BigDecimal getTotalRevenue(@Param("startDate") LocalDateTime startDate, 
                              @Param("endDate") LocalDateTime endDate);

    /**
     * Get discount analytics
     */
    @Query("SELECT SUM(cs.discountAmount), AVG(cs.discountAmount), COUNT(cs) " +
           "FROM CartSummary cs WHERE cs.discountAmount > 0 AND cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false")
    Object[] getDiscountAnalytics(@Param("startDate") LocalDateTime startDate, 
                                 @Param("endDate") LocalDateTime endDate);

    /**
     * Get shipping analytics
     */
    @Query("SELECT cs.shippingMethod, COUNT(cs), AVG(cs.shippingCost), SUM(cs.shippingCost) " +
           "FROM CartSummary cs WHERE cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false " +
           "GROUP BY cs.shippingMethod ORDER BY COUNT(cs) DESC")
    List<Object[]> getShippingAnalytics(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Get tax analytics
     */
    @Query("SELECT SUM(cs.taxAmount), AVG(cs.taxRate), COUNT(cs) " +
           "FROM CartSummary cs WHERE cs.taxAmount > 0 AND cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false")
    Object[] getTaxAnalytics(@Param("startDate") LocalDateTime startDate, 
                            @Param("endDate") LocalDateTime endDate);

    // ==================== FREE SHIPPING ANALYTICS ====================

    /**
     * Get free shipping eligibility stats
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN cs.isFreeShippingEligible = true THEN 1 END) as eligibleCount, " +
           "COUNT(CASE WHEN cs.isFreeShippingEligible = false THEN 1 END) as notEligibleCount, " +
           "AVG(CASE WHEN cs.isFreeShippingEligible = false THEN cs.amountNeededForFreeShipping END) as avgAmountNeeded " +
           "FROM CartSummary cs WHERE cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false")
    Object[] getFreeShippingStats(@Param("startDate") LocalDateTime startDate, 
                                 @Param("endDate") LocalDateTime endDate);

    /**
     * Find carts close to free shipping threshold
     */
    @Query("SELECT cs FROM CartSummary cs WHERE cs.isFreeShippingEligible = false AND cs.amountNeededForFreeShipping <= :threshold AND cs.deleted = false ORDER BY cs.amountNeededForFreeShipping ASC")
    List<CartSummary> findCartsCloseToFreeShipping(@Param("threshold") BigDecimal threshold);

    // ==================== LOYALTY PROGRAM ANALYTICS ====================

    /**
     * Get loyalty points analytics
     */
    @Query("SELECT SUM(cs.loyaltyPointsEarned), SUM(cs.loyaltyPointsUsed), SUM(cs.loyaltyDiscountAmount) " +
           "FROM CartSummary cs WHERE cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false")
    Object[] getLoyaltyAnalytics(@Param("startDate") LocalDateTime startDate, 
                                @Param("endDate") LocalDateTime endDate);

    /**
     * Find high loyalty point earners
     */
    @Query("SELECT cs FROM CartSummary cs WHERE cs.loyaltyPointsEarned >= :minPoints AND cs.deleted = false ORDER BY cs.loyaltyPointsEarned DESC")
    List<CartSummary> findHighLoyaltyPointEarners(@Param("minPoints") Integer minPoints);

    // ==================== GIFT WRAP ANALYTICS ====================

    /**
     * Get gift wrap revenue and statistics
     */
    @Query("SELECT COUNT(cs), SUM(cs.giftWrapCost), AVG(cs.giftWrapCost) " +
           "FROM CartSummary cs WHERE cs.giftWrapCost > 0 AND cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false")
    Object[] getGiftWrapAnalytics(@Param("startDate") LocalDateTime startDate, 
                                 @Param("endDate") LocalDateTime endDate);

    // ==================== PRICING RULES ANALYTICS ====================

    /**
     * Get most applied pricing rules
     */
    @Query("SELECT cs.pricingRulesApplied, COUNT(cs) " +
           "FROM CartSummary cs WHERE cs.pricingRulesApplied IS NOT NULL AND cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false " +
           "GROUP BY cs.pricingRulesApplied ORDER BY COUNT(cs) DESC")
    List<Object[]> getMostAppliedPricingRules(@Param("startDate") LocalDateTime startDate, 
                                             @Param("endDate") LocalDateTime endDate);

    /**
     * Get promotion code usage
     */
    @Query("SELECT cs.promotionCodesApplied, COUNT(cs), SUM(cs.discountAmount) " +
           "FROM CartSummary cs WHERE cs.promotionCodesApplied IS NOT NULL AND cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false " +
           "GROUP BY cs.promotionCodesApplied ORDER BY COUNT(cs) DESC")
    List<Object[]> getPromotionCodeUsage(@Param("startDate") LocalDateTime startDate, 
                                        @Param("endDate") LocalDateTime endDate);

    // ==================== UPDATE OPERATIONS ====================

    /**
     * Update cart summary totals
     */
    @Modifying
    @Query("UPDATE CartSummary cs SET " +
           "cs.subtotal = :subtotal, " +
           "cs.taxAmount = :taxAmount, " +
           "cs.shippingCost = :shippingCost, " +
           "cs.discountAmount = :discountAmount, " +
           "cs.totalAmount = :totalAmount, " +
           "cs.calculationTimestamp = :currentTime, " +
           "cs.updatedAt = :currentTime " +
           "WHERE cs.cart.id = :cartId")
    int updateCartSummaryTotals(@Param("cartId") Long cartId,
                               @Param("subtotal") BigDecimal subtotal,
                               @Param("taxAmount") BigDecimal taxAmount,
                               @Param("shippingCost") BigDecimal shippingCost,
                               @Param("discountAmount") BigDecimal discountAmount,
                               @Param("totalAmount") BigDecimal totalAmount,
                               @Param("currentTime") LocalDateTime currentTime);

    /**
     * Update shipping information
     */
    @Modifying
    @Query("UPDATE CartSummary cs SET " +
           "cs.shippingCost = :shippingCost, " +
           "cs.shippingMethod = :shippingMethod, " +
           "cs.shippingEstimatedDays = :estimatedDays, " +
           "cs.estimatedDeliveryDate = :deliveryDate, " +
           "cs.updatedAt = :currentTime " +
           "WHERE cs.cart.id = :cartId")
    int updateShippingInfo(@Param("cartId") Long cartId,
                          @Param("shippingCost") BigDecimal shippingCost,
                          @Param("shippingMethod") String shippingMethod,
                          @Param("estimatedDays") Integer estimatedDays,
                          @Param("deliveryDate") LocalDateTime deliveryDate,
                          @Param("currentTime") LocalDateTime currentTime);

    /**
     * Update free shipping eligibility
     */
    @Modifying
    @Query("UPDATE CartSummary cs SET " +
           "cs.isFreeShippingEligible = :isEligible, " +
           "cs.freeShippingThreshold = :threshold, " +
           "cs.amountNeededForFreeShipping = :amountNeeded, " +
           "cs.updatedAt = :currentTime " +
           "WHERE cs.cart.id = :cartId")
    int updateFreeShippingEligibility(@Param("cartId") Long cartId,
                                     @Param("isEligible") Boolean isEligible,
                                     @Param("threshold") BigDecimal threshold,
                                     @Param("amountNeeded") BigDecimal amountNeeded,
                                     @Param("currentTime") LocalDateTime currentTime);

    // ==================== CLEANUP OPERATIONS ====================

    /**
     * Delete summaries for deleted carts
     */
    @Modifying
    @Query("UPDATE CartSummary cs SET cs.deleted = true, cs.deletedAt = :currentTime, cs.deletedBy = 'SYSTEM_CLEANUP' WHERE cs.cart.deleted = true AND cs.deleted = false")
    int cleanupDeletedCartSummaries(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find summaries without valid cart
     */
    @Query("SELECT cs FROM CartSummary cs WHERE cs.cart IS NULL OR cs.cart.deleted = true")
    List<CartSummary> findOrphanedSummaries();

    // ==================== REPORTING QUERIES ====================

    /**
     * Get daily revenue report
     */
    @Query("SELECT DATE(cs.calculationTimestamp), COUNT(cs), SUM(cs.totalAmount), AVG(cs.totalAmount) " +
           "FROM CartSummary cs WHERE cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false " +
           "GROUP BY DATE(cs.calculationTimestamp) ORDER BY DATE(cs.calculationTimestamp)")
    List<Object[]> getDailyRevenueReport(@Param("startDate") LocalDateTime startDate, 
                                        @Param("endDate") LocalDateTime endDate);

    /**
     * Get cart size distribution
     */
    @Query("SELECT " +
           "CASE " +
           "WHEN cs.itemCount <= 1 THEN '1 item' " +
           "WHEN cs.itemCount <= 3 THEN '2-3 items' " +
           "WHEN cs.itemCount <= 5 THEN '4-5 items' " +
           "WHEN cs.itemCount <= 10 THEN '6-10 items' " +
           "ELSE '10+ items' END as cartSize, " +
           "COUNT(cs), AVG(cs.totalAmount) " +
           "FROM CartSummary cs WHERE cs.calculationTimestamp BETWEEN :startDate AND :endDate AND cs.deleted = false " +
           "GROUP BY " +
           "CASE " +
           "WHEN cs.itemCount <= 1 THEN '1 item' " +
           "WHEN cs.itemCount <= 3 THEN '2-3 items' " +
           "WHEN cs.itemCount <= 5 THEN '4-5 items' " +
           "WHEN cs.itemCount <= 10 THEN '6-10 items' " +
           "ELSE '10+ items' END " +
           "ORDER BY MIN(cs.itemCount)")
    List<Object[]> getCartSizeDistribution(@Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate);
}
