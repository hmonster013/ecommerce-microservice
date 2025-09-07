package org.de013.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for notification operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Standard response for notification operations")
public class NotificationResponse {

    @Schema(description = "Operation success status", example = "true")
    private boolean success;

    @Schema(description = "Response message", example = "Email sent successfully")
    private String message;

    @Schema(description = "Notification ID", example = "123")
    private Long notificationId;

    @Schema(description = "Notification status", example = "SENT", allowableValues = {"PENDING", "SENT", "FAILED", "READ"})
    private String status;

    @Schema(description = "Timestamp when the response was created", example = "2023-12-07T10:30:00")
    private LocalDateTime timestamp;

    public static NotificationResponse success(String message, Long notificationId, String status) {
        return NotificationResponse.builder()
                .success(true)
                .message(message)
                .notificationId(notificationId)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static NotificationResponse error(String message) {
        return NotificationResponse.builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
