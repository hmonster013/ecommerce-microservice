package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.TemplateContent;
import org.de013.notificationservice.entity.enums.ContentStatus;
import org.de013.notificationservice.repository.TemplateContentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing template content with rich text, media, and approval workflow
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ContentManagementService {

    private final TemplateContentRepository templateContentRepository;
    private final MediaStorageService mediaStorageService;
    private final ContentApprovalService contentApprovalService;

    /**
     * Create new template content
     */
    @Transactional
    public TemplateContent createContent(TemplateContent content) {
        log.info("Creating new template content: templateId={}, language={}", 
                content.getTemplateId(), content.getLanguageCode());

        try {
            // Set initial status and metadata
            content.setStatus(ContentStatus.DRAFT);
            content.setContentVersion(getNextVersion(content.getTemplateId(), content.getLanguageCode()));
            content.setCreatedAt(LocalDateTime.now());
            content.setUpdatedAt(LocalDateTime.now());

            // Process rich text content
            content = processRichTextContent(content);

            // Generate plain text version
            if (content.getPlainTextContent() == null && content.getHtmlContent() != null) {
                content.setPlainTextContent(stripHtmlTags(content.getHtmlContent()));
            }

            TemplateContent savedContent = templateContentRepository.save(content);
            log.info("Template content created successfully: id={}", savedContent.getId());

            return savedContent;

        } catch (Exception e) {
            log.error("Error creating template content: {}", e.getMessage(), e);
            throw new ContentManagementException("Failed to create template content: " + e.getMessage(), e);
        }
    }

    /**
     * Update template content
     */
    @Transactional
    public TemplateContent updateContent(Long contentId, TemplateContent updatedContent) {
        log.info("Updating template content: id={}", contentId);

        try {
            TemplateContent existingContent = getContentById(contentId);

            // Check if content can be edited
            if (!existingContent.getStatus().canEdit()) {
                throw new ContentManagementException("Content cannot be edited in current status: " + existingContent.getStatus());
            }

            // Update fields
            existingContent.setTitle(updatedContent.getTitle());
            existingContent.setSubject(updatedContent.getSubject());
            existingContent.setContent(updatedContent.getContent());
            existingContent.setHtmlContent(updatedContent.getHtmlContent());
            existingContent.setPlainTextContent(updatedContent.getPlainTextContent());
            existingContent.setAltText(updatedContent.getAltText());
            existingContent.setMetaDescription(updatedContent.getMetaDescription());
            existingContent.setKeywords(updatedContent.getKeywords());
            existingContent.setExpiresAt(updatedContent.getExpiresAt());
            existingContent.setUpdatedAt(LocalDateTime.now());

            // Process rich text content
            existingContent = processRichTextContent(existingContent);

            // Generate plain text version if not provided
            if (existingContent.getPlainTextContent() == null && existingContent.getHtmlContent() != null) {
                existingContent.setPlainTextContent(stripHtmlTags(existingContent.getHtmlContent()));
            }

            TemplateContent savedContent = templateContentRepository.save(existingContent);
            log.info("Template content updated successfully: id={}", savedContent.getId());

            return savedContent;

        } catch (Exception e) {
            log.error("Error updating template content: id={}, error={}", contentId, e.getMessage(), e);
            throw new ContentManagementException("Failed to update template content: " + e.getMessage(), e);
        }
    }

    /**
     * Get template content by ID
     */
    public TemplateContent getContentById(Long contentId) {
        return templateContentRepository.findByIdAndDeletedFalse(contentId)
                .orElseThrow(() -> new ContentManagementException("Template content not found: " + contentId));
    }

    /**
     * Get template contents by template ID
     */
    public List<TemplateContent> getContentsByTemplateId(Long templateId) {
        return templateContentRepository.findByTemplateIdAndDeletedFalseOrderByVersionDesc(templateId);
    }

    /**
     * Get template content by template ID and language
     */
    public Optional<TemplateContent> getContentByTemplateIdAndLanguage(Long templateId, String languageCode) {
        return templateContentRepository.findByTemplateIdAndLanguageCodeAndStatusAndDeletedFalse(
                templateId, languageCode, ContentStatus.PUBLISHED);
    }

    /**
     * Get template content with fallback
     */
    public TemplateContent getContentWithFallback(Long templateId, String languageCode, String fallbackLanguage) {
        // Try to get content in requested language
        Optional<TemplateContent> content = getContentByTemplateIdAndLanguage(templateId, languageCode);
        if (content.isPresent()) {
            return content.get();
        }

        // Try fallback language
        if (fallbackLanguage != null && !fallbackLanguage.equals(languageCode)) {
            content = getContentByTemplateIdAndLanguage(templateId, fallbackLanguage);
            if (content.isPresent()) {
                return content.get();
            }
        }

        // Try default content
        Optional<TemplateContent> defaultContent = templateContentRepository
                .findByTemplateIdAndIsDefaultTrueAndStatusAndDeletedFalse(templateId, ContentStatus.PUBLISHED);
        if (defaultContent.isPresent()) {
            return defaultContent.get();
        }

        throw new ContentManagementException("No content found for template: " + templateId);
    }

    /**
     * Get all template contents with pagination
     */
    public Page<TemplateContent> getAllContents(Pageable pageable) {
        return templateContentRepository.findByDeletedFalseOrderByUpdatedAtDesc(pageable);
    }

    /**
     * Get template contents by status
     */
    public Page<TemplateContent> getContentsByStatus(ContentStatus status, Pageable pageable) {
        return templateContentRepository.findByStatusAndDeletedFalseOrderByUpdatedAtDesc(status, pageable);
    }

    /**
     * Submit content for review
     */
    @Transactional
    public TemplateContent submitForReview(Long contentId, Long submittedBy) {
        log.info("Submitting content for review: id={}, submittedBy={}", contentId, submittedBy);

        try {
            TemplateContent content = getContentById(contentId);

            if (!content.getStatus().canSubmitForReview()) {
                throw new ContentManagementException("Content cannot be submitted for review in current status: " + content.getStatus());
            }

            content.setStatus(ContentStatus.PENDING_REVIEW);
            content.setUpdatedAt(LocalDateTime.now());

            TemplateContent savedContent = templateContentRepository.save(content);

            // Notify reviewers
            contentApprovalService.notifyReviewers(savedContent);

            log.info("Content submitted for review successfully: id={}", savedContent.getId());
            return savedContent;

        } catch (Exception e) {
            log.error("Error submitting content for review: id={}, error={}", contentId, e.getMessage(), e);
            throw new ContentManagementException("Failed to submit content for review: " + e.getMessage(), e);
        }
    }

    /**
     * Approve content
     */
    @Transactional
    public TemplateContent approveContent(Long contentId, Long approvedBy, String comments) {
        log.info("Approving content: id={}, approvedBy={}", contentId, approvedBy);

        try {
            TemplateContent content = getContentById(contentId);

            if (!content.getStatus().canApprove()) {
                throw new ContentManagementException("Content cannot be approved in current status: " + content.getStatus());
            }

            content.approve(approvedBy);
            content.setUpdatedAt(LocalDateTime.now());

            if (comments != null) {
                content.setMetadata("approval_comments", comments);
            }

            TemplateContent savedContent = templateContentRepository.save(content);

            // Notify content creator
            contentApprovalService.notifyApproval(savedContent, comments);

            log.info("Content approved successfully: id={}", savedContent.getId());
            return savedContent;

        } catch (Exception e) {
            log.error("Error approving content: id={}, error={}", contentId, e.getMessage(), e);
            throw new ContentManagementException("Failed to approve content: " + e.getMessage(), e);
        }
    }

    /**
     * Reject content
     */
    @Transactional
    public TemplateContent rejectContent(Long contentId, Long rejectedBy, String reason) {
        log.info("Rejecting content: id={}, rejectedBy={}, reason={}", contentId, rejectedBy, reason);

        try {
            TemplateContent content = getContentById(contentId);

            if (!content.getStatus().canReject()) {
                throw new ContentManagementException("Content cannot be rejected in current status: " + content.getStatus());
            }

            content.reject(rejectedBy, reason);
            content.setUpdatedAt(LocalDateTime.now());

            TemplateContent savedContent = templateContentRepository.save(content);

            // Notify content creator
            contentApprovalService.notifyRejection(savedContent, reason);

            log.info("Content rejected successfully: id={}", savedContent.getId());
            return savedContent;

        } catch (Exception e) {
            log.error("Error rejecting content: id={}, error={}", contentId, e.getMessage(), e);
            throw new ContentManagementException("Failed to reject content: " + e.getMessage(), e);
        }
    }

    /**
     * Publish content
     */
    @Transactional
    public TemplateContent publishContent(Long contentId) {
        log.info("Publishing content: id={}", contentId);

        try {
            TemplateContent content = getContentById(contentId);

            if (!content.getStatus().canPublish()) {
                throw new ContentManagementException("Content cannot be published in current status: " + content.getStatus());
            }

            content.publish();
            content.setUpdatedAt(LocalDateTime.now());

            TemplateContent savedContent = templateContentRepository.save(content);

            log.info("Content published successfully: id={}", savedContent.getId());
            return savedContent;

        } catch (Exception e) {
            log.error("Error publishing content: id={}, error={}", contentId, e.getMessage(), e);
            throw new ContentManagementException("Failed to publish content: " + e.getMessage(), e);
        }
    }

    /**
     * Archive content
     */
    @Transactional
    public TemplateContent archiveContent(Long contentId) {
        log.info("Archiving content: id={}", contentId);

        try {
            TemplateContent content = getContentById(contentId);

            if (!content.getStatus().canArchive()) {
                throw new ContentManagementException("Content cannot be archived in current status: " + content.getStatus());
            }

            content.archive();
            content.setUpdatedAt(LocalDateTime.now());

            TemplateContent savedContent = templateContentRepository.save(content);

            log.info("Content archived successfully: id={}", savedContent.getId());
            return savedContent;

        } catch (Exception e) {
            log.error("Error archiving content: id={}, error={}", contentId, e.getMessage(), e);
            throw new ContentManagementException("Failed to archive content: " + e.getMessage(), e);
        }
    }

    /**
     * Upload media attachment
     */
    @Transactional
    public TemplateContent.MediaAttachment uploadMedia(Long contentId, MultipartFile file, String altText) {
        log.info("Uploading media for content: id={}, filename={}", contentId, file.getOriginalFilename());

        try {
            TemplateContent content = getContentById(contentId);

            // Upload file to storage
            String mediaUrl = mediaStorageService.uploadFile(file);

            // Create media attachment
            TemplateContent.MediaAttachment attachment = TemplateContent.MediaAttachment.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .type(getMediaType(file.getContentType()))
                    .url(mediaUrl)
                    .filename(file.getOriginalFilename())
                    .mimeType(file.getContentType())
                    .size(file.getSize())
                    .altText(altText)
                    .build();

            // Add to content
            content.addMediaAttachment(attachment);
            content.setUpdatedAt(LocalDateTime.now());

            templateContentRepository.save(content);

            log.info("Media uploaded successfully: contentId={}, mediaId={}", contentId, attachment.getId());
            return attachment;

        } catch (Exception e) {
            log.error("Error uploading media: contentId={}, error={}", contentId, e.getMessage(), e);
            throw new ContentManagementException("Failed to upload media: " + e.getMessage(), e);
        }
    }

    /**
     * Create new version of content
     */
    @Transactional
    public TemplateContent createNewVersion(Long contentId) {
        log.info("Creating new version of content: id={}", contentId);

        try {
            TemplateContent existingContent = getContentById(contentId);
            TemplateContent newVersion = existingContent.createNewVersion();

            TemplateContent savedContent = templateContentRepository.save(newVersion);

            log.info("New version created successfully: originalId={}, newId={}, version={}", 
                    contentId, savedContent.getId(), savedContent.getVersion());
            return savedContent;

        } catch (Exception e) {
            log.error("Error creating new version: id={}, error={}", contentId, e.getMessage(), e);
            throw new ContentManagementException("Failed to create new version: " + e.getMessage(), e);
        }
    }

    /**
     * Process rich text content
     */
    private TemplateContent processRichTextContent(TemplateContent content) {
        if (content.getContent() != null) {
            // Process embedded media references
            content.setContent(processMediaReferences(content.getContent()));
            
            // Generate HTML version if not provided
            if (content.getHtmlContent() == null) {
                content.setHtmlContent(convertToHtml(content.getContent()));
            }
        }
        return content;
    }

    /**
     * Process media references in content
     */
    private String processMediaReferences(String content) {
        // Process media references like {{media:id}} and replace with actual URLs
        // This is a simplified implementation
        return content;
    }

    /**
     * Convert content to HTML
     */
    private String convertToHtml(String content) {
        // Convert rich text content to HTML
        // This would typically use a rich text processor
        return content;
    }

    /**
     * Strip HTML tags
     */
    private String stripHtmlTags(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * Get media type from MIME type
     */
    private String getMediaType(String mimeType) {
        if (mimeType == null) return "UNKNOWN";
        
        if (mimeType.startsWith("image/")) return "IMAGE";
        if (mimeType.startsWith("video/")) return "VIDEO";
        if (mimeType.startsWith("audio/")) return "AUDIO";
        return "DOCUMENT";
    }

    /**
     * Get next version number
     */
    private Integer getNextVersion(Long templateId, String languageCode) {
        Optional<Integer> maxVersion = templateContentRepository.findMaxVersionByTemplateIdAndLanguageCode(templateId, languageCode);
        return maxVersion.orElse(0) + 1;
    }

    /**
     * Content management exception
     */
    public static class ContentManagementException extends RuntimeException {
        public ContentManagementException(String message) {
            super(message);
        }

        public ContentManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
