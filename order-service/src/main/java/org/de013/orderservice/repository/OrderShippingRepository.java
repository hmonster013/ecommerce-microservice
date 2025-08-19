package org.de013.orderservice.repository;

import org.de013.orderservice.entity.OrderShipping;
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
 * Order Shipping Repository
 * 
 * JPA repository for OrderShipping entity with shipping details and analytics.
 * Provides comprehensive data access methods for shipping management.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Repository
public interface OrderShippingRepository extends JpaRepository<OrderShipping, Long>, JpaSpecificationExecutor<OrderShipping> {
    
    /**
     * Find shipping by order ID
     * 
     * @param orderId the order ID
     * @return optional shipping record
     */
    Optional<OrderShipping> findByOrderId(Long orderId);
    
    /**
     * Find shipping records by tracking number
     * 
     * @param trackingNumber the tracking number
     * @return optional shipping record
     */
    Optional<OrderShipping> findByTrackingNumber(String trackingNumber);
    
    /**
     * Find shipping records by carrier
     * 
     * @param carrier the carrier name
     * @param pageable pagination information
     * @return page of shipping records
     */
    Page<OrderShipping> findByCarrier(String carrier, Pageable pageable);
    
    /**
     * Find shipping records by shipping method
     * 
     * @param shippingMethod the shipping method
     * @param pageable pagination information
     * @return page of shipping records
     */
    Page<OrderShipping> findByShippingMethod(String shippingMethod, Pageable pageable);
    
    /**
     * Find shipping records by carrier service
     * 
     * @param carrierService the carrier service
     * @param pageable pagination information
     * @return page of shipping records
     */
    Page<OrderShipping> findByCarrierService(String carrierService, Pageable pageable);
    
    /**
     * Find shipping records by shipping status
     * 
     * @param shippingStatus the shipping status
     * @param pageable pagination information
     * @return page of shipping records
     */
    Page<OrderShipping> findByShippingStatus(String shippingStatus, Pageable pageable);
    
    /**
     * Find shipped orders
     * 
     * @param pageable pagination information
     * @return page of shipped orders
     */
    Page<OrderShipping> findByShippedAtIsNotNull(Pageable pageable);
    
    /**
     * Find delivered orders
     * 
     * @param pageable pagination information
     * @return page of delivered orders
     */
    Page<OrderShipping> findByActualDeliveryDateIsNotNull(Pageable pageable);
    
    /**
     * Find orders with created shipping labels
     * 
     * @param pageable pagination information
     * @return page of orders with shipping labels
     */
    Page<OrderShipping> findByShippingLabelUrlIsNotNull(Pageable pageable);
    
    /**
     * Find international shipments
     * 
     * @param pageable pagination information
     * @return page of international shipments
     */
    Page<OrderShipping> findByIsInternationalTrue(Pageable pageable);
    
    /**
     * Find domestic shipments
     * 
     * @param pageable pagination information
     * @return page of domestic shipments
     */
    Page<OrderShipping> findByIsInternationalFalse(Pageable pageable);
    
    /**
     * Find insured shipments
     * 
     * @param pageable pagination information
     * @return page of insured shipments
     */
    Page<OrderShipping> findByIsInsuredTrue(Pageable pageable);
    
    /**
     * Find shipments requiring signature
     * 
     * @param pageable pagination information
     * @return page of shipments requiring signature
     */
    Page<OrderShipping> findBySignatureRequiredTrue(Pageable pageable);
    
    /**
     * Find shipments requiring adult signature
     * 
     * @param pageable pagination information
     * @return page of shipments requiring adult signature
     */
    Page<OrderShipping> findByAdultSignatureRequiredTrue(Pageable pageable);
    
    /**
     * Find shipments by shipping country
     * 
     * @param country the shipping country
     * @param pageable pagination information
     * @return page of shipments
     */
    @Query("SELECT os FROM OrderShipping os WHERE os.shippingAddress.country = :country")
    Page<OrderShipping> findByShippingCountry(@Param("country") String country, Pageable pageable);
    
    /**
     * Find shipments by shipping city
     * 
     * @param city the shipping city
     * @param pageable pagination information
     * @return page of shipments
     */
    @Query("SELECT os FROM OrderShipping os WHERE os.shippingAddress.city = :city")
    Page<OrderShipping> findByShippingCity(@Param("city") String city, Pageable pageable);
    
