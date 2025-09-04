package org.de013.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notification Template Entity
 * Represents a template for creating notifications with variable substitution
 */
@Entity
@Table(name = "notification_templates", indexes = {
    @Index(name = "idx_template_name", columnList = "name"),
    @Index(name = "idx_template_type", columnList = "type"),
    @Index(name = "idx_template_channel", columnList = "channel"),
    @Index(name = "idx_template_language", columnList = "language"),
    @Index(name = "idx_template_active", columnList = "active"),
    @Index(name = "idx_template_version", columnList = "version")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_template_name_channel_language", 
                     columnNames = {"name", "channel", "language"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
public class NotificationTemplate extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "language", nullable = false, length = 10)
    @Builder.Default
    private String language = "en";

    @Column(name = "subject_template", length = 500)
    private String subjectTemplate;

    @Column(name = "body_template", columnDefinition = "TEXT", nullable = false)
    private String bodyTemplate;

    @Column(name = "html_template", columnDefinition = "TEXT")
    private String htmlTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_values", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> defaultValues = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_rules", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> validationRules = new HashMap<>();

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "template_version", nullable = false)
    @Builder.Default
    private Integer templateVersion = 1;

    @Column(name = "parent_template_id")
    private Long parentTemplateId;

    @Column(name = "sender_name", length = 200)
    private String senderName;

    @Column(name = "sender_email", length = 200)
    private String senderEmail;

    @Column(name = "reply_to", length = 200)
    private String replyTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "category", length = 100)
    private String category;

    @OneToMany(mappedBy = "templateId", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Notification> notifications = new ArrayList<>();

    // Business methods

    /**
     * Check if template is active and can be used
     */
    public boolean isUsable() {
        return Boolean.TRUE.equals(active) && !isDeleted();
    }

    /**
     * Check if template supports HTML content
     */
    public boolean supportsHtml() {
        return channel.supportsRichContent() && htmlTemplate != null && !htmlTemplate.trim().isEmpty();
    }

    /**
     * Check if template has required variables
     */
    public boolean hasRequiredVariables() {
        return variables != null && !variables.isEmpty();
    }

    /**
     * Get all variable names used in templates
     */
    public List<String> getVariableNames() {
        List<String> variableNames = new ArrayList<>();
        if (variables != null) {
            variableNames.addAll(variables.keySet());
        }
        return variableNames;
    }

    /**
     * Check if template supports the given channel
     */
    public boolean supportsChannel(NotificationChannel targetChannel) {
        return this.channel == targetChannel;
    }

    /**
     * Check if template is for the given type
     */
    public boolean isForType(NotificationType targetType) {
        return this.type == targetType;
    }

    /**
     * Get template key for caching
     */
    public String getTemplateKey() {
        return String.format("%s_%s_%s", name, channel.getCode(), language);
    }

    /**
     * Create a new version of this template
     */
    public NotificationTemplate createNewVersion() {
        return NotificationTemplate.builder()
                .name(this.name)
                .displayName(this.displayName)
                .description(this.description)
                .type(this.type)
                .channel(this.channel)
                .language(this.language)
                .subjectTemplate(this.subjectTemplate)
                .bodyTemplate(this.bodyTemplate)
                .htmlTemplate(this.htmlTemplate)
                .variables(new HashMap<>(this.variables))
                .defaultValues(new HashMap<>(this.defaultValues))
                .validationRules(new HashMap<>(this.validationRules))
                .active(false) // New version starts as inactive
                .templateVersion(this.templateVersion + 1)
                .parentTemplateId(this.getId())
                .senderName(this.senderName)
                .senderEmail(this.senderEmail)
                .replyTo(this.replyTo)
                .metadata(new HashMap<>(this.metadata))
                .tags(this.tags)
                .category(this.category)
                .build();
    }

    /**
     * Activate this template version and deactivate others
     */
    public void activate() {
        this.active = true;
    }

    /**
     * Deactivate this template version
     */
    public void deactivate() {
        this.active = false;
    }
}
