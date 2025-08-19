package org.de013.orderservice.repository;

import org.de013.orderservice.entity.OrderItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Order Item Repository
 * 
 * JPA repository for OrderItem entity with batch operations and product analytics.
 * Provides comprehensive data access methods for order item management.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long>, JpaSpecificationExecutor<OrderItem> {
    
    /**
     * Find order items by order ID
     * 
     * @param orderId the order ID
     * @return list of order items
     */
    List<OrderItem> findByOrderId(Long orderId);
    
    /**
     * Find order items by order ID with pagination
     * 
     * @param orderId the order ID
     * @param pageable pagination information
     * @return page of order items
     */
    Page<OrderItem> findByOrderId(Long orderId, Pageable pageable);
    
    /**
     * Find order items by product ID
     * 
     * @param productId the product ID
     * @param pageable pagination information
     * @return page of order items
     */
    Page<OrderItem> findByProductId(Long productId, Pageable pageable);
    
    /**
     * Find order items by SKU
     * 
     * @param sku the product SKU
     * @param pageable pagination information
     * @return page of order items
     */
    Page<OrderItem> findBySku(String sku, Pageable pageable);
    
    /**
     * Find order items by product category
     * 
     * @param category the product category
     * @param pageable pagination information
     * @return page of order items
     */
    Page<OrderItem> findByProductCategory(String category, Pageable pageable);
    
    /**
     * Find order items by product brand
     * 
     * @param brand the product brand
     * @param pageable pagination information
     * @return page of order items
     */
    Page<OrderItem> findByProductBrand(String brand, Pageable pageable);
    
    /**
     * Find gift items
     * 
     * @param pageable pagination information
     * @return page of gift items
     */
    Page<OrderItem> findByIsGiftTrue(Pageable pageable);
    
    /**
     * Find items requiring special handling
     * 
     * @param pageable pagination information
     * @return page of items requiring special handling
     */
    Page<OrderItem> findByRequiresSpecialHandlingTrue(Pageable pageable);
    
    /**
     * Find fragile items
     * 
     * @param pageable pagination information
     * @return page of fragile items
     */
    Page<OrderItem> findByIsFragileTrue(Pageable pageable);
    
    /**
     * Find hazardous items
     * 
     * @param pageable pagination information
     * @return page of hazardous items
     */
    Page<OrderItem> findByIsHazardousTrue(Pageable pageable);
    
    /**
     * Find order items by status
     * 
     * @param status the item status
     * @param pageable pagination information
     * @return page of order items
     */
    Page<OrderItem> findByStatus(String status, Pageable pageable);
    
    /**
     * Find order items with expected delivery date before given date
     * 
     * @param date the date to compare
     * @param pageable pagination information
     * @return page of overdue items
     */
    Page<OrderItem> findByExpectedDeliveryDateBeforeAndActualDeliveryDateIsNull(LocalDateTime date, Pageable pageable);
    
    /**
     * Count order items by order ID
     * 
     * @param orderId the order ID
     * @return count of order items
     */
    long countByOrderId(Long orderId);
    
    /**
     * Count order items by product ID
     * 
     * @param productId the product ID
     * @return count of order items
     */
    long countByProductId(Long productId);
    
    /**
     * Sum quantity by product ID
     * 
     * @param productId the product ID
     * @return total quantity ordered
     */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi WHERE oi.productId = :productId")
    Long sumQuantityByProductId(@Param("productId") Long productId);
    
    /**
     * Sum quantity by product ID for date range
     * 
     * @param productId the product ID
     * @param startDate start date
     * @param endDate end date
     * @return total quantity ordered in date range
     */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
           "WHERE oi.productId = :productId AND oi.createdAt BETWEEN :startDate AND :endDate")
    Long sumQuantityByProductIdAndDateRange(@Param("productId") Long productId, 
                                           @Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find top selling products by quantity
     * 
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination information
     * @return list of product sales data
     */
    @Query("SELECT oi.productId, oi.productName, oi.sku, SUM(oi.quantity) as totalQuantity, " +
           "COUNT(DISTINCT oi.order.id) as orderCount, SUM(oi.totalPrice.amount) as totalRevenue " +
           "FROM OrderItem oi WHERE oi.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY oi.productId, oi.productName, oi.sku " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findTopSellingProductsByQuantity(@Param("startDate") LocalDateTime startDate, 
                                                   @Param("endDate") LocalDateTime endDate, 
                                                   Pageable pageable);
    
    /**
     * Find top selling products by revenue
     * 
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination information
     * @return list of product revenue data
     */
    @Query("SELECT oi.productId, oi.productName, oi.sku, SUM(oi.totalPrice.amount) as totalRevenue, " +
           "SUM(oi.quantity) as totalQuantity, COUNT(DISTINCT oi.order.id) as orderCount " +
           "FROM OrderItem oi WHERE oi.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY oi.productId, oi.productName, oi.sku " +
           "ORDER BY SUM(oi.totalPrice.amount) DESC")
    List<Object[]> findTopSellingProductsByRevenue(@Param("startDate") LocalDateTime startDate, 
                                                  @Param("endDate") LocalDateTime endDate, 
                                                  Pageable pageable);
    
    /**
     * Find product category performance
     * 
     * @param startDate start date
     * @param endDate end date
     * @return list of category performance data
     */
    @Query("SELECT oi.productCategory, COUNT(DISTINCT oi.order.id) as orderCount, " +
           "SUM(oi.quantity) as totalQuantity, SUM(oi.totalPrice.amount) as totalRevenue, " +
           "AVG(oi.totalPrice.amount) as averageOrderValue " +
           "FROM OrderItem oi WHERE oi.createdAt BETWEEN :startDate AND :endDate " +
           "AND oi.productCategory IS NOT NULL " +
           "GROUP BY oi.productCategory " +
           "ORDER BY SUM(oi.totalPrice.amount) DESC")
    List<Object[]> findCategoryPerformance(@Param("startDate") LocalDateTime startDate, 
                                          @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find product brand performance
     * 
     * @param startDate start date
     * @param endDate end date
     * @return list of brand performance data
     */
    @Query("SELECT oi.productBrand, COUNT(DISTINCT oi.order.id) as orderCount, " +
           "SUM(oi.quantity) as totalQuantity, SUM(oi.totalPrice.amount) as totalRevenue, " +
           "AVG(oi.totalPrice.amount) as averageOrderValue " +
           "FROM OrderItem oi WHERE oi.createdAt BETWEEN :startDate AND :endDate " +
           "AND oi.productBrand IS NOT NULL " +
           "GROUP BY oi.productBrand " +
           "ORDER BY SUM(oi.totalPrice.amount) DESC")
    List<Object[]> findBrandPerformance(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find items with high discount percentage
     * 
     * @param minDiscountPercentage minimum discount percentage
     * @param pageable pagination information
     * @return page of discounted items
     */
    @Query("SELECT oi FROM OrderItem oi WHERE " +
           "oi.discountAmount.amount > 0 AND " +
           "(oi.discountAmount.amount / oi.totalPrice.amount * 100) >= :minDiscountPercentage")
    Page<OrderItem> findItemsWithHighDiscount(@Param("minDiscountPercentage") BigDecimal minDiscountPercentage, 
                                             Pageable pageable);
    
    /**
     * Find items by price range
     * 
     * @param minPrice minimum unit price
     * @param maxPrice maximum unit price
     * @param currency currency code
     * @param pageable pagination information
     * @return page of items in price range
     */
    @Query("SELECT oi FROM OrderItem oi WHERE " +
           "oi.unitPrice.amount BETWEEN :minPrice AND :maxPrice AND " +
           "oi.unitPrice.currency = :currency")
    Page<OrderItem> findItemsByPriceRange(@Param("minPrice") BigDecimal minPrice, 
                                         @Param("maxPrice") BigDecimal maxPrice, 
                                         @Param("currency") String currency, 
                                         Pageable pageable);
    
    /**
     * Find items by weight range
     * 
     * @param minWeight minimum weight
     * @param maxWeight maximum weight
     * @param pageable pagination information
     * @return page of items in weight range
     */
    @Query("SELECT oi FROM OrderItem oi WHERE oi.weight BETWEEN :minWeight AND :maxWeight")
    Page<OrderItem> findItemsByWeightRange(@Param("minWeight") BigDecimal minWeight, 
                                          @Param("maxWeight") BigDecimal maxWeight, 
                                          Pageable pageable);
    
    /**
     * Get total weight by order ID
     * 
     * @param orderId the order ID
     * @return total weight of all items in order
     */
    @Query("SELECT COALESCE(SUM(oi.weight * oi.quantity), 0) FROM OrderItem oi WHERE oi.order.id = :orderId")
    BigDecimal getTotalWeightByOrderId(@Param("orderId") Long orderId);
    
    /**
     * Get item statistics for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return item statistics
     */
    @Query("SELECT new map(" +
           "COUNT(oi) as totalItems, " +
           "SUM(oi.quantity) as totalQuantity, " +
           "COUNT(DISTINCT oi.productId) as uniqueProducts, " +
           "SUM(oi.totalPrice.amount) as totalRevenue, " +
           "AVG(oi.unitPrice.amount) as averageUnitPrice) " +
           "FROM OrderItem oi WHERE oi.createdAt BETWEEN :startDate AND :endDate")
    java.util.Map<String, Object> getItemStatistics(@Param("startDate") LocalDateTime startDate, 
                                                    @Param("endDate") LocalDateTime endDate);
    
    /**
     * Update item status
     * 
     * @param itemId the item ID
     * @param status the new status
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderItem oi SET oi.status = :status, oi.updatedAt = :updatedAt WHERE oi.id = :itemId")
    int updateItemStatus(@Param("itemId") Long itemId, 
                        @Param("status") String status, 
                        @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Update item expected delivery date
     * 
     * @param itemId the item ID
     * @param expectedDeliveryDate the new expected delivery date
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderItem oi SET oi.expectedDeliveryDate = :expectedDeliveryDate, oi.updatedAt = :updatedAt WHERE oi.id = :itemId")
    int updateExpectedDeliveryDate(@Param("itemId") Long itemId, 
                                  @Param("expectedDeliveryDate") LocalDateTime expectedDeliveryDate, 
                                  @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Mark item as delivered
     * 
     * @param itemId the item ID
     * @param deliveryDate the delivery date
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderItem oi SET oi.status = 'DELIVERED', oi.actualDeliveryDate = :deliveryDate, oi.updatedAt = :updatedAt WHERE oi.id = :itemId")
    int markItemAsDelivered(@Param("itemId") Long itemId, 
                           @Param("deliveryDate") LocalDateTime deliveryDate, 
                           @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Batch update item quantities
     * 
     * @param itemIds list of item IDs
     * @param quantities list of new quantities (same order as itemIds)
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderItem oi SET oi.quantity = " +
           "CASE " +
           "WHEN oi.id = :itemId1 THEN :quantity1 " +
           "WHEN oi.id = :itemId2 THEN :quantity2 " +
           "WHEN oi.id = :itemId3 THEN :quantity3 " +
           "WHEN oi.id = :itemId4 THEN :quantity4 " +
           "WHEN oi.id = :itemId5 THEN :quantity5 " +
           "END, " +
           "oi.totalPrice.amount = oi.unitPrice.amount * " +
           "CASE " +
           "WHEN oi.id = :itemId1 THEN :quantity1 " +
           "WHEN oi.id = :itemId2 THEN :quantity2 " +
           "WHEN oi.id = :itemId3 THEN :quantity3 " +
           "WHEN oi.id = :itemId4 THEN :quantity4 " +
           "WHEN oi.id = :itemId5 THEN :quantity5 " +
           "END, " +
           "oi.updatedAt = :updatedAt " +
           "WHERE oi.id IN (:itemId1, :itemId2, :itemId3, :itemId4, :itemId5)")
    int batchUpdateQuantities(@Param("itemId1") Long itemId1, @Param("quantity1") Integer quantity1,
                             @Param("itemId2") Long itemId2, @Param("quantity2") Integer quantity2,
                             @Param("itemId3") Long itemId3, @Param("quantity3") Integer quantity3,
                             @Param("itemId4") Long itemId4, @Param("quantity4") Integer quantity4,
                             @Param("itemId5") Long itemId5, @Param("quantity5") Integer quantity5,
                             @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Delete items by order ID (soft delete)
     * 
     * @param orderId the order ID
     * @param deletedAt the deletion timestamp
     * @return number of deleted records
     */
    @Modifying
    @Query("UPDATE OrderItem oi SET oi.deletedAt = :deletedAt WHERE oi.order.id = :orderId")
    int softDeleteByOrderId(@Param("orderId") Long orderId, @Param("deletedAt") LocalDateTime deletedAt);
    
    /**
     * Find items needing inventory update
     * 
     * @param statuses list of order statuses that require inventory update
     * @return list of items needing inventory update
     */
    @Query("SELECT oi FROM OrderItem oi JOIN oi.order o " +
           "WHERE o.status IN :statuses AND oi.status = 'PENDING'")
    List<OrderItem> findItemsNeedingInventoryUpdate(@Param("statuses") List<String> statuses);
    
    /**
     * Find recently added items for product
     * 
     * @param productId the product ID
     * @param hours number of hours to look back
     * @return list of recent items
     */
    @Query("SELECT oi FROM OrderItem oi WHERE oi.productId = :productId AND oi.createdAt >= :since")
    List<OrderItem> findRecentItemsForProduct(@Param("productId") Long productId, 
                                             @Param("since") LocalDateTime since);
}