    /**
     * Find shipments by shipping state
     * 
     * @param state the shipping state
     * @param pageable pagination information
     * @return page of shipments
     */
    @Query("SELECT os FROM OrderShipping os WHERE os.shippingAddress.state = :state")
    Page<OrderShipping> findByShippingState(@Param("state") String state, Pageable pageable);
    
    /**
     * Find shipments with estimated delivery date before given date
     * 
     * @param date the date to compare
     * @param pageable pagination information
     * @return page of overdue shipments
     */
    Page<OrderShipping> findByEstimatedDeliveryDateBeforeAndActualDeliveryDateIsNull(LocalDateTime date, Pageable pageable);
    
    /**
     * Find shipments shipped between dates
     * 
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination information
     * @return page of shipments
     */
    Page<OrderShipping> findByShippedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find shipments delivered between dates
     * 
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination information
     * @return page of shipments
     */
    Page<OrderShipping> findByActualDeliveryDateBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find shipments by weight range
     * 
     * @param minWeight minimum weight
     * @param maxWeight maximum weight
     * @param pageable pagination information
     * @return page of shipments
     */
    Page<OrderShipping> findByTotalWeightBetween(BigDecimal minWeight, BigDecimal maxWeight, Pageable pageable);
    
    /**
     * Find shipments with shipping cost greater than amount
     * 
     * @param amount the minimum amount
     * @param currency the currency
     * @param pageable pagination information
     * @return page of shipments
     */
    @Query("SELECT os FROM OrderShipping os WHERE os.shippingCost.amount > :amount AND os.shippingCost.currency = :currency")
    Page<OrderShipping> findByShippingCostGreaterThan(@Param("amount") BigDecimal amount, 
                                                      @Param("currency") String currency, 
                                                      Pageable pageable);
    
    /**
     * Find shipments with multiple packages
     * 
     * @param minPackages minimum number of packages
     * @param pageable pagination information
     * @return page of shipments with multiple packages
     */
    Page<OrderShipping> findByPackageCountGreaterThan(Integer minPackages, Pageable pageable);
    
    /**
     * Count shipments by carrier
     * 
     * @param carrier the carrier name
     * @return count of shipments
     */
    long countByCarrier(String carrier);
    
    /**
     * Count shipments by shipping method
     * 
     * @param shippingMethod the shipping method
     * @return count of shipments
     */
    long countByShippingMethod(String shippingMethod);
    
    /**
     * Count shipments by shipping status
     * 
     * @param shippingStatus the shipping status
     * @return count of shipments
     */
    long countByShippingStatus(String shippingStatus);
    
    /**
     * Count international shipments
     * 
     * @return count of international shipments
     */
    long countByIsInternationalTrue();
    
    /**
     * Count domestic shipments
     * 
     * @return count of domestic shipments
     */
    long countByIsInternationalFalse();
    
    /**
     * Check if tracking number exists
     * 
     * @param trackingNumber the tracking number
     * @return true if exists
     */
    boolean existsByTrackingNumber(String trackingNumber);
    
    /**
     * Find shipments ready for pickup
     * 
     * @param pageable pagination information
     * @return page of shipments ready for pickup
     */
    @Query("SELECT os FROM OrderShipping os WHERE os.shippingStatus = 'READY_FOR_PICKUP'")
    Page<OrderShipping> findShipmentsReadyForPickup(Pageable pageable);
    
    /**
     * Find shipments in transit
     * 
     * @param pageable pagination information
     * @return page of shipments in transit
     */
    @Query("SELECT os FROM OrderShipping os WHERE os.shippingStatus IN ('SHIPPED', 'IN_TRANSIT')")
    Page<OrderShipping> findShipmentsInTransit(Pageable pageable);
    
    /**
     * Find shipments out for delivery
     * 
     * @param pageable pagination information
     * @return page of shipments out for delivery
     */
    @Query("SELECT os FROM OrderShipping os WHERE os.shippingStatus = 'OUT_FOR_DELIVERY'")
    Page<OrderShipping> findShipmentsOutForDelivery(Pageable pageable);
    
    /**
     * Find express shipments
     * 
     * @param pageable pagination information
     * @return page of express shipments
     */
    @Query("SELECT os FROM OrderShipping os WHERE os.shippingMethod LIKE '%EXPRESS%' OR os.shippingMethod LIKE '%OVERNIGHT%'")
    Page<OrderShipping> findExpressShipments(Pageable pageable);
    
