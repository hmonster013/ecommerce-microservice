package org.de013.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Response DTO for both email and SMS notification operations
 */
@Schema(description = "Response for sending both email and SMS notifications")
public record BothNotificationResponse(

    @Schema(description = "Operation success status", example = "true")
    boolean success,

    @Schema(description = "Response message", example = "Both notifications sent successfully")
    String message,

    @Schema(description = "Email notification ID", example = "123")
    Long emailNotificationId,

    @Schema(description = "SMS notification ID", example = "124")
    Long smsNotificationId,

    @Schema(description = "Email notification status", example = "SENT", allowableValues = {"PENDING", "SENT", "FAILED", "READ"})
    String emailStatus,

    @Schema(description = "SMS notification status", example = "SENT", allowableValues = {"PENDING", "SENT", "FAILED", "READ"})
    String smsStatus,

    @Schema(description = "Timestamp when the response was created", example = "2023-12-07T10:30:00")
    LocalDateTime timestamp
) {

    public static BothNotificationResponse success(String message,
                                                    Long emailNotificationId, String emailStatus,
                                                    Long smsNotificationId, String smsStatus) {
        return new BothNotificationResponse(
                true,
                message,
                emailNotificationId,
                smsNotificationId,
                emailStatus,
                smsStatus,
                LocalDateTime.now()
        );
    }

    public static BothNotificationResponse error(String message) {
        return new BothNotificationResponse(false, message, null, null, null, null, LocalDateTime.now());
    }
}
