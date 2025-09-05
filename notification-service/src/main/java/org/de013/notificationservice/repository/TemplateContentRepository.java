package org.de013.notificationservice.repository;

import org.de013.notificationservice.entity.TemplateContent;
import org.de013.notificationservice.entity.enums.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for TemplateContent entity
 */
@Repository
public interface TemplateContentRepository extends JpaRepository<TemplateContent, Long> {

    /**
     * Find template content by ID and not deleted
     */
    Optional<TemplateContent> findByIdAndDeletedFalse(Long id);

    /**
     * Find template contents by template ID, ordered by version descending
     */
    List<TemplateContent> findByTemplateIdAndDeletedFalseOrderByVersionDesc(Long templateId);

    /**
     * Find template content by template ID and language code
     */
    Optional<TemplateContent> findByTemplateIdAndLanguageCodeAndStatusAndDeletedFalse(
            Long templateId, String languageCode, ContentStatus status);

    /**
     * Find template content by template ID, language code, and country code
     */
    Optional<TemplateContent> findByTemplateIdAndLanguageCodeAndCountryCodeAndStatusAndDeletedFalse(
            Long templateId, String languageCode, String countryCode, ContentStatus status);

    /**
     * Find template content by template ID, language code, and country code (any status)
     */
    Optional<TemplateContent> findByTemplateIdAndLanguageCodeAndCountryCodeAndDeletedFalse(
            Long templateId, String languageCode, String countryCode);

    /**
     * Find default template content by template ID
     */
    Optional<TemplateContent> findByTemplateIdAndIsDefaultTrueAndStatusAndDeletedFalse(
            Long templateId, ContentStatus status);

    /**
     * Find all template contents ordered by updated date
     */
    Page<TemplateContent> findByDeletedFalseOrderByUpdatedAtDesc(Pageable pageable);

    /**
     * Find template contents by status
     */
    Page<TemplateContent> findByStatusAndDeletedFalseOrderByUpdatedAtDesc(ContentStatus status, Pageable pageable);

    /**
     * Find distinct language codes by template ID and status
     */
    @Query("SELECT DISTINCT tc.languageCode FROM TemplateContent tc " +
           "WHERE tc.templateId = :templateId AND tc.status = :status AND tc.deleted = false")
    List<String> findDistinctLanguageCodesByTemplateIdAndStatusAndDeletedFalse(
            @Param("templateId") Long templateId, @Param("status") ContentStatus status);

    /**
     * Find max version by template ID and language code
     */
    @Query("SELECT MAX(tc.version) FROM TemplateContent tc " +
           "WHERE tc.templateId = :templateId AND tc.languageCode = :languageCode AND tc.deleted = false")
    Optional<Integer> findMaxVersionByTemplateIdAndLanguageCode(
            @Param("templateId") Long templateId, @Param("languageCode") String languageCode);

    /**
     * Find template contents by template ID and status
     */
    List<TemplateContent> findByTemplateIdAndStatusAndDeletedFalse(Long templateId, ContentStatus status);

    /**
     * Find template contents by language code and status
     */
    List<TemplateContent> findByLanguageCodeAndStatusAndDeletedFalse(String languageCode, ContentStatus status);

    /**
     * Find template contents by created by user
     */
    List<TemplateContent> findByCreatedByAndDeletedFalseOrderByCreatedAtDesc(String createdBy);

    /**
     * Find template contents pending approval
     */
    @Query("SELECT tc FROM TemplateContent tc " +
           "WHERE tc.status IN ('PENDING_REVIEW', 'IN_REVIEW') AND tc.deleted = false " +
           "ORDER BY tc.createdAt ASC")
    List<TemplateContent> findContentsPendingApproval();

    /**
     * Find expired template contents
     */
    @Query("SELECT tc FROM TemplateContent tc " +
           "WHERE tc.expiresAt < CURRENT_TIMESTAMP AND tc.status = 'PUBLISHED' AND tc.deleted = false")
    List<TemplateContent> findExpiredContents();

    /**
     * Count template contents by status
     */
    long countByStatusAndDeletedFalse(ContentStatus status);

