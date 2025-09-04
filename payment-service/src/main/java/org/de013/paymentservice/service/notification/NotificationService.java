package org.de013.paymentservice.service.notification;

import org.de013.paymentservice.event.PaymentEvent;
import org.de013.paymentservice.event.RefundEvent;

import java.util.Map;

/**
 * Service interface for sending notifications
 */
public interface NotificationService {

    /**
     * Send payment notification based on event
     */
    void sendPaymentNotification(PaymentEvent event);

    /**
     * Send refund notification based on event
     */
    void sendRefundNotification(RefundEvent event);

    /**
     * Send email notification
     */
    void sendEmailNotification(String to, String subject, String body, String templateName, Map<String, Object> templateData);

    /**
     * Send SMS notification
     */
    void sendSmsNotification(String phoneNumber, String message);

    /**
     * Send push notification
     */
    void sendPushNotification(Long userId, String title, String message, Map<String, Object> data);

    /**
     * Send webhook notification
     */
    void sendWebhookNotification(String webhookUrl, Object payload);

    /**
     * Send notification to multiple channels
     */
    void sendMultiChannelNotification(Long userId, String email, String phoneNumber,
                                    String subject, String message, Map<String, Object> data,
                                    boolean sendEmail, boolean sendSms, boolean sendPush);
}
