package org.de013.notificationservice.repository;

import org.de013.notificationservice.entity.NotificationTemplate;
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
 * Repository interface for NotificationTemplate entity
 */
@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    /**
     * Find active template by name, channel, and language
     */
    Optional<NotificationTemplate> findByNameAndChannelAndLanguageAndActiveTrueAndDeletedFalse(
            String name, NotificationChannel channel, String language);

    /**
     * Find active template by name and channel (default language)
     */
    Optional<NotificationTemplate> findByNameAndChannelAndActiveTrueAndDeletedFalse(
            String name, NotificationChannel channel);

    /**
     * Find all active templates by type and channel
     */
    List<NotificationTemplate> findByTypeAndChannelAndActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
            NotificationType type, NotificationChannel channel);

    /**
     * Find all active templates by type
     */
    List<NotificationTemplate> findByTypeAndActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
            NotificationType type);

    /**
     * Find all templates by name (all versions)
     */
    List<NotificationTemplate> findByNameAndDeletedFalseOrderByTemplateVersionDesc(String name);

    /**
     * Find latest version of template by name and channel
     */
    @Query("SELECT t FROM NotificationTemplate t WHERE t.name = :name " +
           "AND t.channel = :channel " +
           "AND t.deleted = false " +
           "ORDER BY t.templateVersion DESC")
    List<NotificationTemplate> findLatestVersionByNameAndChannel(@Param("name") String name, 
                                                               @Param("channel") NotificationChannel channel);

    /**
     * Find all active templates
     */
    Page<NotificationTemplate> findByActiveTrueAndDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find templates by category
     */
    List<NotificationTemplate> findByCategoryAndActiveTrueAndDeletedFalseOrderByCreatedAtDesc(String category);

    /**
     * Find templates by tags (contains)
     */
    @Query("SELECT t FROM NotificationTemplate t WHERE t.tags LIKE %:tag% " +
           "AND t.active = true AND t.deleted = false " +
           "ORDER BY t.createdAt DESC")
    List<NotificationTemplate> findByTagsContaining(@Param("tag") String tag);

    /**
     * Find templates by language
     */
    List<NotificationTemplate> findByLanguageAndActiveTrueAndDeletedFalseOrderByCreatedAtDesc(String language);

    /**
     * Count active templates by type
     */
    long countByTypeAndActiveTrueAndDeletedFalse(NotificationType type);

    /**
     * Count active templates by channel
     */
    long countByChannelAndActiveTrueAndDeletedFalse(NotificationChannel channel);

    /**
     * Find templates that need to be updated (old versions)
     */
    @Query("SELECT t FROM NotificationTemplate t WHERE t.updatedAt < :cutoffDate " +
           "AND t.active = true AND t.deleted = false")
    List<NotificationTemplate> findTemplatesNeedingUpdate(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Deactivate all templates with the same name and channel except the specified one
     */
    @Modifying
    @Query("UPDATE NotificationTemplate t SET t.active = false, t.updatedAt = :now " +
           "WHERE t.name = :name AND t.channel = :channel " +
           "AND t.id != :excludeId AND t.deleted = false")
    int deactivateOtherVersions(@Param("name") String name, 
                               @Param("channel") NotificationChannel channel,
                               @Param("excludeId") Long excludeId,
                               @Param("now") LocalDateTime now);

    /**
     * Find templates by parent template ID
     */
    List<NotificationTemplate> findByParentTemplateIdAndDeletedFalseOrderByTemplateVersionDesc(Long parentTemplateId);

    /**
     * Check if template name exists for channel
     */
    boolean existsByNameAndChannelAndDeletedFalse(String name, NotificationChannel channel);

    /**
     * Find templates for analytics
     */
    @Query("SELECT t.type, t.channel, t.language, COUNT(t) " +
           "FROM NotificationTemplate t " +
           "WHERE t.active = true AND t.deleted = false " +
           "GROUP BY t.type, t.channel, t.language")
    List<Object[]> getTemplateStatistics();

    /**
     * Search templates by name or description
     */
    @Query("SELECT t FROM NotificationTemplate t WHERE " +
           "(LOWER(t.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(t.displayName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND t.active = true AND t.deleted = false " +
           "ORDER BY t.createdAt DESC")
    Page<NotificationTemplate> searchTemplates(@Param("searchTerm") String searchTerm, Pageable pageable);
}
