package org.de013.notificationservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.entity.enums.Priority;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request DTO for creating a notification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Request to create a new notification")
public class CreateNotificationRequest {

    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    @Schema(description = "User ID to send notification to", example = "123", required = true)
    private Long userId;

    @NotNull(message = "Notification type is required")
    @Schema(description = "Type of notification", example = "ORDER_PLACED", required = true)
    private NotificationType type;

    @NotNull(message = "Notification channel is required")
    @Schema(description = "Channel to send notification through", example = "EMAIL", required = true)
    private NotificationChannel channel;

    @Schema(description = "Notification priority", example = "NORMAL")
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    @Size(max = 500, message = "Subject cannot exceed 500 characters")
    @Schema(description = "Notification subject", example = "Your order has been placed")
    private String subject;

    @Size(max = 10000, message = "Content cannot exceed 10000 characters")
    @Schema(description = "Notification content", example = "Thank you for your order...")
    private String content;

    @Size(max = 20000, message = "HTML content cannot exceed 20000 characters")
    @Schema(description = "HTML content for rich notifications")
    private String htmlContent;

    @NotBlank(message = "Recipient address is required")
    @Size(max = 500, message = "Recipient address cannot exceed 500 characters")
    @Schema(description = "Recipient address", example = "user@example.com", required = true)
    private String recipientAddress;

    @Size(max = 500, message = "Sender address cannot exceed 500 characters")
    @Schema(description = "Sender address", example = "noreply@company.com")
    private String senderAddress;

    @Positive(message = "Template ID must be positive")
    @Schema(description = "Template ID to use for rendering", example = "5")
    private Long templateId;

    @Schema(description = "Template variables for rendering")
    private Map<String, Object> templateVariables;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Future(message = "Scheduled time must be in the future")
    @Schema(description = "Scheduled delivery time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledAt;

    @Future(message = "Expiration time must be in the future")
    @Schema(description = "Expiration time for the notification")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    @Min(value = 1, message = "Max retry attempts must be at least 1")
    @Max(value = 10, message = "Max retry attempts cannot exceed 10")
    @Schema(description = "Maximum retry attempts", example = "3")
    @Builder.Default
    private Integer maxRetryAttempts = 3;

    @Size(max = 255, message = "Correlation ID cannot exceed 255 characters")
    @Schema(description = "Correlation ID for tracking", example = "order-123-notification")
    private String correlationId;

    @Size(max = 50, message = "Reference type cannot exceed 50 characters")
    @Schema(description = "Reference type", example = "ORDER")
    private String referenceType;

    @Size(max = 255, message = "Reference ID cannot exceed 255 characters")
    @Schema(description = "Reference ID", example = "ORD-123")
    private String referenceId;

    // Validation methods

    /**
     * Validate that either content or template is provided
     */
    public boolean hasContentOrTemplate() {
        return (content != null && !content.trim().isEmpty()) || templateId != null;
    }

    /**
     * Validate email address format if channel is EMAIL
     */
    public boolean isValidEmailAddress() {
        if (channel != NotificationChannel.EMAIL) {
            return true;
        }
        return recipientAddress != null && 
               recipientAddress.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    }

    /**
     * Validate phone number format if channel is SMS
     */
    public boolean isValidPhoneNumber() {
        if (channel != NotificationChannel.SMS) {
            return true;
        }
        return recipientAddress != null && 
               recipientAddress.matches("^\\+?[1-9]\\d{1,14}$");
    }

    /**
     * Validate scheduled time is before expiration time
     */
    public boolean isValidScheduleAndExpiration() {
        if (scheduledAt == null || expiresAt == null) {
            return true;
        }
        return scheduledAt.isBefore(expiresAt);
    }
}
