package org.de013.paymentservice.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.event.PaymentEvent;
import org.de013.paymentservice.event.RefundEvent;
import org.de013.paymentservice.service.external.UserValidationService;
import org.de013.paymentservice.dto.external.UserDto;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of NotificationService
 * Handles sending notifications for payment and refund events
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final UserValidationService userValidationService;

    @Override
    public void sendPaymentNotification(PaymentEvent event) {
        try {
            log.info("Sending payment notification: eventType={}, paymentId={}, userId={}", 
                    event.getEventType(), event.getPaymentId(), event.getUserId());

            // Get user details if not provided
            if (event.getUserEmail() == null || event.getUserName() == null) {
                enrichEventWithUserDetails(event);
            }

            // Prepare notification data
            Map<String, Object> templateData = createPaymentTemplateData(event);
            
            // Send notifications based on preferences
            if (event.isSendEmailNotification() && event.getUserEmail() != null) {
                sendPaymentEmailNotification(event, templateData);
            }
            
            if (event.isSendSmsNotification()) {
                sendPaymentSmsNotification(event);
            }
            
            if (event.isSendPushNotification()) {
                sendPaymentPushNotification(event, templateData);
            }

            log.info("Payment notification sent successfully: eventType={}, paymentId={}", 
                    event.getEventType(), event.getPaymentId());

        } catch (Exception e) {
            log.error("Error sending payment notification: eventType={}, paymentId={}", 
                     event.getEventType(), event.getPaymentId(), e);
        }
    }

    @Override
    public void sendRefundNotification(RefundEvent event) {
        try {
            log.info("Sending refund notification: eventType={}, refundId={}, userId={}", 
                    event.getEventType(), event.getRefundId(), event.getUserId());

            // Get user details if not provided
            if (event.getUserEmail() == null || event.getUserName() == null) {
                enrichRefundEventWithUserDetails(event);
            }

            // Prepare notification data
            Map<String, Object> templateData = createRefundTemplateData(event);
            
            // Send notifications based on preferences
            if (event.isSendEmailNotification() && event.getUserEmail() != null) {
                sendRefundEmailNotification(event, templateData);
            }
            
            if (event.isSendSmsNotification()) {
                sendRefundSmsNotification(event);
            }
            
            if (event.isSendPushNotification()) {
                sendRefundPushNotification(event, templateData);
            }

            log.info("Refund notification sent successfully: eventType={}, refundId={}", 
                    event.getEventType(), event.getRefundId());

        } catch (Exception e) {
            log.error("Error sending refund notification: eventType={}, refundId={}", 
                     event.getEventType(), event.getRefundId(), e);
        }
    }

    @Override
    public void sendEmailNotification(String to, String subject, String body, String templateName, Map<String, Object> templateData) {
        try {
            log.info("Sending email notification: to={}, subject={}, template={}", to, subject, templateName);
            
            // TODO: Integrate with email service (SendGrid, AWS SES, etc.)
            // For now, just log the notification
            log.info("EMAIL NOTIFICATION - To: {}, Subject: {}, Body: {}", to, subject, body);
            
            if (templateData != null) {
                log.debug("Email template data: {}", templateData);
            }
            
        } catch (Exception e) {
            log.error("Error sending email notification: to={}, subject={}", to, subject, e);
        }
    }

    @Override
    public void sendSmsNotification(String phoneNumber, String message) {
        try {
            log.info("Sending SMS notification: to={}, message length={}", phoneNumber, message.length());
            
            // TODO: Integrate with SMS service (Twilio, AWS SNS, etc.)
            // For now, just log the notification
            log.info("SMS NOTIFICATION - To: {}, Message: {}", phoneNumber, message);
            
        } catch (Exception e) {
            log.error("Error sending SMS notification: to={}", phoneNumber, e);
        }
    }

    @Override
    public void sendPushNotification(Long userId, String title, String message, Map<String, Object> data) {
        try {
            log.info("Sending push notification: userId={}, title={}", userId, title);
            
            // TODO: Integrate with push notification service (Firebase, AWS SNS, etc.)
            // For now, just log the notification
            log.info("PUSH NOTIFICATION - UserId: {}, Title: {}, Message: {}", userId, title, message);
            
            if (data != null) {
                log.debug("Push notification data: {}", data);
            }
            
        } catch (Exception e) {
            log.error("Error sending push notification: userId={}, title={}", userId, title, e);
        }
    }

    @Override
    public void sendWebhookNotification(String webhookUrl, Object payload) {
        try {
            log.info("Sending webhook notification: url={}", webhookUrl);
            
            // TODO: Implement HTTP POST to webhook URL
            // For now, just log the notification
            log.info("WEBHOOK NOTIFICATION - URL: {}, Payload: {}", webhookUrl, payload);
            
        } catch (Exception e) {
            log.error("Error sending webhook notification: url={}", webhookUrl, e);
        }
    }

    @Override
    public void sendMultiChannelNotification(Long userId, String email, String phoneNumber, 
                                           String subject, String message, Map<String, Object> data,
                                           boolean sendEmail, boolean sendSms, boolean sendPush) {
        try {
            log.info("Sending multi-channel notification: userId={}, email={}, sms={}, push={}", 
                    userId, sendEmail, sendSms, sendPush);

            if (sendEmail && email != null) {
                sendEmailNotification(email, subject, message, null, data);
            }
            
            if (sendSms && phoneNumber != null) {
                sendSmsNotification(phoneNumber, message);
            }
            
            if (sendPush && userId != null) {
                sendPushNotification(userId, subject, message, data);
            }

        } catch (Exception e) {
            log.error("Error sending multi-channel notification: userId={}", userId, e);
        }
    }

    // Private helper methods

    private void enrichEventWithUserDetails(PaymentEvent event) {
        try {
            UserDto user = userValidationService.getUserById(event.getUserId());
            event.setUserEmail(user.getEmail());
            event.setUserName(user.getFullName());
        } catch (Exception e) {
            log.warn("Could not enrich payment event with user details: userId={}", event.getUserId(), e);
        }
    }

    private void enrichRefundEventWithUserDetails(RefundEvent event) {
        try {
            UserDto user = userValidationService.getUserById(event.getUserId());
            event.setUserEmail(user.getEmail());
            event.setUserName(user.getFullName());
        } catch (Exception e) {
            log.warn("Could not enrich refund event with user details: userId={}", event.getUserId(), e);
        }
    }

    private Map<String, Object> createPaymentTemplateData(PaymentEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("paymentId", event.getPaymentId());
        data.put("paymentNumber", event.getPaymentNumber());
        data.put("orderId", event.getOrderId());
        data.put("orderNumber", event.getOrderNumber());
        data.put("amount", event.getAmount());
        data.put("currency", event.getCurrency());
        data.put("status", event.getStatus());
        data.put("userName", event.getUserName());
        data.put("userEmail", event.getUserEmail());
        data.put("timestamp", event.getTimestamp());
        data.put("reason", event.getReason());
        data.put("description", event.getDescription());
        return data;
    }

    private Map<String, Object> createRefundTemplateData(RefundEvent event) {
        Map<String, Object> data = new HashMap<>();
        data.put("refundId", event.getRefundId());
        data.put("refundNumber", event.getRefundNumber());
        data.put("paymentId", event.getPaymentId());
        data.put("paymentNumber", event.getPaymentNumber());
        data.put("orderId", event.getOrderId());
        data.put("orderNumber", event.getOrderNumber());
        data.put("amount", event.getAmount());
        data.put("currency", event.getCurrency());
        data.put("status", event.getStatus());
        data.put("refundType", event.getRefundType());
        data.put("userName", event.getUserName());
        data.put("userEmail", event.getUserEmail());
        data.put("timestamp", event.getTimestamp());
        data.put("refundReason", event.getRefundReason());
        data.put("initiatedBy", event.getInitiatedBy());
        return data;
    }

    private void sendPaymentEmailNotification(PaymentEvent event, Map<String, Object> templateData) {
        String subject = getPaymentEmailSubject(event);
        String body = getPaymentEmailBody(event);
        String templateName = getPaymentEmailTemplate(event);
        
        sendEmailNotification(event.getUserEmail(), subject, body, templateName, templateData);
    }

    private void sendPaymentSmsNotification(PaymentEvent event) {
        String message = getPaymentSmsMessage(event);
        // TODO: Get user phone number from user service
        String phoneNumber = null; // userValidationService.getUserPhoneNumber(event.getUserId());
        
        if (phoneNumber != null) {
            sendSmsNotification(phoneNumber, message);
        }
    }

    private void sendPaymentPushNotification(PaymentEvent event, Map<String, Object> templateData) {
        String title = getPaymentPushTitle(event);
        String message = getPaymentPushMessage(event);
        
        sendPushNotification(event.getUserId(), title, message, templateData);
    }

    private void sendRefundEmailNotification(RefundEvent event, Map<String, Object> templateData) {
        String subject = getRefundEmailSubject(event);
        String body = getRefundEmailBody(event);
        String templateName = getRefundEmailTemplate(event);
        
        sendEmailNotification(event.getUserEmail(), subject, body, templateName, templateData);
    }

    private void sendRefundSmsNotification(RefundEvent event) {
        String message = getRefundSmsMessage(event);
        // TODO: Get user phone number from user service
        String phoneNumber = null; // userValidationService.getUserPhoneNumber(event.getUserId());
        
        if (phoneNumber != null) {
            sendSmsNotification(phoneNumber, message);
        }
    }

    private void sendRefundPushNotification(RefundEvent event, Map<String, Object> templateData) {
        String title = getRefundPushTitle(event);
        String message = getRefundPushMessage(event);
        
        sendPushNotification(event.getUserId(), title, message, templateData);
    }

    // Message generation methods
    private String getPaymentEmailSubject(PaymentEvent event) {
        return switch (event.getEventType()) {
            case PaymentEvent.PAYMENT_CREATED -> "Payment Created - Order #" + event.getOrderNumber();
            case PaymentEvent.PAYMENT_SUCCEEDED -> "Payment Successful - Order #" + event.getOrderNumber();
            case PaymentEvent.PAYMENT_FAILED -> "Payment Failed - Order #" + event.getOrderNumber();
            case PaymentEvent.PAYMENT_CANCELED -> "Payment Canceled - Order #" + event.getOrderNumber();
            case PaymentEvent.PAYMENT_REQUIRES_ACTION -> "Payment Requires Action - Order #" + event.getOrderNumber();
            default -> "Payment Update - Order #" + event.getOrderNumber();
        };
    }

    private String getPaymentEmailBody(PaymentEvent event) {
        return String.format("Dear %s,\n\nYour payment of %s %s for order #%s has been %s.\n\nPayment Number: %s\n\nThank you for your business!",
                event.getUserName(), event.getAmount(), event.getCurrency(), 
                event.getOrderNumber(), event.getStatus().toLowerCase(), event.getPaymentNumber());
    }

    private String getPaymentEmailTemplate(PaymentEvent event) {
        return switch (event.getEventType()) {
            case PaymentEvent.PAYMENT_SUCCEEDED -> "payment-success";
            case PaymentEvent.PAYMENT_FAILED -> "payment-failed";
            case PaymentEvent.PAYMENT_REQUIRES_ACTION -> "payment-action-required";
            default -> "payment-update";
        };
    }

    private String getPaymentSmsMessage(PaymentEvent event) {
        return String.format("Payment %s: %s %s for order #%s. Payment #%s", 
                event.getStatus().toLowerCase(), event.getAmount(), event.getCurrency(), 
                event.getOrderNumber(), event.getPaymentNumber());
    }

    private String getPaymentPushTitle(PaymentEvent event) {
        return switch (event.getEventType()) {
            case PaymentEvent.PAYMENT_SUCCEEDED -> "Payment Successful";
            case PaymentEvent.PAYMENT_FAILED -> "Payment Failed";
            case PaymentEvent.PAYMENT_REQUIRES_ACTION -> "Payment Action Required";
            default -> "Payment Update";
        };
    }

    private String getPaymentPushMessage(PaymentEvent event) {
        return String.format("Your payment of %s %s for order #%s has been %s", 
                event.getAmount(), event.getCurrency(), event.getOrderNumber(), event.getStatus().toLowerCase());
    }

    private String getRefundEmailSubject(RefundEvent event) {
        return switch (event.getEventType()) {
            case RefundEvent.REFUND_CREATED -> "Refund Initiated - Order #" + event.getOrderNumber();
            case RefundEvent.REFUND_SUCCEEDED -> "Refund Processed - Order #" + event.getOrderNumber();
            case RefundEvent.REFUND_FAILED -> "Refund Failed - Order #" + event.getOrderNumber();
            case RefundEvent.REFUND_APPROVED -> "Refund Approved - Order #" + event.getOrderNumber();
            case RefundEvent.REFUND_REJECTED -> "Refund Rejected - Order #" + event.getOrderNumber();
            default -> "Refund Update - Order #" + event.getOrderNumber();
        };
    }

    private String getRefundEmailBody(RefundEvent event) {
        return String.format("Dear %s,\n\nYour refund of %s %s for order #%s has been %s.\n\nRefund Number: %s\nOriginal Payment: %s\n\nThank you!",
                event.getUserName(), event.getAmount(), event.getCurrency(), 
                event.getOrderNumber(), event.getStatus().toLowerCase(), 
                event.getRefundNumber(), event.getPaymentNumber());
    }

    private String getRefundEmailTemplate(RefundEvent event) {
        return switch (event.getEventType()) {
            case RefundEvent.REFUND_SUCCEEDED -> "refund-success";
            case RefundEvent.REFUND_FAILED -> "refund-failed";
            case RefundEvent.REFUND_APPROVED -> "refund-approved";
            case RefundEvent.REFUND_REJECTED -> "refund-rejected";
            default -> "refund-update";
        };
    }

    private String getRefundSmsMessage(RefundEvent event) {
        return String.format("Refund %s: %s %s for order #%s. Refund #%s", 
                event.getStatus().toLowerCase(), event.getAmount(), event.getCurrency(), 
                event.getOrderNumber(), event.getRefundNumber());
    }

    private String getRefundPushTitle(RefundEvent event) {
        return switch (event.getEventType()) {
            case RefundEvent.REFUND_SUCCEEDED -> "Refund Processed";
            case RefundEvent.REFUND_FAILED -> "Refund Failed";
            case RefundEvent.REFUND_APPROVED -> "Refund Approved";
            case RefundEvent.REFUND_REJECTED -> "Refund Rejected";
            default -> "Refund Update";
        };
    }

    private String getRefundPushMessage(RefundEvent event) {
        return String.format("Your refund of %s %s for order #%s has been %s", 
                event.getAmount(), event.getCurrency(), event.getOrderNumber(), event.getStatus().toLowerCase());
    }
}
