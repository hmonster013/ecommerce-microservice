package org.de013.notificationservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Notification Template Data Transfer Object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Notification template information")
public class NotificationTemplateDto {

    @Schema(description = "Template ID", example = "1")
    private Long id;

    @Schema(description = "Template name", example = "order-confirmation")
    private String name;

    @Schema(description = "Template display name", example = "Order Confirmation")
    private String displayName;

    @Schema(description = "Template description", example = "Template for order confirmation notifications")
    private String description;

    @Schema(description = "Notification type", example = "ORDER_PLACED")
    private NotificationType type;

    @Schema(description = "Notification channel", example = "EMAIL")
    private NotificationChannel channel;

    @Schema(description = "Template language", example = "en")
    private String language;

    @Schema(description = "Subject template", example = "Order #{orderNumber} Confirmation")
    private String subjectTemplate;

    @Schema(description = "Body template", example = "Dear {customerName}, your order has been confirmed...")
    private String bodyTemplate;

    @Schema(description = "HTML template for rich content")
    private String htmlTemplate;

    @Schema(description = "Template variables definition")
    private Map<String, Object> variables;

    @Schema(description = "Default values for variables")
    private Map<String, Object> defaultValues;

    @Schema(description = "Validation rules for variables")
    private Map<String, Object> validationRules;

    @Schema(description = "Whether template is active", example = "true")
    private Boolean active;

    @Schema(description = "Template version", example = "1")
    private Integer templateVersion;

    @Schema(description = "Parent template ID for versioning", example = "5")
    private Long parentTemplateId;

    @Schema(description = "Default sender name", example = "Company Name")
    private String senderName;

    @Schema(description = "Default sender email", example = "noreply@company.com")
    private String senderEmail;

    @Schema(description = "Reply-to address", example = "support@company.com")
    private String replyTo;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Template tags", example = "order,confirmation,email")
    private String tags;

    @Schema(description = "Template category", example = "order")
    private String category;

    @Schema(description = "Creation timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @Schema(description = "Created by user")
    private String createdBy;

    @Schema(description = "Updated by user")
    private String updatedBy;

    // Helper methods

    /**
     * Check if template is usable
     */
    public boolean isUsable() {
        return Boolean.TRUE.equals(active);
    }

    /**
     * Check if template supports HTML content
     */
    public boolean supportsHtml() {
        return channel != null && channel.supportsRichContent() && 
               htmlTemplate != null && !htmlTemplate.trim().isEmpty();
    }

    /**
     * Check if template has required variables
     */
    public boolean hasRequiredVariables() {
        return variables != null && !variables.isEmpty();
    }

    /**
     * Get template key for caching
     */
    public String getTemplateKey() {
        return String.format("%s_%s_%s", name, 
                            channel != null ? channel.getCode() : "unknown", 
                            language != null ? language : "en");
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
}
