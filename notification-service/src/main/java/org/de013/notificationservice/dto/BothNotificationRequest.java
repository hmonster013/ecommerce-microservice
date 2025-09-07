package org.de013.notificationservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sending both email and SMS notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to send both email and SMS notifications")
public class BothNotificationRequest {

    @Schema(description = "User ID (optional, for tracking purposes)", 
            example = "123", 
            nullable = true)
    private Long userId;

    @NotBlank(message = "Email address is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Recipient email address", 
            example = "user@example.com", 
            required = true)
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    @Schema(description = "Recipient phone number (E.164 format recommended)", 
            example = "+1234567890", 
            required = true,
            pattern = "^\\+?[1-9]\\d{1,14}$")
    private String phoneNumber;

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject must not exceed 200 characters")
    @Schema(description = "Email subject", 
            example = "Account Verification", 
            required = true,
            maxLength = 200)
    private String subject;

    @NotBlank(message = "Message content is required")
    @Size(max = 160, message = "Message must not exceed 160 characters for SMS compatibility")
    @Schema(description = "Message content (will be used for both email body and SMS text)", 
            example = "Your verification code is: 123456. Please enter this code to verify your account.", 
            required = true,
            maxLength = 160)
    private String message;
}
