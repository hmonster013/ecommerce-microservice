package org.de013.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sending SMS notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to send an SMS notification")
public class SmsNotificationRequest {

    @Schema(description = "User ID (optional, for tracking purposes)", 
            example = "123", 
            nullable = true)
    private Long userId;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    @Schema(description = "Recipient phone number (E.164 format recommended)", 
            example = "+1234567890", 
            required = true,
            pattern = "^\\+?[1-9]\\d{1,14}$")
    private String phoneNumber;

    @NotBlank(message = "Message content is required")
    @Size(max = 160, message = "SMS message must not exceed 160 characters")
    @Schema(description = "SMS message content", 
            example = "Your verification code is: 123456", 
            required = true,
            maxLength = 160)
    private String message;
}
