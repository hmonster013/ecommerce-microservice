package org.de013.notificationservice.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * User event DTO for notification processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {

    private Long userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private UserStatus status;
    private String activationToken;
    private String resetToken;
    private Map<String, Object> preferences;
    private Map<String, Object> metadata;
    private String eventType; // USER_REGISTERED, USER_ACTIVATED, PASSWORD_RESET_REQUESTED, PROFILE_UPDATED
    private String correlationId;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registrationDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTimestamp;

    public enum UserStatus {
        PENDING_ACTIVATION,
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        DELETED
    }

    /**
     * Get notification template variables
     */
    public Map<String, Object> getTemplateVariables() {
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("userId", userId);
        variables.put("username", username);
        variables.put("email", email);
        variables.put("firstName", firstName != null ? firstName : "");
        variables.put("lastName", lastName != null ? lastName : "");
        variables.put("fullName", getFullName());
        variables.put("registrationDate", registrationDate);
        variables.put("activationToken", activationToken != null ? activationToken : "");
        variables.put("resetToken", resetToken != null ? resetToken : "");
        variables.put("activationLink", generateActivationLink());
        variables.put("resetLink", generateResetLink());
        return variables;
    }

    /**
     * Get notification priority based on event type
     */
    public String getNotificationPriority() {
        return switch (eventType) {
            case "PASSWORD_RESET_REQUESTED" -> "HIGH";
            case "USER_REGISTERED" -> "NORMAL";
            case "USER_ACTIVATED" -> "NORMAL";
            default -> "LOW";
        };
    }

    /**
     * Get notification type based on event
     */
    public String getNotificationType() {
        return switch (eventType) {
            case "USER_REGISTERED" -> "WELCOME";
            case "USER_ACTIVATED" -> "ACCOUNT_ACTIVATED";
            case "PASSWORD_RESET_REQUESTED" -> "PASSWORD_RESET";
            case "PROFILE_UPDATED" -> "PROFILE_UPDATE";
            default -> "USER_UPDATE";
        };
    }

    /**
     * Get full name
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return username;
        }
    }

    /**
     * Generate activation link
     */
    private String generateActivationLink() {
        if (activationToken != null) {
            return "https://ecommerce.com/activate?token=" + activationToken;
        }
        return "";
    }

    /**
     * Generate password reset link
     */
    private String generateResetLink() {
        if (resetToken != null) {
            return "https://ecommerce.com/reset-password?token=" + resetToken;
        }
        return "";
    }

    /**
     * Check if notification should be sent
     */
    public boolean shouldSendNotification() {
        // Send notifications for all user events except deleted users
        return status != UserStatus.DELETED;
    }

    /**
     * Get preferred notification channel based on event type
     */
    public String getPreferredChannel() {
        return switch (eventType) {
            case "USER_REGISTERED", "PASSWORD_RESET_REQUESTED" -> "EMAIL";
            case "USER_ACTIVATED" -> "EMAIL";
            default -> "EMAIL";
        };
    }
}
