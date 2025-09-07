package org.de013.notificationservice.service;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Simple SMS Service for sending SMS messages
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    @Value("${app.notification.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${app.notification.sms.provider-name:mock}")
    private String smsProvider;

    @Value("${app.notification.sms.mock-mode:true}")
    private boolean mockMode;

    @Value("${app.notification.sms.account-sid:}")
    private String twilioAccountSid;

    @Value("${app.notification.sms.auth-token:}")
    private String twilioAuthToken;

    @Value("${app.notification.sms.sender-number:}")
    private String twilioSenderNumber;

    /**
     * Send SMS message
     */
    public void sendSms(String phoneNumber, String message) {
        try {
            log.info("Sending SMS to: {}, message length: {}", phoneNumber, message.length());
            
            if (!smsEnabled) {
                log.warn("SMS service is disabled. Message not sent to: {}", phoneNumber);
                return;
            }

            if (!isValidPhoneNumber(phoneNumber)) {
                throw new IllegalArgumentException("Invalid phone number format: " + phoneNumber);
            }

            // Check if mock mode is enabled
            if (mockMode) {
                sendViaMock(phoneNumber, message);
            } else {
                // In production, integrate with Twilio, AWS SNS, or other SMS providers
                switch (smsProvider.toUpperCase()) {
                    case "TWILIO":
                        sendViaTwilio(phoneNumber, message);
                        break;
                    case "AWS":
                        sendViaAWS(phoneNumber, message);
                        break;
                    default:
                        sendViaMock(phoneNumber, message);
                        break;
                }
            }
            
            log.info("SMS sent successfully to: {}", phoneNumber);
            
        } catch (Exception e) {
            log.error("Failed to send SMS to: {}, error: {}", phoneNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to send SMS: " + e.getMessage(), e);
        }
    }

    /**
     * Validate phone number format
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("^\\+?[1-9]\\d{1,14}$");
    }

    private void sendViaTwilio(String phoneNumber, String message) {
        try {
            // Initialize Twilio
            if (twilioAccountSid.isEmpty() || twilioAuthToken.isEmpty() || twilioSenderNumber.isEmpty()) {
                log.warn("Twilio credentials not configured. Using mock mode instead.");
                sendViaMock(phoneNumber, message);
                return;
            }

            Twilio.init(twilioAccountSid, twilioAuthToken);

            // Send SMS via Twilio
            Message twilioMessage = Message.creator(
                    new PhoneNumber(phoneNumber),
                    new PhoneNumber(twilioSenderNumber),
                    message
            ).create();

            log.info("TWILIO SMS sent successfully - SID: {}, To: {}, Status: {}",
                    twilioMessage.getSid(), phoneNumber, twilioMessage.getStatus());

        } catch (ApiException e) {
            log.error("Twilio API error sending SMS to {}: {} (Code: {})",
                    phoneNumber, e.getMessage(), e.getCode());

            // For development, fallback to mock mode on Twilio errors
            if (mockMode || e.getMessage().contains("not a Twilio phone number")) {
                log.warn("Falling back to mock mode due to Twilio configuration issue");
                sendViaMock(phoneNumber, message);
                return;
            }

            throw new RuntimeException("Twilio SMS failed: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Failed to send SMS via Twilio to {}: {}", phoneNumber, e.getMessage(), e);
            throw new RuntimeException("Twilio SMS failed: " + e.getMessage(), e);
        }
    }

    private void sendViaAWS(String phoneNumber, String message) {
        // TODO: Implement AWS SNS integration
        log.info("AWS SNS SMS - To: {}, Message: {}", phoneNumber, message);
    }

    private void sendViaMock(String phoneNumber, String message) {
        log.info("MOCK SMS - To: {}, Message: {}", phoneNumber, message);
    }
}
