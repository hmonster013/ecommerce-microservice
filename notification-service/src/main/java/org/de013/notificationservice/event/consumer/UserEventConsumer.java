package org.de013.notificationservice.event.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.dto.CreateNotificationRequest;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.entity.enums.Priority;
import org.de013.notificationservice.event.dto.UserEvent;
import org.de013.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumer for user events to trigger notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final NotificationService notificationService;

    /**
     * Handle user events
     */
    @RabbitListener(
        queues = "notification.user.events",
        containerFactory = "eventRabbitListenerContainerFactory"
    )
    public void handleUserEvent(@Payload UserEvent userEvent, @Header Map<String, Object> headers) {
        log.info("Received user event: type={}, userId={}, email={}", 
                userEvent.getEventType(), userEvent.getUserId(), userEvent.getEmail());

        try {
            // Only process events that should trigger notifications
            if (!userEvent.shouldSendNotification()) {
                log.debug("Skipping notification for user event: type={}, status={}", 
                        userEvent.getEventType(), userEvent.getStatus());
                return;
            }

            switch (userEvent.getEventType()) {
                case "USER_REGISTERED" -> handleUserRegistered(userEvent);
                case "USER_ACTIVATED" -> handleUserActivated(userEvent);
                case "PASSWORD_RESET_REQUESTED" -> handlePasswordResetRequested(userEvent);
                case "PROFILE_UPDATED" -> handleProfileUpdated(userEvent);
                default -> log.warn("Unknown user event type: {}", userEvent.getEventType());
            }
            
            log.info("Successfully processed user event: type={}, userId={}", 
                    userEvent.getEventType(), userEvent.getUserId());
            
        } catch (Exception e) {
            log.error("Error processing user event: type={}, userId={}, error={}", 
                    userEvent.getEventType(), userEvent.getUserId(), e.getMessage(), e);
            throw e; // Re-throw to trigger retry or DLQ
        }
    }

    /**
     * Handle user registered event - send welcome email
     */
    private void handleUserRegistered(UserEvent userEvent) {
        log.info("Processing user registered event: userId={}, email={}", 
                userEvent.getUserId(), userEvent.getEmail());

        // Send welcome email with activation link
        CreateNotificationRequest welcomeEmailRequest = CreateNotificationRequest.builder()
                .userId(userEvent.getUserId())
                .type(NotificationType.WELCOME)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.NORMAL)
                .recipientAddress(userEvent.getEmail())
                .subject("Welcome to E-Commerce Platform!")
                .templateId(getTemplateId("WELCOME_EMAIL"))
                .templateVariables(userEvent.getTemplateVariables())
                .correlationId(userEvent.getCorrelationId())
                .referenceType("USER")
                .referenceId(userEvent.getUserId().toString())
                .build();

        notificationService.createNotification(welcomeEmailRequest);

        // Send welcome in-app notification
        CreateNotificationRequest inAppRequest = CreateNotificationRequest.builder()
                .userId(userEvent.getUserId())
                .type(NotificationType.WELCOME)
                .channel(NotificationChannel.IN_APP)
                .priority(Priority.NORMAL)
                .subject("Welcome!")
                .content("Welcome to E-Commerce Platform, " + userEvent.getFullName() + 
                        "! Please check your email to activate your account.")
                .correlationId(userEvent.getCorrelationId())
                .referenceType("USER")
                .referenceId(userEvent.getUserId().toString())
                .build();

        notificationService.createNotification(inAppRequest);

        // Schedule follow-up email if account is not activated within 24 hours
        CreateNotificationRequest followUpRequest = CreateNotificationRequest.builder()
                .userId(userEvent.getUserId())
                .type(NotificationType.ACCOUNT_ACTIVATION_REMINDER)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.LOW)
                .recipientAddress(userEvent.getEmail())
                .subject("Don't forget to activate your account")
                .templateId(getTemplateId("ACTIVATION_REMINDER_EMAIL"))
                .templateVariables(userEvent.getTemplateVariables())
                .correlationId(userEvent.getCorrelationId())
                .referenceType("USER")
                .referenceId(userEvent.getUserId().toString())
                .scheduledAt(java.time.LocalDateTime.now().plusHours(24))
                .build();

        notificationService.createNotification(followUpRequest);
    }

    /**
     * Handle user activated event
     */
    private void handleUserActivated(UserEvent userEvent) {
        log.info("Processing user activated event: userId={}", userEvent.getUserId());

        // Send account activation confirmation
        CreateNotificationRequest emailRequest = CreateNotificationRequest.builder()
                .userId(userEvent.getUserId())
                .type(NotificationType.ACCOUNT_ACTIVATED)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.NORMAL)
                .recipientAddress(userEvent.getEmail())
                .subject("Account Activated Successfully")
                .templateId(getTemplateId("ACCOUNT_ACTIVATED_EMAIL"))
                .templateVariables(userEvent.getTemplateVariables())
                .correlationId(userEvent.getCorrelationId())
                .referenceType("USER")
                .referenceId(userEvent.getUserId().toString())
                .build();

        notificationService.createNotification(emailRequest);

        // Send in-app notification
        CreateNotificationRequest inAppRequest = CreateNotificationRequest.builder()
                .userId(userEvent.getUserId())
                .type(NotificationType.ACCOUNT_ACTIVATED)
                .channel(NotificationChannel.IN_APP)
                .priority(Priority.NORMAL)
                .subject("Account Activated")
                .content("Your account has been successfully activated. You can now enjoy all features of our platform!")
                .correlationId(userEvent.getCorrelationId())
                .referenceType("USER")
                .referenceId(userEvent.getUserId().toString())
                .build();

        notificationService.createNotification(inAppRequest);

        // Send getting started guide after 1 hour
        CreateNotificationRequest gettingStartedRequest = CreateNotificationRequest.builder()
                .userId(userEvent.getUserId())
                .type(NotificationType.GETTING_STARTED)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.LOW)
                .recipientAddress(userEvent.getEmail())
                .subject("Getting Started Guide")
                .templateId(getTemplateId("GETTING_STARTED_EMAIL"))
                .templateVariables(userEvent.getTemplateVariables())
                .correlationId(userEvent.getCorrelationId())
                .referenceType("USER")
                .referenceId(userEvent.getUserId().toString())
                .scheduledAt(java.time.LocalDateTime.now().plusHours(1))
                .build();

        notificationService.createNotification(gettingStartedRequest);
    }

    /**
     * Handle password reset requested event
     */
    private void handlePasswordResetRequested(UserEvent userEvent) {
        log.info("Processing password reset requested event: userId={}", userEvent.getUserId());

        // Send password reset email
        CreateNotificationRequest emailRequest = CreateNotificationRequest.builder()
                .userId(userEvent.getUserId())
                .type(NotificationType.PASSWORD_RESET)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.HIGH)
                .recipientAddress(userEvent.getEmail())
                .subject("Password Reset Request")
                .templateId(getTemplateId("PASSWORD_RESET_EMAIL"))
                .templateVariables(userEvent.getTemplateVariables())
                .correlationId(userEvent.getCorrelationId())
                .referenceType("USER")
                .referenceId(userEvent.getUserId().toString())
                .expiresAt(java.time.LocalDateTime.now().plusHours(1)) // Reset link expires in 1 hour
                .build();

        notificationService.createNotification(emailRequest);

        // Send SMS if phone number is available for additional security
        if (userEvent.getPhoneNumber() != null && !userEvent.getPhoneNumber().isEmpty()) {
            CreateNotificationRequest smsRequest = CreateNotificationRequest.builder()
                    .userId(userEvent.getUserId())
                    .type(NotificationType.SECURITY_ALERT)
                    .channel(NotificationChannel.SMS)
                    .priority(Priority.HIGH)
                    .recipientAddress(userEvent.getPhoneNumber())
                    .content("A password reset was requested for your account. If this wasn't you, please contact support immediately.")
                    .correlationId(userEvent.getCorrelationId())
                    .referenceType("USER")
                    .referenceId(userEvent.getUserId().toString())
                    .build();

            notificationService.createNotification(smsRequest);
        }
    }

    /**
     * Handle profile updated event
     */
    private void handleProfileUpdated(UserEvent userEvent) {
        log.info("Processing profile updated event: userId={}", userEvent.getUserId());

        // Send profile update confirmation
        CreateNotificationRequest emailRequest = CreateNotificationRequest.builder()
                .userId(userEvent.getUserId())
                .type(NotificationType.PROFILE_UPDATE)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.LOW)
                .recipientAddress(userEvent.getEmail())
                .subject("Profile Updated")
                .templateId(getTemplateId("PROFILE_UPDATED_EMAIL"))
                .templateVariables(userEvent.getTemplateVariables())
                .correlationId(userEvent.getCorrelationId())
                .referenceType("USER")
                .referenceId(userEvent.getUserId().toString())
                .build();

        notificationService.createNotification(emailRequest);

        // Send in-app notification
        CreateNotificationRequest inAppRequest = CreateNotificationRequest.builder()
                .userId(userEvent.getUserId())
                .type(NotificationType.PROFILE_UPDATE)
                .channel(NotificationChannel.IN_APP)
                .priority(Priority.LOW)
                .subject("Profile Updated")
                .content("Your profile has been successfully updated.")
                .correlationId(userEvent.getCorrelationId())
                .referenceType("USER")
                .referenceId(userEvent.getUserId().toString())
                .build();

        notificationService.createNotification(inAppRequest);
    }

    /**
     * Get template ID based on template name
     */
    private Long getTemplateId(String templateName) {
        // In a real implementation, this would look up template IDs from a service or cache
        return switch (templateName) {
            case "WELCOME_EMAIL" -> 10L;
            case "ACTIVATION_REMINDER_EMAIL" -> 11L;
            case "ACCOUNT_ACTIVATED_EMAIL" -> 12L;
            case "GETTING_STARTED_EMAIL" -> 13L;
            case "PASSWORD_RESET_EMAIL" -> 14L;
            case "PROFILE_UPDATED_EMAIL" -> 15L;
            default -> null;
        };
    }
}
