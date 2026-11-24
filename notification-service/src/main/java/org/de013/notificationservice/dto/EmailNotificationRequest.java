package org.de013.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for sending email notifications
 */
@Schema(description = "Request to send an email notification")
public record EmailNotificationRequest(

    @Schema(description = "User ID (optional, for tracking purposes)",
            example = "123",
            nullable = true)
    String userId,

    @NotBlank(message = "Email address is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Recipient email address",
            example = "user@example.com",
            required = true)
    String to,

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject must not exceed 200 characters")
    @Schema(description = "Email subject",
            example = "Welcome to our platform!",
            required = true,
            maxLength = 200)
    String subject,

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message must not exceed 5000 characters")
    @Schema(description = "Email message content",
            example = "Thank you for joining our platform. We're excited to have you!",
            required = true,
            maxLength = 5000)
    String message
) {
}

