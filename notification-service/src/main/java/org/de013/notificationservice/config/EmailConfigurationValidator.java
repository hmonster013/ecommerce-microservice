package org.de013.notificationservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.delivery.email.EmailConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Validates email configuration on application startup
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailConfigurationValidator {

    private final EmailConfiguration emailConfiguration;
    private final MailProperties mailProperties;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.test-connection:false}")
    private boolean testConnection;

    @EventListener(ApplicationReadyEvent.class)
    public void validateEmailConfiguration() {
        if (!emailConfiguration.isEnabled()) {
            log.info("Email notifications are disabled");
            return;
        }

        log.info("Validating email configuration...");

        boolean isValid = true;
        StringBuilder errors = new StringBuilder();

        // Validate SMTP host
        if (isBlankOrDefault(mailProperties.getHost(), "smtp.gmail.com")) {
            errors.append("- MAIL_HOST is using default value (smtp.gmail.com)\n");
        }

        // Validate username
        if (isBlankOrDefault(mailProperties.getUsername(), "your-email@gmail.com")) {
            errors.append("- MAIL_USERNAME is not configured\n");
            isValid = false;
        }

        // Validate password
        if (isBlankOrDefault(mailProperties.getPassword(), "your-gmail-app-password")) {
            errors.append("- MAIL_PASSWORD is not configured\n");
            isValid = false;
        }

        // Validate default sender
        if (isBlankOrDefault(emailConfiguration.getDefaultSender(), "noreply@ecommerce.com")) {
            errors.append("- Default sender email should be configured\n");
        }

        if (isValid) {
            log.info("✅ Email configuration appears valid");
            log.info("SMTP Host: {}", mailProperties.getHost());
            log.info("SMTP Port: {}", mailProperties.getPort());
            log.info("SMTP Username: {}", maskEmail(mailProperties.getUsername()));
            log.info("Default Sender: {}", emailConfiguration.getDefaultSender());
            
            // Test connection if enabled
            if (testConnection) {
                testEmailConnection();
            }
        } else {
            log.error("❌ Email configuration is INCOMPLETE:");
            log.error("\n{}", errors.toString());
            log.error("Please set the following environment variables:");
            log.error("- MAIL_HOST=your.smtp.server.com (e.g., smtp.gmail.com, smtp.sendgrid.net)");
            log.error("- MAIL_PORT=587 (or 465 for SSL)");
            log.error("- MAIL_USERNAME=your-email@domain.com");
            log.error("- MAIL_PASSWORD=your-password-or-app-password");
            log.error("\nFor Gmail, use App Password instead of regular password:");
            log.error("https://support.google.com/accounts/answer/185833");
        }
    }

    private void testEmailConnection() {
        try {
            log.info("Testing email connection...");
            mailSender.createMimeMessage();
            log.info("✅ Email connection test successful");
        } catch (Exception e) {
            log.error("❌ Email connection test failed: {}", e.getMessage());
            log.error("Please verify your SMTP settings and credentials");
        }
    }

    private boolean isBlankOrDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() || value.equals(defaultValue);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];
        
        if (username.length() <= 2) {
            return "**@" + domain;
        }
        
        return username.substring(0, 2) + "****@" + domain;
    }
}
