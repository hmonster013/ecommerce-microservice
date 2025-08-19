package org.de013.orderservice.repository;

import org.de013.orderservice.entity.OrderTracking;
import org.de013.orderservice.entity.enums.TrackingStatus;
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
 * Order Tracking Repository
 * 
 * JPA repository for OrderTracking entity with timeline queries and tracking analytics.
 * Provides comprehensive data access methods for order tracking management.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Repository
public interface OrderTrackingRepository extends JpaRepository<OrderTracking, Long>, JpaSpecificationExecutor<OrderTracking> {
    
    /**
     * Find tracking records by order ID ordered by timestamp descending
     * 
     * @param orderId the order ID
     * @return list of tracking records
     */
    List<OrderTracking> findByOrderIdOrderByTimestampDesc(Long orderId);
    
    /**
     * Find tracking records by order ID with pagination
     * 
     * @param orderId the order ID
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByOrderIdOrderByTimestampDesc(Long orderId, Pageable pageable);
    
    /**
     * Find latest tracking record for order
     * 
     * @param orderId the order ID
     * @return optional latest tracking record
     */
    Optional<OrderTracking> findFirstByOrderIdOrderByTimestampDesc(Long orderId);
    
    /**
     * Find tracking records by tracking status
     * 
     * @param trackingStatus the tracking status
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByTrackingStatus(TrackingStatus trackingStatus, Pageable pageable);
    
    /**
     * Find tracking records by tracking number
     * 
     * @param trackingNumber the tracking number
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByTrackingNumber(String trackingNumber, Pageable pageable);
    
    /**
     * Find tracking records by carrier
     * 
     * @param carrier the carrier name
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByCarrier(String carrier, Pageable pageable);
    
    /**
     * Find tracking records by location
     * 
     * @param location the location
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByLocationContainingIgnoreCase(String location, Pageable pageable);
    
    /**
     * Find tracking records by city
     * 
     * @param city the city
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByCity(String city, Pageable pageable);
    
    /**
     * Find tracking records by state
     * 
     * @param state the state
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByState(String state, Pageable pageable);
    
    /**
     * Find tracking records by country
     * 
     * @param country the country
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByCountry(String country, Pageable pageable);
    
    /**
     * Find tracking records by timestamp range
     * 
     * @param startDate start date
     * @param endDate end date
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find tracking records that are customer visible
     * 
     * @param pageable pagination information
     * @return page of customer visible tracking records
     */
    Page<OrderTracking> findByIsCustomerVisibleTrue(Pageable pageable);
    
    /**
     * Find automated tracking updates
     * 
     * @param pageable pagination information
     * @return page of automated tracking records
     */
    Page<OrderTracking> findByIsAutomatedTrue(Pageable pageable);
    
    /**
     * Find manual tracking updates
     * 
     * @param pageable pagination information
     * @return page of manual tracking records
     */
    Page<OrderTracking> findByIsAutomatedFalse(Pageable pageable);
    
    /**
     * Find tracking records by priority level
     * 
     * @param priorityLevel the priority level
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByPriorityLevel(Integer priorityLevel, Pageable pageable);
    
    /**
     * Find tracking records with delivery attempts
     * 
     * @param pageable pagination information
     * @return page of tracking records with delivery attempts
     */
    Page<OrderTracking> findByDeliveryAttemptGreaterThan(Integer attempt, Pageable pageable);
    
    /**
     * Find failed delivery attempts
     * 
     * @param pageable pagination information
     * @return page of failed delivery attempts
     */
    Page<OrderTracking> findByDeliveryFailureReasonIsNotNull(Pageable pageable);
    
    /**
     * Find tracking records with proof of delivery
     * 
     * @param pageable pagination information
     * @return page of tracking records with proof of delivery
     */
    Page<OrderTracking> findByProofOfDeliveryUrlIsNotNull(Pageable pageable);
    
    /**
     * Count tracking records by order ID
     * 
     * @param orderId the order ID
     * @return count of tracking records
     */
    long countByOrderId(Long orderId);
    
    /**
     * Count tracking records by status
     * 
     * @param trackingStatus the tracking status
     * @return count of tracking records
     */
    long countByTrackingStatus(TrackingStatus trackingStatus);
    
    /**
     * Count tracking records by carrier
     * 
     * @param carrier the carrier name
     * @return count of tracking records
     */
    long countByCarrier(String carrier);
    
    /**
     * Find tracking timeline for order
     * 
     * @param orderId the order ID
     * @return list of tracking records ordered by timestamp
     */
    @Query("SELECT ot FROM OrderTracking ot WHERE ot.order.id = :orderId " +
           "AND ot.isCustomerVisible = true " +
           "ORDER BY ot.timestamp ASC")
    List<OrderTracking> findTrackingTimelineForOrder(@Param("orderId") Long orderId);
    
