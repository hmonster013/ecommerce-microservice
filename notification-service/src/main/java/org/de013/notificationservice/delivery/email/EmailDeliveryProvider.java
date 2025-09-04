package org.de013.notificationservice.delivery.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.delivery.DeliveryProvider;
import org.de013.notificationservice.delivery.DeliveryResult;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.NotificationDelivery;
import org.de013.notificationservice.entity.enums.DeliveryStatus;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Email delivery provider using Spring Mail
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true", matchIfMissing = true)
public class EmailDeliveryProvider implements DeliveryProvider {

    private final JavaMailSender mailSender;
    private final EmailConfiguration emailConfiguration;

    @Override
    public NotificationChannel getSupportedChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public boolean canHandle(Notification notification) {
        return notification.getChannel() == NotificationChannel.EMAIL &&
               notification.getRecipientAddress() != null &&
               isValidEmail(notification.getRecipientAddress());
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        long startTime = System.currentTimeMillis();
        String messageId = UUID.randomUUID().toString();
        
        log.info("Delivering email notification: id={}, recipient={}", 
                notification.getId(), notification.getRecipientAddress());

        try {
            MimeMessage message = createMimeMessage(notification, messageId);
            mailSender.send(message);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("Email sent successfully: id={}, messageId={}, processingTime={}ms", 
                    notification.getId(), messageId, processingTime);
            
            return DeliveryResult.success(messageId, messageId, "200", processingTime);
            
        } catch (MessagingException e) {
            log.error("Failed to create email message: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            return DeliveryResult.failure(DeliveryStatus.FAILED, 
                    "Failed to create email message: " + e.getMessage(), "400", e.getMessage());
            
        } catch (MailException e) {
            log.error("Failed to send email: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            return DeliveryResult.failure(DeliveryStatus.FAILED, 
                    "Failed to send email: " + e.getMessage(), "500", e.getMessage());
            
        } catch (Exception e) {
            log.error("Unexpected error sending email: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            return DeliveryResult.failure(DeliveryStatus.PROVIDER_ERROR, 
                    "Unexpected error: " + e.getMessage(), "500", e.getMessage());
        }
    }

    @Override
    public DeliveryResult checkStatus(NotificationDelivery delivery) {
        // For SMTP, we can't check delivery status after sending
        // This would be implemented for providers like SendGrid, AWS SES, etc.
        log.debug("Status check not supported for SMTP delivery: deliveryId={}", delivery.getId());
        return DeliveryResult.success(delivery.getExternalId(), delivery.getProviderMessageId());
    }

    @Override
    public String getProviderName() {
        return "SMTP";
    }

    @Override
    public boolean isAvailable() {
        try {
            // Simple availability check - try to get a connection
            mailSender.createMimeMessage();
            return true;
        } catch (Exception e) {
            log.warn("Email provider not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public int getRateLimit() {
        return emailConfiguration.getRateLimit();
    }

    /**
     * Create MIME message from notification
     */
    private MimeMessage createMimeMessage(Notification notification, String messageId) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        // Set basic properties
        helper.setTo(notification.getRecipientAddress());
        helper.setSubject(notification.getSubject() != null ? notification.getSubject() : "Notification");
        
        // Set sender
        String senderAddress = notification.getSenderAddress() != null ? 
                notification.getSenderAddress() : emailConfiguration.getDefaultSender();
        String senderName = emailConfiguration.getDefaultSenderName();
        
        try {
            if (senderName != null) {
                helper.setFrom(senderAddress, senderName);
            } else {
                helper.setFrom(senderAddress);
            }
        } catch (java.io.UnsupportedEncodingException e) {
            // Fallback to address only if encoding fails
            helper.setFrom(senderAddress);
        }

        // Set reply-to if configured
        if (emailConfiguration.getReplyTo() != null) {
            helper.setReplyTo(emailConfiguration.getReplyTo());
        }

        // Set content
        boolean hasHtml = notification.getHtmlContent() != null && !notification.getHtmlContent().trim().isEmpty();
        if (hasHtml) {
            String textContent = notification.getContent() != null ? notification.getContent() : 
                    stripHtml(notification.getHtmlContent());
            helper.setText(textContent, notification.getHtmlContent());
        } else {
            helper.setText(notification.getContent() != null ? notification.getContent() : "");
        }

        // Set headers
        message.setHeader("Message-ID", messageId);
        message.setHeader("X-Notification-ID", notification.getId().toString());
        if (notification.getCorrelationId() != null) {
            message.setHeader("X-Correlation-ID", notification.getCorrelationId());
        }

        return message;
    }

    /**
     * Validate email address format
     */
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    }

    /**
     * Strip HTML tags for plain text version
     */
    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }
}
