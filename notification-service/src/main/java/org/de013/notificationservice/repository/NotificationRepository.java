package org.de013.notificationservice.repository;

import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.entity.enums.Priority;
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
 * Repository interface for Notification entity
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find notifications by user ID
     */
    Page<Notification> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Find notifications by user ID and status
     */
    Page<Notification> findByUserIdAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            Long userId, NotificationStatus status, Pageable pageable);

    /**
     * Find notifications by user ID and type
     */
    Page<Notification> findByUserIdAndTypeAndDeletedFalseOrderByCreatedAtDesc(
            Long userId, NotificationType type, Pageable pageable);

    /**
     * Find notifications by user ID and channel
     */
    Page<Notification> findByUserIdAndChannelAndDeletedFalseOrderByCreatedAtDesc(
            Long userId, NotificationChannel channel, Pageable pageable);

    /**
     * Find notifications ready for delivery
     */
    @Query("SELECT n FROM Notification n WHERE n.status IN :statuses " +
           "AND (n.scheduledAt IS NULL OR n.scheduledAt <= :now) " +
           "AND (n.expiresAt IS NULL OR n.expiresAt > :now) " +
           "AND n.deleted = false " +
           "ORDER BY n.priority DESC, n.createdAt ASC")
    List<Notification> findReadyForDelivery(@Param("statuses") List<NotificationStatus> statuses, 
                                          @Param("now") LocalDateTime now);

    /**
     * Find notifications ready for retry
     */
    @Query("SELECT n FROM Notification n WHERE n.status = :retryStatus " +
           "AND n.nextRetryAt <= :now " +
           "AND n.retryCount < n.maxRetryAttempts " +
           "AND (n.expiresAt IS NULL OR n.expiresAt > :now) " +
           "AND n.deleted = false " +
           "ORDER BY n.priority DESC, n.nextRetryAt ASC")
    List<Notification> findReadyForRetry(@Param("retryStatus") NotificationStatus retryStatus, 
                                       @Param("now") LocalDateTime now);

    /**
     * Find expired notifications
     */
    @Query("SELECT n FROM Notification n WHERE n.expiresAt <= :now " +
           "AND n.status NOT IN :terminalStatuses " +
           "AND n.deleted = false")
    List<Notification> findExpiredNotifications(@Param("now") LocalDateTime now, 
                                               @Param("terminalStatuses") List<NotificationStatus> terminalStatuses);

    /**
     * Count notifications by user and status
     */
    long countByUserIdAndStatusAndDeletedFalse(Long userId, NotificationStatus status);

    /**
     * Count unread notifications for user
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId " +
           "AND n.status IN ('SENT', 'DELIVERED') " +
           "AND n.deleted = false")
    long countUnreadByUserId(@Param("userId") Long userId);

    /**
     * Find notifications by correlation ID
     */
    List<Notification> findByCorrelationIdAndDeletedFalseOrderByCreatedAtDesc(String correlationId);

    /**
     * Find notifications by reference
     */
    List<Notification> findByReferenceTypeAndReferenceIdAndDeletedFalseOrderByCreatedAtDesc(
            String referenceType, String referenceId);

    /**
     * Find notifications by external ID
     */
    Optional<Notification> findByExternalIdAndDeletedFalse(String externalId);

    /**
     * Find notifications by template ID
     */
    Page<Notification> findByTemplateIdAndDeletedFalseOrderByCreatedAtDesc(Long templateId, Pageable pageable);

    /**
     * Count notifications sent in time period for user
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId " +
           "AND n.channel = :channel " +
           "AND n.type = :type " +
           "AND n.sentAt BETWEEN :startTime AND :endTime " +
           "AND n.deleted = false")
    long countSentInPeriod(@Param("userId") Long userId, 
                          @Param("channel") NotificationChannel channel,
                          @Param("type") NotificationType type,
                          @Param("startTime") LocalDateTime startTime, 
                          @Param("endTime") LocalDateTime endTime);

    /**
     * Find notifications by priority
     */
    Page<Notification> findByPriorityAndDeletedFalseOrderByCreatedAtDesc(Priority priority, Pageable pageable);

    /**
     * Find scheduled notifications
     */
    @Query("SELECT n FROM Notification n WHERE n.scheduledAt > :now " +
           "AND n.status = :status " +
           "AND n.deleted = false " +
           "ORDER BY n.scheduledAt ASC")
    List<Notification> findScheduledNotifications(@Param("now") LocalDateTime now, 
                                                 @Param("status") NotificationStatus status);

    /**
     * Update notification status
     */
    @Modifying
    @Query("UPDATE Notification n SET n.status = :status, n.updatedAt = :now " +
           "WHERE n.id = :id")
    int updateStatus(@Param("id") Long id, 
                    @Param("status") NotificationStatus status, 
                    @Param("now") LocalDateTime now);

    /**
     * Mark notification as read
     */
    @Modifying
    @Query("UPDATE Notification n SET n.status = :readStatus, n.readAt = :now, n.updatedAt = :now " +
           "WHERE n.id = :id AND n.userId = :userId")
    int markAsRead(@Param("id") Long id, 
                  @Param("userId") Long userId, 
                  @Param("readStatus") NotificationStatus readStatus, 
                  @Param("now") LocalDateTime now);

    /**
     * Soft delete notifications older than specified date
     */
    @Modifying
    @Query("UPDATE Notification n SET n.deleted = true, n.deletedAt = :now, n.deletedBy = :deletedBy " +
           "WHERE n.createdAt < :cutoffDate AND n.deleted = false")
    int softDeleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate, 
                           @Param("now") LocalDateTime now, 
                           @Param("deletedBy") String deletedBy);

    /**
     * Find notifications for analytics
     */
    @Query("SELECT n.type, n.channel, n.status, COUNT(n) " +
           "FROM Notification n " +
           "WHERE n.createdAt BETWEEN :startDate AND :endDate " +
           "AND n.deleted = false " +
           "GROUP BY n.type, n.channel, n.status")
    List<Object[]> getNotificationStatistics(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    /**
     * Find pending notifications for digest
     */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId " +
           "AND n.status IN ('PENDING', 'QUEUED') " +
           "AND n.createdAt BETWEEN :startDate AND :endDate " +
           "AND n.digestId IS NULL " +
           "AND n.deleted = false " +
           "ORDER BY n.createdAt DESC")
    List<Notification> findPendingNotificationsForDigest(@Param("userId") Long userId,
                                                        @Param("startDate") LocalDateTime startDate,
                                                        @Param("endDate") LocalDateTime endDate);

    /**
     * Count digest notifications by frequency
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.type = 'DIGEST' " +
           "AND n.referenceId LIKE CONCAT(:frequency, '%') " +
           "AND n.createdAt BETWEEN :startDate AND :endDate " +
           "AND n.deleted = false")
    long countDigestNotifications(@Param("frequency") String frequency,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);

    /**
     * Count all digest notifications
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.type = 'DIGEST' " +
           "AND n.createdAt BETWEEN :startDate AND :endDate " +
           "AND n.deleted = false")
    long countAllDigestNotifications(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    /**
     * Get average notifications per digest
     */
    @Query(value = "SELECT COALESCE(AVG(CAST(template_variables->>'totalCount' AS integer)), 0) FROM notifications n " +
           "WHERE n.type = 'DIGEST' " +
           "AND n.created_at BETWEEN :startDate AND :endDate " +
           "AND n.template_variables->>'totalCount' IS NOT NULL " +
           "AND n.deleted = false", nativeQuery = true)
    double getAverageNotificationsPerDigest(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);
}