    /**
     * Find latest tracking status for multiple orders
     * 
     * @param orderIds list of order IDs
     * @return list of latest tracking records
     */
    @Query("SELECT ot FROM OrderTracking ot WHERE ot.order.id IN :orderIds " +
           "AND ot.timestamp = (SELECT MAX(ot2.timestamp) FROM OrderTracking ot2 WHERE ot2.order.id = ot.order.id)")
    List<OrderTracking> findLatestTrackingForOrders(@Param("orderIds") List<Long> orderIds);
    
    /**
     * Find orders with specific tracking status
     * 
     * @param trackingStatus the tracking status
     * @param pageable pagination information
     * @return page of order IDs
     */
    @Query("SELECT DISTINCT ot.order.id FROM OrderTracking ot WHERE ot.trackingStatus = :trackingStatus " +
           "AND ot.timestamp = (SELECT MAX(ot2.timestamp) FROM OrderTracking ot2 WHERE ot2.order.id = ot.order.id)")
    Page<Long> findOrdersWithTrackingStatus(@Param("trackingStatus") TrackingStatus trackingStatus, Pageable pageable);
    
    /**
     * Find orders with problem tracking statuses
     * 
     * @param problemStatuses list of problem tracking statuses
     * @param pageable pagination information
     * @return page of order IDs
     */
    @Query("SELECT DISTINCT ot.order.id FROM OrderTracking ot WHERE ot.trackingStatus IN :problemStatuses " +
           "AND ot.timestamp = (SELECT MAX(ot2.timestamp) FROM OrderTracking ot2 WHERE ot2.order.id = ot.order.id)")
    Page<Long> findOrdersWithProblemStatus(@Param("problemStatuses") List<TrackingStatus> problemStatuses, Pageable pageable);
    
    /**
     * Find tracking records requiring customer action
     * 
     * @param actionStatuses list of statuses requiring customer action
     * @param pageable pagination information
     * @return page of tracking records
     */
    @Query("SELECT ot FROM OrderTracking ot WHERE ot.trackingStatus IN :actionStatuses " +
           "AND ot.isCustomerVisible = true " +
           "ORDER BY ot.timestamp DESC")
    Page<OrderTracking> findTrackingRequiringCustomerAction(@Param("actionStatuses") List<TrackingStatus> actionStatuses, Pageable pageable);
    
    /**
     * Find tracking records by external tracking ID
     * 
     * @param externalTrackingId the external tracking ID
     * @return list of tracking records
     */
    List<OrderTracking> findByExternalTrackingId(String externalTrackingId);
    
