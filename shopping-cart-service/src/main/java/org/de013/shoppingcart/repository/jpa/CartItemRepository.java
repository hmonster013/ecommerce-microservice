package org.de013.shoppingcart.repository.jpa;

import org.de013.shoppingcart.entity.CartItem;
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
 * JPA Repository for CartItem entity
 * Provides cart item data access with batch operations and analytics
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // ==================== BASIC FINDER METHODS ====================

    /**
     * Find all items in a cart
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.deleted = false ORDER BY ci.addedAt ASC")
    List<CartItem> findByCartId(@Param("cartId") Long cartId);

    /**
     * Find item by cart and product
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.productId = :productId AND ci.deleted = false")
    Optional<CartItem> findByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") String productId);

    /**
     * Find item by cart, product and variant
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.productId = :productId AND " +
           "(:variantId IS NULL AND ci.variantId IS NULL OR ci.variantId = :variantId) AND ci.deleted = false")
    Optional<CartItem> findByCartIdAndProductIdAndVariantId(@Param("cartId") Long cartId, 
                                                           @Param("productId") String productId, 
                                                           @Param("variantId") String variantId);

    /**
     * Find items by product ID across all carts
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.productId = :productId AND ci.deleted = false")
    List<CartItem> findByProductId(@Param("productId") String productId);

    /**
     * Find items by multiple product IDs
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.productId IN :productIds AND ci.deleted = false")
    List<CartItem> findByProductIdIn(@Param("productIds") List<String> productIds);

    /**
     * Find items by category
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.categoryId = :categoryId AND ci.deleted = false")
    List<CartItem> findByCategoryId(@Param("categoryId") String categoryId);

    // ==================== CART-SPECIFIC QUERIES ====================

    /**
     * Get cart item count
     */
    @Query("SELECT COUNT(ci) FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.deleted = false")
    long countByCartId(@Param("cartId") Long cartId);

    /**
     * Get total quantity in cart
     */
    @Query("SELECT COALESCE(SUM(ci.quantity), 0) FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.deleted = false")
    int getTotalQuantityByCartId(@Param("cartId") Long cartId);

    /**
     * Get cart subtotal
     */
    @Query("SELECT COALESCE(SUM(ci.totalPrice), 0) FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.deleted = false")
    BigDecimal getCartSubtotal(@Param("cartId") Long cartId);

    /**
     * Get cart total weight
     */
    @Query("SELECT COALESCE(SUM(ci.weight * ci.quantity), 0) FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.weight IS NOT NULL AND ci.deleted = false")
    BigDecimal getCartTotalWeight(@Param("cartId") Long cartId);

    /**
     * Check if cart contains product
     */
    @Query("SELECT CASE WHEN COUNT(ci) > 0 THEN true ELSE false END FROM CartItem ci WHERE ci.cart.id = :cartId AND ci.productId = :productId AND ci.deleted = false")
    boolean existsByCartIdAndProductId(@Param("cartId") Long cartId, @Param("productId") String productId);

    // ==================== BATCH OPERATIONS ====================

    /**
     * Batch update item quantities
     */
    @Modifying
    @Query("UPDATE CartItem ci SET ci.quantity = :quantity, ci.updatedAt = :currentTime WHERE ci.id IN :itemIds")
    int batchUpdateQuantities(@Param("itemIds") List<Long> itemIds,
                             @Param("quantity") Integer quantity,
                             @Param("currentTime") LocalDateTime currentTime);

    /**
     * Batch update item prices
     */
    @Modifying
    @Query("UPDATE CartItem ci SET ci.unitPrice = :unitPrice, ci.priceChanged = true, ci.lastPriceCheckAt = :currentTime, ci.updatedAt = :currentTime WHERE ci.id IN :itemIds")
    int batchUpdatePrices(@Param("itemIds") List<Long> itemIds,
                         @Param("unitPrice") BigDecimal unitPrice,
                         @Param("currentTime") LocalDateTime currentTime);

    /**
     * Batch soft delete items
     */
    @Modifying
    @Query("UPDATE CartItem ci SET ci.deleted = true, ci.deletedAt = :currentTime, ci.deletedBy = :deletedBy WHERE ci.id IN :itemIds")
    int batchSoftDelete(@Param("itemIds") List<Long> itemIds, 
                       @Param("currentTime") LocalDateTime currentTime, 
                       @Param("deletedBy") String deletedBy);

    /**
     * Batch delete items by cart ID
     */
    @Modifying
    @Query("UPDATE CartItem ci SET ci.deleted = true, ci.deletedAt = :currentTime, ci.deletedBy = :deletedBy WHERE ci.cart.id = :cartId")
    int batchSoftDeleteByCartId(@Param("cartId") Long cartId, 
                               @Param("currentTime") LocalDateTime currentTime, 
                               @Param("deletedBy") String deletedBy);

    /**
     * Batch update availability status
     */
    @Modifying
    @Query("UPDATE CartItem ci SET ci.availabilityStatus = :status, ci.updatedAt = :currentTime WHERE ci.productId IN :productIds")
    int batchUpdateAvailabilityStatus(@Param("productIds") List<String> productIds, 
                                     @Param("status") String status, 
                                     @Param("currentTime") LocalDateTime currentTime);

    // ==================== PRICE MANAGEMENT ====================

    /**
     * Find items with price changes
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.priceChanged = true AND ci.deleted = false")
    List<CartItem> findItemsWithPriceChanges();

    /**
     * Find items needing price check
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.lastPriceCheckAt < :cutoffTime AND ci.deleted = false")
    List<CartItem> findItemsNeedingPriceCheck(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Update item price
     */
    @Modifying
    @Query("UPDATE CartItem ci SET ci.unitPrice = :newPrice, " +
           "ci.priceChanged = CASE WHEN ci.unitPrice != :newPrice THEN true ELSE ci.priceChanged END, " +
           "ci.lastPriceCheckAt = :currentTime, ci.updatedAt = :currentTime WHERE ci.id = :itemId")
    int updateItemPrice(@Param("itemId") Long itemId,
                       @Param("newPrice") BigDecimal newPrice,
                       @Param("currentTime") LocalDateTime currentTime);

    /**
     * Reset price change flags
     */
    @Modifying
    @Query("UPDATE CartItem ci SET ci.priceChanged = false, ci.updatedAt = :currentTime WHERE ci.cart.id = :cartId")
    int resetPriceChangeFlags(@Param("cartId") Long cartId, @Param("currentTime") LocalDateTime currentTime);

    // ==================== ANALYTICS QUERIES ====================

    /**
     * Get popular products (most added to carts)
     */
    @Query("SELECT ci.productId, ci.productName, COUNT(ci), SUM(ci.quantity) " +
           "FROM CartItem ci WHERE ci.addedAt BETWEEN :startDate AND :endDate AND ci.deleted = false " +
           "GROUP BY ci.productId, ci.productName ORDER BY COUNT(ci) DESC")
    List<Object[]> getPopularProducts(@Param("startDate") LocalDateTime startDate, 
                                     @Param("endDate") LocalDateTime endDate, 
                                     Pageable pageable);

    /**
     * Get category performance
     */
    @Query("SELECT ci.categoryId, ci.categoryName, COUNT(ci), SUM(ci.quantity), AVG(ci.unitPrice), SUM(ci.totalPrice) " +
           "FROM CartItem ci WHERE ci.addedAt BETWEEN :startDate AND :endDate AND ci.deleted = false " +
           "GROUP BY ci.categoryId, ci.categoryName ORDER BY SUM(ci.totalPrice) DESC")
    List<Object[]> getCategoryPerformance(@Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate);

    /**
     * Get average cart item metrics
     */
    @Query("SELECT AVG(ci.quantity), AVG(ci.unitPrice), AVG(ci.totalPrice) " +
           "FROM CartItem ci WHERE ci.addedAt BETWEEN :startDate AND :endDate AND ci.deleted = false")
    Object[] getAverageCartItemMetrics(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);

    /**
     * Find frequently bought together products
     */
    @Query("SELECT ci1.productId, ci2.productId, COUNT(*) as frequency " +
           "FROM CartItem ci1 JOIN CartItem ci2 ON ci1.cart.id = ci2.cart.id " +
           "WHERE ci1.productId < ci2.productId AND ci1.deleted = false AND ci2.deleted = false " +
           "GROUP BY ci1.productId, ci2.productId " +
           "HAVING COUNT(*) >= :minFrequency " +
           "ORDER BY frequency DESC")
    List<Object[]> findFrequentlyBoughtTogether(@Param("minFrequency") long minFrequency, Pageable pageable);

    // ==================== INVENTORY IMPACT ====================

    /**
     * Get total demand for products
     */
    @Query("SELECT ci.productId, SUM(ci.quantity) " +
           "FROM CartItem ci WHERE ci.cart.status = 'ACTIVE' AND ci.deleted = false " +
           "GROUP BY ci.productId")
    List<Object[]> getProductDemand();

    /**
     * Find items exceeding stock
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.quantity > ci.stockQuantity AND ci.stockQuantity IS NOT NULL AND ci.deleted = false")
    List<CartItem> findItemsExceedingStock();

    /**
     * Find out of stock items
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.availabilityStatus = 'OUT_OF_STOCK' AND ci.deleted = false")
    List<CartItem> findOutOfStockItems();

    // ==================== GIFT & SPECIAL FEATURES ====================

    /**
     * Find gift items
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.isGift = true AND ci.deleted = false")
    List<CartItem> findGiftItems();

    /**
     * Find items with special instructions
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.specialInstructions IS NOT NULL AND ci.specialInstructions != '' AND ci.deleted = false")
    List<CartItem> findItemsWithSpecialInstructions();

    /**
     * Get gift wrap revenue
     */
    @Query("SELECT COALESCE(SUM(ci.giftWrapPrice), 0) FROM CartItem ci WHERE ci.isGift = true AND ci.giftWrapPrice IS NOT NULL AND ci.deleted = false")
    BigDecimal getGiftWrapRevenue();

    // ==================== CLEANUP & MAINTENANCE ====================

    /**
     * Find orphaned items (items without valid cart)
     */
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart IS NULL OR ci.cart.deleted = true")
    List<CartItem> findOrphanedItems();

    /**
     * Clean up old deleted items
     */
    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.deleted = true AND ci.deletedAt < :cutoffTime")
    int cleanupOldDeletedItems(@Param("cutoffTime") LocalDateTime cutoffTime);

    // ==================== SEARCH & FILTERING ====================

    /**
     * Search cart items by criteria
     */
    @Query("SELECT ci FROM CartItem ci WHERE " +
           "(:cartId IS NULL OR ci.cart.id = :cartId) AND " +
           "(:productId IS NULL OR ci.productId = :productId) AND " +
           "(:categoryId IS NULL OR ci.categoryId = :categoryId) AND " +
           "(:minPrice IS NULL OR ci.unitPrice >= :minPrice) AND " +
           "(:maxPrice IS NULL OR ci.unitPrice <= :maxPrice) AND " +
           "(:isGift IS NULL OR ci.isGift = :isGift) AND " +
           "ci.deleted = false " +
           "ORDER BY ci.addedAt DESC")
    Page<CartItem> searchCartItems(@Param("cartId") Long cartId,
                                  @Param("productId") String productId,
                                  @Param("categoryId") String categoryId,
                                  @Param("minPrice") BigDecimal minPrice,
                                  @Param("maxPrice") BigDecimal maxPrice,
                                  @Param("isGift") Boolean isGift,
                                  Pageable pageable);
}
