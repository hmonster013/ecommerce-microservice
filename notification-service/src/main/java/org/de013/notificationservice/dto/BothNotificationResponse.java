package org.de013.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for both email and SMS notification operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for sending both email and SMS notifications")
public class BothNotificationResponse {

    @Schema(description = "Operation success status", example = "true")
    private boolean success;

    @Schema(description = "Response message", example = "Both notifications sent successfully")
    private String message;

    @Schema(description = "Email notification ID", example = "123")
    private Long emailNotificationId;

    @Schema(description = "SMS notification ID", example = "124")
    private Long smsNotificationId;

    @Schema(description = "Email notification status", example = "SENT", allowableValues = {"PENDING", "SENT", "FAILED", "READ"})
    private String emailStatus;

    @Schema(description = "SMS notification status", example = "SENT", allowableValues = {"PENDING", "SENT", "FAILED", "READ"})
    private String smsStatus;

    @Schema(description = "Timestamp when the response was created", example = "2023-12-07T10:30:00")
    private LocalDateTime timestamp;

    public static BothNotificationResponse success(String message, 
                                                  Long emailNotificationId, String emailStatus,
                                                  Long smsNotificationId, String smsStatus) {
        return BothNotificationResponse.builder()
                .success(true)
                .message(message)
                .emailNotificationId(emailNotificationId)
                .emailStatus(emailStatus)
                .smsNotificationId(smsNotificationId)
                .smsStatus(smsStatus)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static BothNotificationResponse error(String message) {
        return BothNotificationResponse.builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