    /**
     * Find recent tracking updates
     * 
     * @param hours number of hours to look back
     * @param pageable pagination information
     * @return page of recent tracking records
     */
    @Query("SELECT ot FROM OrderTracking ot WHERE ot.timestamp >= :since ORDER BY ot.timestamp DESC")
    Page<OrderTracking> findRecentTrackingUpdates(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * Find tracking records by update source
     * 
     * @param updateSource the update source
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByUpdateSource(String updateSource, Pageable pageable);
    
    /**
     * Find tracking records updated by user
     * 
     * @param userId the user ID
     * @param pageable pagination information
     * @return page of tracking records
     */
    Page<OrderTracking> findByUpdatedByUserId(Long userId, Pageable pageable);
    
    /**
     * Get tracking statistics for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return tracking statistics
     */
    @Query("SELECT new map(" +
           "COUNT(ot) as totalUpdates, " +
           "COUNT(DISTINCT ot.order.id) as uniqueOrders, " +
           "COUNT(DISTINCT ot.carrier) as uniqueCarriers, " +
           "SUM(CASE WHEN ot.isAutomated = true THEN 1 ELSE 0 END) as automatedUpdates, " +
           "SUM(CASE WHEN ot.isAutomated = false THEN 1 ELSE 0 END) as manualUpdates) " +
           "FROM OrderTracking ot WHERE ot.timestamp BETWEEN :startDate AND :endDate")
    java.util.Map<String, Object> getTrackingStatistics(@Param("startDate") LocalDateTime startDate, 
                                                        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get tracking count by status for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return tracking count by status
     */
    @Query("SELECT ot.trackingStatus, COUNT(ot) FROM OrderTracking ot " +
           "WHERE ot.timestamp BETWEEN :startDate AND :endDate " +
           "GROUP BY ot.trackingStatus " +
           "ORDER BY COUNT(ot) DESC")
    List<Object[]> getTrackingCountByStatus(@Param("startDate") LocalDateTime startDate, 
                                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get tracking count by carrier for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return tracking count by carrier
     */
    @Query("SELECT ot.carrier, COUNT(ot) FROM OrderTracking ot " +
           "WHERE ot.timestamp BETWEEN :startDate AND :endDate " +
           "AND ot.carrier IS NOT NULL " +
           "GROUP BY ot.carrier " +
           "ORDER BY COUNT(ot) DESC")
    List<Object[]> getTrackingCountByCarrier(@Param("startDate") LocalDateTime startDate, 
                                            @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get tracking count by location for date range
     * 
     * @param startDate start date
     * @param endDate end date
     * @return tracking count by location
     */
    @Query("SELECT ot.city, ot.state, ot.country, COUNT(ot) FROM OrderTracking ot " +
           "WHERE ot.timestamp BETWEEN :startDate AND :endDate " +
           "AND ot.city IS NOT NULL " +
           "GROUP BY ot.city, ot.state, ot.country " +
           "ORDER BY COUNT(ot) DESC")
    List<Object[]> getTrackingCountByLocation(@Param("startDate") LocalDateTime startDate, 
                                             @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find delivery data by carrier for calculation
     *
     * @param startDate start date
     * @param endDate end date
     * @return delivery data by carrier
     */
    @Query("SELECT ot.carrier, ot.timestamp, ot.actualDeliveryDate " +
           "FROM OrderTracking ot " +
           "WHERE ot.timestamp BETWEEN :startDate AND :endDate " +
           "AND ot.actualDeliveryDate IS NOT NULL " +
           "AND ot.carrier IS NOT NULL " +
           "ORDER BY ot.carrier")
    List<Object[]> getDeliveryDataByCarrier(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * Update tracking visibility
     * 
     * @param trackingId the tracking ID
     * @param isVisible the visibility flag
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderTracking ot SET ot.isCustomerVisible = :isVisible, ot.updatedAt = :updatedAt WHERE ot.id = :trackingId")
    int updateTrackingVisibility(@Param("trackingId") Long trackingId, 
                                @Param("isVisible") Boolean isVisible, 
                                @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Update tracking priority
     * 
     * @param trackingId the tracking ID
     * @param priorityLevel the new priority level
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderTracking ot SET ot.priorityLevel = :priorityLevel, ot.updatedAt = :updatedAt WHERE ot.id = :trackingId")
    int updateTrackingPriority(@Param("trackingId") Long trackingId, 
                              @Param("priorityLevel") Integer priorityLevel, 
                              @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Bulk update tracking records for order
     * 
     * @param orderId the order ID
     * @param isVisible the visibility flag
     * @param updatedAt the update timestamp
     * @return number of updated records
     */
    @Modifying
    @Query("UPDATE OrderTracking ot SET ot.isCustomerVisible = :isVisible, ot.updatedAt = :updatedAt WHERE ot.order.id = :orderId")
    int bulkUpdateTrackingVisibilityForOrder(@Param("orderId") Long orderId, 
                                            @Param("isVisible") Boolean isVisible, 
                                            @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * Delete tracking records by order ID (soft delete)
     * 
     * @param orderId the order ID
     * @param deletedAt the deletion timestamp
     * @return number of deleted records
     */
    @Modifying
    @Query("UPDATE OrderTracking ot SET ot.deletedAt = :deletedAt WHERE ot.order.id = :orderId")
    int softDeleteByOrderId(@Param("orderId") Long orderId, @Param("deletedAt") LocalDateTime deletedAt);
    
    /**
     * Find stale tracking records (no updates for specified hours)
     * 
     * @param hours number of hours
     * @param excludeStatuses statuses to exclude from stale check
     * @return list of stale tracking records
     */
    @Query("SELECT ot FROM OrderTracking ot WHERE ot.timestamp < :cutoffTime " +
           "AND ot.trackingStatus NOT IN :excludeStatuses " +
           "AND ot.id = (SELECT MAX(ot2.id) FROM OrderTracking ot2 WHERE ot2.order.id = ot.order.id)")
    List<OrderTracking> findStaleTrackingRecords(@Param("cutoffTime") LocalDateTime cutoffTime, 
                                                 @Param("excludeStatuses") List<TrackingStatus> excludeStatuses);
    
    /**
     * Find tracking records needing follow-up
     * 
     * @param followUpStatuses statuses that need follow-up
     * @param hours hours since last update
     * @return list of tracking records needing follow-up
     */
    @Query("SELECT ot FROM OrderTracking ot WHERE ot.trackingStatus IN :followUpStatuses " +
           "AND ot.timestamp < :cutoffTime " +
           "AND ot.id = (SELECT MAX(ot2.id) FROM OrderTracking ot2 WHERE ot2.order.id = ot.order.id)")
    List<OrderTracking> findTrackingNeedingFollowUp(@Param("followUpStatuses") List<TrackingStatus> followUpStatuses, 
                                                    @Param("cutoffTime") LocalDateTime cutoffTime);
}
