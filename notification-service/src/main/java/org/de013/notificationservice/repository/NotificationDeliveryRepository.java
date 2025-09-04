package org.de013.notificationservice.repository;

import org.de013.notificationservice.entity.NotificationDelivery;
import org.de013.notificationservice.entity.enums.DeliveryStatus;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for NotificationDelivery entity
 */
@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    /**
     * Find deliveries by notification ID
     */
    List<NotificationDelivery> findByNotificationIdAndDeletedFalseOrderByCreatedAtDesc(Long notificationId);

    /**
     * Find deliveries by notification ID and channel
     */
    List<NotificationDelivery> findByNotificationIdAndChannelAndDeletedFalseOrderByCreatedAtDesc(
            Long notificationId, NotificationChannel channel);

    /**
     * Find deliveries by status
     */
    Page<NotificationDelivery> findByStatusAndDeletedFalseOrderByCreatedAtDesc(
            DeliveryStatus status, Pageable pageable);

    /**
     * Find deliveries ready for retry
     */
    @Query("SELECT d FROM NotificationDelivery d WHERE d.status IN :retryStatuses " +
           "AND d.nextAttemptAt <= :now " +
           "AND d.attemptCount < d.maxAttempts " +
           "AND d.deleted = false " +
           "ORDER BY d.nextAttemptAt ASC")
    List<NotificationDelivery> findReadyForRetry(@Param("retryStatuses") List<DeliveryStatus> retryStatuses,
                                               @Param("now") LocalDateTime now);

    /**
     * Find deliveries by external ID
     */
    Optional<NotificationDelivery> findByExternalIdAndDeletedFalse(String externalId);

    /**
     * Find deliveries by provider message ID
     */
    Optional<NotificationDelivery> findByProviderMessageIdAndDeletedFalse(String providerMessageId);

    /**
     * Find failed deliveries for notification
     */
    List<NotificationDelivery> findByNotificationIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            Long notificationId, DeliveryStatus status);

    /**
     * Count deliveries by status for notification
     */
    long countByNotificationIdAndStatusAndDeletedFalse(Long notificationId, DeliveryStatus status);

    /**
     * Find deliveries by channel and status
     */
    List<NotificationDelivery> findByChannelAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            NotificationChannel channel, DeliveryStatus status);

    /**
     * Find deliveries by provider name
     */
    List<NotificationDelivery> findByProviderNameAndDeletedFalseOrderByCreatedAtDesc(String providerName);

    /**
     * Find deliveries in time range
     */
    @Query("SELECT d FROM NotificationDelivery d WHERE d.attemptedAt BETWEEN :startTime AND :endTime " +
           "AND d.deleted = false ORDER BY d.attemptedAt DESC")
    List<NotificationDelivery> findDeliveriesInTimeRange(@Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime);

    /**
     * Count successful deliveries by channel in time period
     */
    @Query("SELECT COUNT(d) FROM NotificationDelivery d WHERE d.channel = :channel " +
           "AND d.status = :successStatus " +
           "AND d.deliveredAt BETWEEN :startTime AND :endTime " +
           "AND d.deleted = false")
    long countSuccessfulDeliveriesByChannel(@Param("channel") NotificationChannel channel,
                                          @Param("successStatus") DeliveryStatus successStatus,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * Count failed deliveries by channel in time period
     */
    @Query("SELECT COUNT(d) FROM NotificationDelivery d WHERE d.channel = :channel " +
           "AND d.status IN :failureStatuses " +
           "AND d.failedAt BETWEEN :startTime AND :endTime " +
           "AND d.deleted = false")
    long countFailedDeliveriesByChannel(@Param("channel") NotificationChannel channel,
                                      @Param("failureStatuses") List<DeliveryStatus> failureStatuses,
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);

    /**
     * Get average processing time by channel
     */
    @Query("SELECT AVG(d.processingTimeMs) FROM NotificationDelivery d WHERE d.channel = :channel " +
           "AND d.processingTimeMs IS NOT NULL " +
           "AND d.attemptedAt BETWEEN :startTime AND :endTime " +
           "AND d.deleted = false")
    Double getAverageProcessingTimeByChannel(@Param("channel") NotificationChannel channel,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * Update delivery status
     */
    @Modifying
    @Query("UPDATE NotificationDelivery d SET d.status = :status, d.updatedAt = :now " +
           "WHERE d.id = :id")
    int updateStatus(@Param("id") Long id,
                    @Param("status") DeliveryStatus status,
                    @Param("now") LocalDateTime now);

    /**
     * Find deliveries for analytics
     */
    @Query("SELECT d.channel, d.status, COUNT(d), AVG(d.processingTimeMs) " +
           "FROM NotificationDelivery d " +
           "WHERE d.attemptedAt BETWEEN :startDate AND :endDate " +
           "AND d.deleted = false " +
           "GROUP BY d.channel, d.status")
    List<Object[]> getDeliveryStatistics(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    /**
     * Find deliveries with high attempt count
     */
    @Query("SELECT d FROM NotificationDelivery d WHERE d.attemptCount >= :minAttempts " +
           "AND d.deleted = false ORDER BY d.attemptCount DESC")
    List<NotificationDelivery> findHighAttemptDeliveries(@Param("minAttempts") int minAttempts);

    /**
     * Calculate total cost by channel in time period
     */
    @Query("SELECT SUM(d.costCents) FROM NotificationDelivery d WHERE d.channel = :channel " +
           "AND d.deliveredAt BETWEEN :startTime AND :endTime " +
           "AND d.costCents IS NOT NULL " +
           "AND d.deleted = false")
    Long getTotalCostByChannel(@Param("channel") NotificationChannel channel,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);
}