    /**
     * Count template contents by template ID
     */
    long countByTemplateIdAndDeletedFalse(Long templateId);

    /**
     * Count template contents by language code
     */
    long countByLanguageCodeAndDeletedFalse(String languageCode);

    /**
     * Find template contents by A/B test group
     */
    List<TemplateContent> findByAbTestGroupAndStatusAndDeletedFalse(String abTestGroup, ContentStatus status);

    /**
     * Find template contents with media attachments
     */
    @Query("SELECT tc FROM TemplateContent tc " +
           "WHERE SIZE(tc.mediaAttachments) > 0 AND tc.deleted = false")
    List<TemplateContent> findContentsWithMediaAttachments();

    /**
     * Search template contents by title or content
     */
    @Query("SELECT tc FROM TemplateContent tc " +
           "WHERE (LOWER(tc.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(tc.content) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(tc.subject) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND tc.deleted = false " +
           "ORDER BY tc.updatedAt DESC")
    Page<TemplateContent> searchContents(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find template contents by keywords
     */
    @Query("SELECT tc FROM TemplateContent tc " +
           "WHERE LOWER(tc.keywords) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "AND tc.deleted = false " +
           "ORDER BY tc.updatedAt DESC")
    List<TemplateContent> findByKeywordsContaining(@Param("keyword") String keyword);

    /**
     * Find template contents by date range
     */
    @Query("SELECT tc FROM TemplateContent tc " +
           "WHERE tc.createdAt BETWEEN :startDate AND :endDate " +
           "AND tc.deleted = false " +
           "ORDER BY tc.createdAt DESC")
    List<TemplateContent> findByDateRange(@Param("startDate") java.time.LocalDateTime startDate,
                                         @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Get content statistics
     */
    @Query("SELECT tc.status, tc.languageCode, COUNT(tc) " +
           "FROM TemplateContent tc " +
           "WHERE tc.deleted = false " +
           "GROUP BY tc.status, tc.languageCode")
    List<Object[]> getContentStatistics();

    /**
     * Find template contents needing translation
     */
    @Query("SELECT DISTINCT tc.templateId FROM TemplateContent tc " +
           "WHERE tc.languageCode = :sourceLanguage " +
           "AND tc.status = 'PUBLISHED' " +
           "AND tc.deleted = false " +
           "AND NOT EXISTS (" +
           "    SELECT 1 FROM TemplateContent tc2 " +
           "    WHERE tc2.templateId = tc.templateId " +
           "    AND tc2.languageCode = :targetLanguage " +
           "    AND tc2.deleted = false" +
           ")")
    List<Long> findTemplateIdsNeedingTranslation(@Param("sourceLanguage") String sourceLanguage,
                                                @Param("targetLanguage") String targetLanguage);

    /**
     * Find latest version of template content by template ID and language
     */
    @Query("SELECT tc FROM TemplateContent tc " +
           "WHERE tc.templateId = :templateId " +
           "AND tc.languageCode = :languageCode " +
           "AND tc.version = (" +
           "    SELECT MAX(tc2.version) FROM TemplateContent tc2 " +
           "    WHERE tc2.templateId = :templateId " +
           "    AND tc2.languageCode = :languageCode " +
           "    AND tc2.deleted = false" +
           ") " +
           "AND tc.deleted = false")
    Optional<TemplateContent> findLatestVersionByTemplateIdAndLanguage(
            @Param("templateId") Long templateId, @Param("languageCode") String languageCode);

    /**
     * Find template contents by approval status
     */
    @Query("SELECT tc FROM TemplateContent tc " +
           "WHERE tc.approvedBy = :approvedBy " +
           "AND tc.deleted = false " +
           "ORDER BY tc.approvedAt DESC")
    List<TemplateContent> findByApprovedBy(@Param("approvedBy") Long approvedBy);

    /**
     * Find template contents by rejection status
     */
    @Query("SELECT tc FROM TemplateContent tc " +
           "WHERE tc.rejectedBy = :rejectedBy " +
           "AND tc.deleted = false " +
           "ORDER BY tc.rejectedAt DESC")
    List<TemplateContent> findByRejectedBy(@Param("rejectedBy") Long rejectedBy);
}
