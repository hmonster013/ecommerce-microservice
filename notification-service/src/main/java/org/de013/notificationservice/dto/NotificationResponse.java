package org.de013.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Response DTO for notification operations
 */
@Schema(description = "Standard response for notification operations")
public record NotificationResponse(

    @Schema(description = "Operation success status", example = "true")
    boolean success,

    @Schema(description = "Response message", example = "Email sent successfully")
    String message,

    @Schema(description = "Notification ID", example = "123")
    Long notificationId,

    @Schema(description = "Notification status", example = "SENT", allowableValues = {"PENDING", "SENT", "FAILED", "READ"})
    String status,

    @Schema(description = "Timestamp when the response was created", example = "2023-12-07T10:30:00")
    LocalDateTime timestamp
) {

    public static NotificationResponse success(String message, Long notificationId, String status) {
        return new NotificationResponse(true, message, notificationId, status, LocalDateTime.now());
    }

    public static NotificationResponse error(String message) {
        return new NotificationResponse(false, message, null, null, LocalDateTime.now());
    }
}
