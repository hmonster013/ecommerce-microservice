package org.de013.orderservice.repository;

import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Order Repository
 * 
 * JPA repository for Order entity with custom queries and pagination support.
 * Provides comprehensive data access methods for order management.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    
    /**
     * Find order by order number
     * 
     * @param orderNumber the order number
     * @return optional order
     */
    Optional<Order> findByOrderNumber(String orderNumber);
    
    /**
     * Find orders by user ID
     * 
     * @param userId the user ID
     * @param pageable pagination information
     * @return page of orders
     */
    Page<Order> findByUserId(Long userId, Pageable pageable);
    
    /**
     * Find orders by user ID and status
     * 
     * @param userId the user ID
     * @param status the order status
     * @param pageable pagination information
     * @return page of orders
     */
    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);
    
    /**
     * Find orders by status
     * 
     * @param status the order status
     * @param pageable pagination information
     * @return page of orders
     */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    
    /**
     * Find orders by order type
     * 
     * @param orderType the order type
     * @param pageable pagination information
     * @return page of orders
     */
    Page<Order> findByOrderType(OrderType orderType, Pageable pageable);
    
    /**
     * Find orders created between dates
     * 
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination information
     * @return page of orders
     */
    Page<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find orders by user and date range
     * 
     * @param userId the user ID
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination information
     * @return page of orders
     */
    Page<Order> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find orders by status and date range
     * 
     * @param status the order status
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination information
     * @return page of orders
     */
    Page<Order> findByStatusAndCreatedAtBetween(OrderStatus status, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find orders by multiple statuses
     * 
     * @param statuses list of order statuses
     * @param pageable pagination information
     * @return page of orders
     */
    Page<Order> findByStatusIn(List<OrderStatus> statuses, Pageable pageable);
    
    /**
     * Find orders by user and multiple statuses
     * 
     * @param userId the user ID
     * @param statuses list of order statuses
     * @param pageable pagination information
     * @return page of orders
     */
    Page<Order> findByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses, Pageable pageable);
    
    /**
     * Find orders with expected delivery date before given date
     * 
     * @param date the date to compare
     * @param pageable pagination information
     * @return page of overdue orders
     */
    Page<Order> findByExpectedDeliveryDateBeforeAndActualDeliveryDateIsNull(LocalDateTime date, Pageable pageable);
    
    /**
     * Find orders by priority level
     * 
     * @param priorityLevel the priority level
     * @param pageable pagination information
     * @return page of orders
     */
    Page<Order> findByPriorityLevel(Integer priorityLevel, Pageable pageable);
    
    /**
     * Find gift orders
     * 
     * @param pageable pagination information
     * @return page of gift orders
     */
    Page<Order> findByIsGiftTrue(Pageable pageable);
    
    /**
     * Find orders requiring special handling
     * 
     * @param pageable pagination information
     * @return page of orders requiring special handling
     */
    Page<Order> findByRequiresSpecialHandlingTrue(Pageable pageable);
    
    /**
     * Count orders by user ID
     * 
     * @param userId the user ID
     * @return count of orders
     */
    long countByUserId(Long userId);
    
    /**
     * Count orders by status
     * 
     * @param status the order status
     * @return count of orders
     */
    long countByStatus(OrderStatus status);
    
    /**
     * Count orders by user and status
     * 
     * @param userId the user ID
     * @param status the order status
     * @return count of orders
     */
    long countByUserIdAndStatus(Long userId, OrderStatus status);
    
    /**
     * Count orders created today
     * 
     * @param startOfDay start of today
     * @param endOfDay end of today
     * @return count of orders created today
     */
    long countByCreatedAtBetween(LocalDateTime startOfDay, LocalDateTime endOfDay);
    
    /**
     * Check if order number exists
     * 
     * @param orderNumber the order number
     * @return true if exists
     */
    boolean existsByOrderNumber(String orderNumber);
    
    /**
     * Find orders with total amount greater than specified amount
     * 
     * @param amount the minimum amount
     * @param currency the currency
     * @param pageable pagination information
     * @return page of orders
     */
    @Query("SELECT o FROM Order o WHERE o.totalAmount.amount > :amount AND o.totalAmount.currency = :currency")
    Page<Order> findOrdersWithTotalAmountGreaterThan(@Param("amount") java.math.BigDecimal amount, 
                                                     @Param("currency") String currency, 
                                                     Pageable pageable);
    
    /**
     * Find orders by shipping country
     * 
     * @param country the shipping country
     * @param pageable pagination information
     * @return page of orders
     */
    @Query("SELECT o FROM Order o WHERE o.shippingAddress.country = :country")
    Page<Order> findByShippingCountry(@Param("country") String country, Pageable pageable);
    
    /**
     * Find orders by shipping city
     * 
     * @param city the shipping city
     * @param pageable pagination information
     * @return page of orders
     */
    @Query("SELECT o FROM Order o WHERE o.shippingAddress.city = :city")
    Page<Order> findByShippingCity(@Param("city") String city, Pageable pageable);
    
    /**
     * Find recent orders for user
     * 
     * @param userId the user ID
     * @param hours number of hours to look back
     * @param pageable pagination information
     * @return page of recent orders
     */
    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.createdAt >= :since")
    Page<Order> findRecentOrdersForUser(@Param("userId") Long userId, 
                                       @Param("since") LocalDateTime since, 
                                       Pageable pageable);
    

    
    /**
     * Find orders by customer email
     * 
     * @param email the customer email
     * @param pageable pagination information
     * @return page of orders
     */
    @Query("SELECT o FROM Order o WHERE o.shippingAddress.email = :email OR o.billingAddress.email = :email")
    Page<Order> findByCustomerEmail(@Param("email") String email, Pageable pageable);
    
    /**
     * Find orders by customer phone
     * 
     * @param phone the customer phone
     * @param pageable pagination information
     * @return page of orders
     */
    @Query("SELECT o FROM Order o WHERE o.shippingAddress.phone = :phone OR o.billingAddress.phone = :phone")
    Page<Order> findByCustomerPhone(@Param("phone") String phone, Pageable pageable);
    
    /**
     * Get order statistics for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return order statistics
     */
    @Query("SELECT new map(" +
           "COUNT(o) as totalOrders, " +
           "SUM(o.totalAmount.amount) as totalRevenue, " +
           "AVG(o.totalAmount.amount) as averageOrderValue, " +
           "COUNT(DISTINCT o.userId) as uniqueCustomers) " +
           "FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate")
    java.util.Map<String, Object> getOrderStatistics(@Param("startDate") LocalDateTime startDate, 
                                                     @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get order count by status for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return order count by status
     */
    @Query("SELECT o.status, COUNT(o) FROM Order o " +
           "WHERE o.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY o.status")
    List<Object[]> getOrderCountByStatus(@Param("startDate") LocalDateTime startDate, 
                                        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get daily order counts for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return daily order counts
     */
    @Query("SELECT DATE(o.createdAt), COUNT(o) FROM Order o " +
           "WHERE o.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(o.createdAt) " +
           "ORDER BY DATE(o.createdAt)")
    List<Object[]> getDailyOrderCounts(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);
    
    /**
     * Update order status
     * 
     * @param orderId the order ID
     * @param status the new status
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE Order o SET o.status = :status, o.updatedAt = :updatedAt WHERE o.id = :orderId")
    int updateOrderStatus(@Param("orderId") Long orderId, 
                         @Param("status") OrderStatus status, 
                         @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Update order expected delivery date
     * 
     * @param orderId the order ID
     * @param expectedDeliveryDate the new expected delivery date
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE Order o SET o.expectedDeliveryDate = :expectedDeliveryDate, o.updatedAt = :updatedAt WHERE o.id = :orderId")
    int updateExpectedDeliveryDate(@Param("orderId") Long orderId, 
                                  @Param("expectedDeliveryDate") LocalDateTime expectedDeliveryDate, 
                                  @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Mark order as delivered
     * 
     * @param orderId the order ID
     * @param deliveryDate the delivery date
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE Order o SET o.status = 'DELIVERED', o.actualDeliveryDate = :deliveryDate, o.updatedAt = :updatedAt WHERE o.id = :orderId")
    int markOrderAsDelivered(@Param("orderId") Long orderId, 
                            @Param("deliveryDate") LocalDateTime deliveryDate, 
                            @Param("updatedAt") LocalDateTime updatedAt);
    

    
    /**
     * Find top customers by order count
     * 
     * @param startDate start date
     * @param endDate end date
     * @param limit number of top customers
     * @return list of customer data
     */
    @Query("SELECT o.userId, COUNT(o) as orderCount, SUM(o.totalAmount.amount) as totalSpent " +
           "FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY o.userId " +
           "ORDER BY COUNT(o) DESC")
    List<Object[]> findTopCustomersByOrderCount(@Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate, 
                                               Pageable pageable);
    
    /**
     * Find top customers by revenue
     * 
     * @param startDate start date
     * @param endDate end date
     * @param limit number of top customers
     * @return list of customer data
     */
    @Query("SELECT o.userId, SUM(o.totalAmount.amount) as totalSpent, COUNT(o) as orderCount " +
           "FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY o.userId " +
           "ORDER BY SUM(o.totalAmount.amount) DESC")
    List<Object[]> findTopCustomersByRevenue(@Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate, 
                                           Pageable pageable);
}
