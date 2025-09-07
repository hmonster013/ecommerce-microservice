package org.de013.notificationservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.delivery.sms.SmsConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Validates Twilio configuration on application startup
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TwilioConfigurationValidator {

    private final SmsConfiguration smsConfiguration;

    @EventListener(ApplicationReadyEvent.class)
    public void validateTwilioConfiguration() {
        if (!smsConfiguration.isEnabled()) {
            log.info("SMS notifications are disabled");
            return;
        }

        if (smsConfiguration.isMockMode()) {
            log.info("SMS notifications running in MOCK mode");
            return;
        }

        log.info("Validating Twilio configuration...");

        boolean isValid = true;
        StringBuilder errors = new StringBuilder();

        // Validate Account SID
        if (isBlankOrDefault(smsConfiguration.getAccountSid(), "your_account_sid_here")) {
            errors.append("- TWILIO_ACCOUNT_SID is not configured\n");
            isValid = false;
        }

        // Validate Auth Token
        if (isBlankOrDefault(smsConfiguration.getAuthToken(), "your_auth_token_here")) {
            errors.append("- TWILIO_AUTH_TOKEN is not configured\n");
            isValid = false;
        }

        // Validate Sender Number
        if (isBlankOrDefault(smsConfiguration.getSenderNumber(), "your_twilio_phone_number_here")) {
            errors.append("- TWILIO_PHONE_NUMBER is not configured\n");
            isValid = false;
        } else if (!isValidPhoneNumber(smsConfiguration.getSenderNumber())) {
            errors.append("- TWILIO_PHONE_NUMBER format is invalid (should start with + and country code)\n");
            isValid = false;
        }

        if (isValid) {
            log.info("✅ Twilio configuration is valid. SMS notifications are ready.");
            log.info("Twilio Account SID: {}", maskAccountSid(smsConfiguration.getAccountSid()));
            log.info("Twilio Sender Number: {}", smsConfiguration.getSenderNumber());
        } else {
            log.error("❌ Twilio configuration is INVALID. SMS notifications will not work:");
            log.error("\n{}", errors.toString());
            log.error("Please set the following environment variables:");
            log.error("- TWILIO_ACCOUNT_SID=your_actual_account_sid");
            log.error("- TWILIO_AUTH_TOKEN=your_actual_auth_token");
            log.error("- TWILIO_PHONE_NUMBER=your_twilio_phone_number (e.g., +1234567890)");
            log.error("Or set notification.sms.mock-mode=true for testing");
        }
    }

    private boolean isBlankOrDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() || value.equals(defaultValue);
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("^\\+[1-9]\\d{1,14}$");
    }

    private String maskAccountSid(String accountSid) {
        if (accountSid == null || accountSid.length() < 8) {
            return "****";
        }
        return accountSid.substring(0, 4) + "****" + accountSid.substring(accountSid.length() - 4);
    }
}
