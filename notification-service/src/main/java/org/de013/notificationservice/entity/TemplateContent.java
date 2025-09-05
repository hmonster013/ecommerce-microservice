package org.de013.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.de013.notificationservice.entity.BaseEntity;
import org.de013.notificationservice.entity.enums.ContentStatus;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Template Content Entity for rich content management
 */
@Entity
@Table(name = "template_contents", indexes = {
    @Index(name = "idx_template_content_template_id", columnList = "template_id"),
    @Index(name = "idx_template_content_language", columnList = "language_code"),
    @Index(name = "idx_template_content_status", columnList = "status"),
    @Index(name = "idx_template_content_version", columnList = "template_id, version")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
public class TemplateContent extends BaseEntity {

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "language_code", length = 10, nullable = false)
    private String languageCode; // e.g., "en", "vi", "fr"

    @Column(name = "country_code", length = 10)
    private String countryCode; // e.g., "US", "VN", "FR"

    @Column(name = "content_version", nullable = false)
    @Builder.Default
    private Integer contentVersion = 1;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "subject", length = 1000)
    private String subject;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content; // Rich text content

    @Column(name = "plain_text_content", columnDefinition = "TEXT")
    private String plainTextContent; // Plain text version

    @Column(name = "html_content", columnDefinition = "TEXT")
    private String htmlContent; // HTML version

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ContentStatus status = ContentStatus.DRAFT;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false; // Default content for this language

    @Column(name = "is_fallback", nullable = false)
    @Builder.Default
    private Boolean isFallback = false; // Fallback content

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media_attachments", columnDefinition = "jsonb")
    @Builder.Default
    private List<MediaAttachment> mediaAttachments = new java.util.ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_blocks", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, ContentBlock> contentBlocks = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // Approval workflow fields

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_by")
    private Long rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // SEO and accessibility
    @Column(name = "alt_text", length = 500)
    private String altText;

    @Column(name = "meta_description", length = 1000)
    private String metaDescription;

    @Column(name = "keywords", length = 1000)
    private String keywords;

    // A/B testing
    @Column(name = "ab_test_group")
    private String abTestGroup;

    @Column(name = "ab_test_weight")
    @Builder.Default
    private Integer abTestWeight = 100; // Weight for A/B testing (0-100)

    /**
     * Media Attachment DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MediaAttachment {
        private String id;
        private String type; // IMAGE, VIDEO, AUDIO, DOCUMENT
        private String url;
        private String filename;
        private String mimeType;
        private Long size;
        private Integer width;
        private Integer height;
        private String altText;
        private String caption;
        private Map<String, String> metadata;
    }

    /**
     * Content Block DTO for structured content
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentBlock {
        private String type; // TEXT, IMAGE, BUTTON, DIVIDER, SPACER
        private String content;
        private Map<String, Object> properties;
        private Map<String, String> styles;
        private Integer order;
        private Boolean visible;
    }

    /**
     * Check if content is approved
     */
    public boolean isApproved() {
        return status == ContentStatus.APPROVED;
    }

    /**
     * Check if content is published
     */
    public boolean isPublished() {
        return status == ContentStatus.PUBLISHED;
    }

    /**
     * Check if content is expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    /**
     * Check if content is active (published and not expired)
     */
    public boolean isActive() {
        return isPublished() && !isExpired();
    }

    /**
     * Get full locale string
     */
    public String getFullLocale() {
        if (countryCode != null && !countryCode.isEmpty()) {
            return languageCode + "_" + countryCode;
        }
        return languageCode;
    }

    /**
     * Get content for specific channel
     */
    public String getContentForChannel(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> htmlContent != null ? htmlContent : content;
            case SMS -> plainTextContent != null ? plainTextContent : stripHtml(content);
            case PUSH -> plainTextContent != null ? plainTextContent : stripHtml(content);
            case IN_APP -> content;
            default -> content;
        };
    }

    /**
     * Strip HTML tags from content
     */
    private String stripHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * Add media attachment
     */
    public void addMediaAttachment(MediaAttachment attachment) {
        if (mediaAttachments == null) {
            mediaAttachments = new java.util.ArrayList<>();
        }
        mediaAttachments.add(attachment);
    }

    /**
     * Add content block
     */
    public void addContentBlock(String key, ContentBlock block) {
        if (contentBlocks == null) {
            contentBlocks = new HashMap<>();
        }
        contentBlocks.put(key, block);
    }

    /**
     * Get content block by key
     */
    public ContentBlock getContentBlock(String key) {
        return contentBlocks != null ? contentBlocks.get(key) : null;
    }

    /**
     * Set variable
     */
    public void setVariable(String key, Object value) {
        if (variables == null) {
            variables = new HashMap<>();
        }
        variables.put(key, value);
    }

    /**
     * Get variable
     */
    public Object getVariable(String key) {
        return variables != null ? variables.get(key) : null;
    }

    /**
     * Set metadata
     */
    public void setMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    /**
     * Get metadata
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }

    /**
     * Approve content
     */
    public void approve(Long approvedBy) {
        this.status = ContentStatus.APPROVED;
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
        this.rejectedBy = null;
        this.rejectedAt = null;
        this.rejectionReason = null;
    }

    /**
     * Reject content
     */
    public void reject(Long rejectedBy, String reason) {
        this.status = ContentStatus.REJECTED;
        this.rejectedBy = rejectedBy;
        this.rejectedAt = LocalDateTime.now();
        this.rejectionReason = reason;
        this.approvedBy = null;
        this.approvedAt = null;
    }

    /**
     * Publish content
     */
    public void publish() {
        if (!isApproved()) {
            throw new IllegalStateException("Content must be approved before publishing");
        }
        this.status = ContentStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    /**
     * Archive content
     */
    public void archive() {
        this.status = ContentStatus.ARCHIVED;
    }

    /**
     * Create new version
     */
    public TemplateContent createNewVersion() {
        return TemplateContent.builder()
                .templateId(this.templateId)
                .languageCode(this.languageCode)
                .countryCode(this.countryCode)
                .contentVersion(this.contentVersion + 1)
                .title(this.title)
                .subject(this.subject)
                .content(this.content)
                .plainTextContent(this.plainTextContent)
                .htmlContent(this.htmlContent)
                .status(ContentStatus.DRAFT)
                .isDefault(this.isDefault)
                .isFallback(this.isFallback)
                .mediaAttachments(new java.util.ArrayList<>(this.mediaAttachments))
                .contentBlocks(new HashMap<>(this.contentBlocks))
                .variables(new HashMap<>(this.variables))
                .metadata(new HashMap<>(this.metadata))
                .createdBy(this.getCreatedBy())
                .altText(this.altText)
                .metaDescription(this.metaDescription)
                .keywords(this.keywords)
                .abTestGroup(this.abTestGroup)
                .abTestWeight(this.abTestWeight)
                .build();
    }
}
