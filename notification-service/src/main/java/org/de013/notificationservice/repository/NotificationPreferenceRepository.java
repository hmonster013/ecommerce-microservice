package org.de013.notificationservice.repository;

import org.de013.notificationservice.entity.NotificationPreference;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
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
 * Repository interface for NotificationPreference entity
 */
@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    /**
     * Find preference by user, channel, and type
     */
    Optional<NotificationPreference> findByUserIdAndChannelAndTypeAndDeletedFalse(
            Long userId, NotificationChannel channel, NotificationType type);

    /**
     * Find all preferences for a user
     */
    List<NotificationPreference> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    /**
     * Find preferences by user and channel
     */
    List<NotificationPreference> findByUserIdAndChannelAndDeletedFalseOrderByCreatedAtDesc(
            Long userId, NotificationChannel channel);

    /**
     * Find preferences by user and type
     */
    List<NotificationPreference> findByUserIdAndTypeAndDeletedFalseOrderByCreatedAtDesc(
            Long userId, NotificationType type);

    /**
     * Find enabled preferences for user
     */
    List<NotificationPreference> findByUserIdAndEnabledTrueAndGlobalOptOutFalseAndDeletedFalseOrderByCreatedAtDesc(
            Long userId);

    /**
     * Find enabled preferences for user, channel, and type
     */
    Optional<NotificationPreference> findByUserIdAndChannelAndTypeAndEnabledTrueAndGlobalOptOutFalseAndDeletedFalse(
            Long userId, NotificationChannel channel, NotificationType type);

    /**
     * Find users who opted out globally
     */
    @Query("SELECT DISTINCT p.userId FROM NotificationPreference p WHERE p.globalOptOut = true " +
           "AND p.deleted = false")
    List<Long> findGloballyOptedOutUsers();

    /**
     * Find users who opted out from specific channel
     */
    @Query("SELECT DISTINCT p.userId FROM NotificationPreference p WHERE p.channel = :channel " +
           "AND (p.enabled = false OR p.globalOptOut = true) " +
           "AND p.deleted = false")
    List<Long> findOptedOutUsersForChannel(@Param("channel") NotificationChannel channel);

    /**
     * Find users who opted out from specific type
     */
    @Query("SELECT DISTINCT p.userId FROM NotificationPreference p WHERE p.type = :type " +
           "AND (p.enabled = false OR p.globalOptOut = true) " +
           "AND p.deleted = false")
    List<Long> findOptedOutUsersForType(@Param("type") NotificationType type);

    /**
     * Check if user has any enabled preferences
     */
    boolean existsByUserIdAndEnabledTrueAndGlobalOptOutFalseAndDeletedFalse(Long userId);

    /**
     * Count preferences by channel
     */
    long countByChannelAndDeletedFalse(NotificationChannel channel);

    /**
     * Count enabled preferences by channel
     */
    long countByChannelAndEnabledTrueAndGlobalOptOutFalseAndDeletedFalse(NotificationChannel channel);

    /**
     * Count preferences by type
     */
    long countByTypeAndDeletedFalse(NotificationType type);

    /**
     * Count enabled preferences by type
     */
    long countByTypeAndEnabledTrueAndGlobalOptOutFalseAndDeletedFalse(NotificationType type);

    /**
     * Find preferences with quiet hours enabled
     */
    List<NotificationPreference> findByQuietHoursEnabledTrueAndDeletedFalseOrderByCreatedAtDesc();

    /**
     * Find preferences by timezone
     */
    List<NotificationPreference> findByTimezoneAndDeletedFalseOrderByCreatedAtDesc(String timezone);

    /**
     * Find preferences by language
     */
    List<NotificationPreference> findByLanguageAndDeletedFalseOrderByCreatedAtDesc(String language);

    /**
     * Update global opt-out status for user
     */
    @Modifying
    @Query("UPDATE NotificationPreference p SET p.globalOptOut = :optOut, p.updatedAt = :now " +
           "WHERE p.userId = :userId AND p.deleted = false")
    int updateGlobalOptOut(@Param("userId") Long userId,
                          @Param("optOut") boolean optOut,
                          @Param("now") LocalDateTime now);

    /**
     * Update enabled status for user and channel
     */
    @Modifying
    @Query("UPDATE NotificationPreference p SET p.enabled = :enabled, p.updatedAt = :now " +
           "WHERE p.userId = :userId AND p.channel = :channel AND p.deleted = false")
    int updateEnabledForChannel(@Param("userId") Long userId,
                               @Param("channel") NotificationChannel channel,
                               @Param("enabled") boolean enabled,
                               @Param("now") LocalDateTime now);

    /**
     * Update enabled status for user and type
     */
    @Modifying
    @Query("UPDATE NotificationPreference p SET p.enabled = :enabled, p.updatedAt = :now " +
           "WHERE p.userId = :userId AND p.type = :type AND p.deleted = false")
    int updateEnabledForType(@Param("userId") Long userId,
                            @Param("type") NotificationType type,
                            @Param("enabled") boolean enabled,
                            @Param("now") LocalDateTime now);

    /**
     * Find preferences for analytics
     */
    @Query("SELECT p.channel, p.type, p.enabled, p.globalOptOut, COUNT(p) " +
           "FROM NotificationPreference p " +
           "WHERE p.deleted = false " +
           "GROUP BY p.channel, p.type, p.enabled, p.globalOptOut")
    List<Object[]> getPreferenceStatistics();

    /**
     * Find users with frequency limits
     */
    @Query("SELECT p FROM NotificationPreference p WHERE " +
           "(p.frequencyLimitPerHour IS NOT NULL OR p.frequencyLimitPerDay IS NOT NULL) " +
           "AND p.deleted = false ORDER BY p.createdAt DESC")
    List<NotificationPreference> findUsersWithFrequencyLimits();

    /**
     * Check if preference exists for user, channel, and type
     */
    boolean existsByUserIdAndChannelAndTypeAndDeletedFalse(Long userId, NotificationChannel channel, NotificationType type);
}
