package org.de013.notificationservice.service;

import com.twilio.Twilio;
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
        if (!smsEnabled || mockMode) {
            throw new IllegalStateException("SMS provider submission unavailable");
        }
        if (!isValidPhoneNumber(phoneNumber)) {
            throw new IllegalArgumentException("Invalid phone number format");
        }
        if (!"TWILIO".equalsIgnoreCase(smsProvider)) {
            throw new IllegalStateException("SMS provider submission unavailable");
        }
        if (twilioAccountSid == null || twilioAccountSid.isBlank()
                || twilioAuthToken == null || twilioAuthToken.isBlank()
                || twilioSenderNumber == null || twilioSenderNumber.isBlank()) {
            throw new IllegalStateException("SMS provider configuration missing");
        }
        Twilio.init(twilioAccountSid, twilioAuthToken);
        Message.creator(new PhoneNumber(phoneNumber), new PhoneNumber(twilioSenderNumber), message).create();
    }

    /**
     * Validate phone number format
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("^\\+?[1-9]\\d{1,14}$");
    }

}