    /**
     * Find shipments with special instructions
     * 
     * @param pageable pagination information
     * @return page of shipments with special instructions
     */
    Page<OrderShipping> findBySpecialInstructionsIsNotNull(Pageable pageable);
    
    /**
     * Find shipments with return tracking
     * 
     * @param pageable pagination information
     * @return page of shipments with return tracking
     */
    Page<OrderShipping> findByReturnTrackingNumberIsNotNull(Pageable pageable);
    
    /**
     * Get shipping statistics for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return shipping statistics
     */
    @Query("SELECT new map(" +
           "COUNT(os) as totalShipments, " +
           "SUM(os.shippingCost.amount) as totalShippingCost, " +
           "AVG(os.shippingCost.amount) as averageShippingCost, " +
           "SUM(os.totalWeight) as totalWeight, " +
           "AVG(os.totalWeight) as averageWeight, " +
           "COUNT(DISTINCT os.carrier) as uniqueCarriers) " +
           "FROM OrderShipping os WHERE os.createdAt BETWEEN :startDate AND :endDate")
    java.util.Map<String, Object> getShippingStatistics(@Param("startDate") LocalDateTime startDate, 
                                                        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get shipping count by carrier for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return shipping count by carrier
     */
    @Query("SELECT os.carrier, COUNT(os), SUM(os.shippingCost.amount) FROM OrderShipping os " +
           "WHERE os.createdAt BETWEEN :startDate AND :endDate " +
           "AND os.carrier IS NOT NULL " +
           "GROUP BY os.carrier " +
           "ORDER BY COUNT(os) DESC")
    List<Object[]> getShippingCountByCarrier(@Param("startDate") LocalDateTime startDate, 
                                            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get shipping count by method for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return shipping count by method
     */
    @Query("SELECT os.shippingMethod, COUNT(os), SUM(os.shippingCost.amount) FROM OrderShipping os " +
           "WHERE os.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY os.shippingMethod " +
           "ORDER BY COUNT(os) DESC")
    List<Object[]> getShippingCountByMethod(@Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get shipping count by country for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return shipping count by country
     */
    @Query("SELECT os.shippingAddress.country, COUNT(os), SUM(os.shippingCost.amount) FROM OrderShipping os " +
           "WHERE os.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY os.shippingAddress.country " +
           "ORDER BY COUNT(os) DESC")
    List<Object[]> getShippingCountByCountry(@Param("startDate") LocalDateTime startDate, 
                                            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get delivery time data by carrier for calculation
     *
     * @param startDate start date
     * @param endDate end date
     * @return delivery time data by carrier
     */
    @Query("SELECT os.carrier, os.shippedAt, os.actualDeliveryDate " +
           "FROM OrderShipping os " +
           "WHERE os.createdAt BETWEEN :startDate AND :endDate " +
           "AND os.shippedAt IS NOT NULL AND os.actualDeliveryDate IS NOT NULL " +
           "AND os.carrier IS NOT NULL " +
           "ORDER BY os.carrier")
    List<Object[]> getDeliveryTimeDataByCarrier(@Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get delivery time data by shipping method for calculation
     *
     * @param startDate start date
     * @param endDate end date
     * @return delivery time data by shipping method
     */
    @Query("SELECT os.shippingMethod, os.shippedAt, os.actualDeliveryDate " +
           "FROM OrderShipping os " +
           "WHERE os.createdAt BETWEEN :startDate AND :endDate " +
           "AND os.shippedAt IS NOT NULL AND os.actualDeliveryDate IS NOT NULL " +
           "ORDER BY os.shippingMethod")
    List<Object[]> getDeliveryTimeDataByMethod(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get on-time delivery rate by carrier
     * 
     * @param startDate start date
     * @param endDate end date
     * @return on-time delivery rate by carrier
     */
    @Query("SELECT os.carrier, " +
           "COUNT(os) as totalDeliveries, " +
           "SUM(CASE WHEN os.actualDeliveryDate <= os.estimatedDeliveryDate THEN 1 ELSE 0 END) as onTimeDeliveries, " +
           "(SUM(CASE WHEN os.actualDeliveryDate <= os.estimatedDeliveryDate THEN 1 ELSE 0 END) * 100.0 / COUNT(os)) as onTimeRate " +
           "FROM OrderShipping os " +
           "WHERE os.createdAt BETWEEN :startDate AND :endDate " +
           "AND os.actualDeliveryDate IS NOT NULL AND os.estimatedDeliveryDate IS NOT NULL " +
           "AND os.carrier IS NOT NULL " +
           "GROUP BY os.carrier " +
           "ORDER BY onTimeRate DESC")
    List<Object[]> getOnTimeDeliveryRateByCarrier(@Param("startDate") LocalDateTime startDate, 
                                                  @Param("endDate") LocalDateTime endDate);
    
    /**
     * Update shipping status
     * 
     * @param shippingId the shipping ID
     * @param status the new status
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderShipping os SET os.shippingStatus = :status, os.updatedAt = :updatedAt WHERE os.id = :shippingId")
    int updateShippingStatus(@Param("shippingId") Long shippingId, 
                            @Param("status") String status, 
                            @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Update tracking number
     * 
     * @param shippingId the shipping ID
     * @param trackingNumber the tracking number
     * @param shippedAt the shipped timestamp
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderShipping os SET os.trackingNumber = :trackingNumber, os.shippedAt = :shippedAt, " +
           "os.shippingStatus = 'SHIPPED', os.updatedAt = :updatedAt WHERE os.id = :shippingId")
    int updateTrackingNumber(@Param("shippingId") Long shippingId, 
                            @Param("trackingNumber") String trackingNumber, 
                            @Param("shippedAt") LocalDateTime shippedAt, 
                            @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Update estimated delivery date
     * 
     * @param shippingId the shipping ID
     * @param estimatedDeliveryDate the estimated delivery date
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderShipping os SET os.estimatedDeliveryDate = :estimatedDeliveryDate, os.updatedAt = :updatedAt WHERE os.id = :shippingId")
    int updateEstimatedDeliveryDate(@Param("shippingId") Long shippingId, 
                                   @Param("estimatedDeliveryDate") LocalDateTime estimatedDeliveryDate, 
                                   @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Mark as delivered
     * 
     * @param shippingId the shipping ID
     * @param deliveryDate the delivery date
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderShipping os SET os.actualDeliveryDate = :deliveryDate, os.shippingStatus = 'DELIVERED', " +
           "os.updatedAt = :updatedAt WHERE os.id = :shippingId")
    int markAsDelivered(@Param("shippingId") Long shippingId, 
                       @Param("deliveryDate") LocalDateTime deliveryDate, 
                       @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Update shipping label URL
     * 
     * @param shippingId the shipping ID
     * @param labelUrl the shipping label URL
     * @param labelCreatedAt the label creation timestamp
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderShipping os SET os.shippingLabelUrl = :labelUrl, os.labelCreatedAt = :labelCreatedAt, " +
           "os.shippingStatus = 'LABEL_CREATED', os.updatedAt = :updatedAt WHERE os.id = :shippingId")
    int updateShippingLabel(@Param("shippingId") Long shippingId, 
                           @Param("labelUrl") String labelUrl, 
                           @Param("labelCreatedAt") LocalDateTime labelCreatedAt, 
                           @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Delete shipping by order ID (soft delete)
     * 
     * @param orderId the order ID
     * @param deletedAt the deletion timestamp
     * @return number of deleted records
     */
    @Modifying
    @Query("UPDATE OrderShipping os SET os.deletedAt = :deletedAt WHERE os.order.id = :orderId")
    int softDeleteByOrderId(@Param("orderId") Long orderId, @Param("deletedAt") LocalDateTime deletedAt);
    
    /**
     * Find shipments needing label creation
     * 
     * @param statuses list of order statuses that need labels
     * @return list of shipments needing labels
     */
    @Query("SELECT os FROM OrderShipping os JOIN os.order o " +
           "WHERE o.status IN :statuses AND os.shippingLabelUrl IS NULL")
    List<OrderShipping> findShipmentsNeedingLabels(@Param("statuses") List<String> statuses);
    
    /**
     * Find overdue shipments
     * 
     * @param currentTime current timestamp
     * @param hours hours overdue threshold
     * @return list of overdue shipments
     */
    @Query("SELECT os FROM OrderShipping os WHERE os.estimatedDeliveryDate < :cutoffTime " +
           "AND os.actualDeliveryDate IS NULL " +
           "AND os.shippingStatus NOT IN ('DELIVERED', 'CANCELLED')")
    List<OrderShipping> findOverdueShipments(@Param("cutoffTime") LocalDateTime cutoffTime);
}
