package org.de013.notificationservice.event.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.dto.CreateNotificationRequest;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.entity.enums.Priority;
import org.de013.notificationservice.event.dto.PaymentEvent;
import org.de013.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumer for payment events to trigger notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final NotificationService notificationService;

    /**
     * Handle payment events
     */
    @RabbitListener(
        queues = "notification.payment.events",
        containerFactory = "eventRabbitListenerContainerFactory"
    )
    public void handlePaymentEvent(@Payload PaymentEvent paymentEvent, @Header Map<String, Object> headers) {
        log.info("Received payment event: type={}, paymentId={}, userId={}, status={}", 
                paymentEvent.getEventType(), paymentEvent.getPaymentId(), 
                paymentEvent.getUserId(), paymentEvent.getStatus());

        try {
            // Only process events that should trigger notifications
            if (!paymentEvent.shouldSendNotification()) {
                log.debug("Skipping notification for payment event: type={}, status={}", 
                        paymentEvent.getEventType(), paymentEvent.getStatus());
                return;
            }

            switch (paymentEvent.getEventType()) {
                case "PAYMENT_CONFIRMED" -> handlePaymentConfirmed(paymentEvent);
                case "PAYMENT_FAILED" -> handlePaymentFailed(paymentEvent);
                case "PAYMENT_REFUNDED" -> handlePaymentRefunded(paymentEvent);
                case "PAYMENT_CANCELLED" -> handlePaymentCancelled(paymentEvent);
                default -> log.warn("Unknown payment event type: {}", paymentEvent.getEventType());
            }
            
            log.info("Successfully processed payment event: type={}, paymentId={}", 
                    paymentEvent.getEventType(), paymentEvent.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error processing payment event: type={}, paymentId={}, error={}", 
                    paymentEvent.getEventType(), paymentEvent.getPaymentId(), e.getMessage(), e);
            throw e; // Re-throw to trigger retry or DLQ
        }
    }

    /**
     * Handle payment confirmed event
     */
    private void handlePaymentConfirmed(PaymentEvent paymentEvent) {
        log.info("Processing payment confirmed event: paymentId={}, amount={}", 
                paymentEvent.getPaymentId(), paymentEvent.getAmount());

        // Send payment confirmation email
        CreateNotificationRequest emailRequest = CreateNotificationRequest.builder()
                .userId(paymentEvent.getUserId())
                .type(NotificationType.PAYMENT_CONFIRMATION)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.NORMAL)
                .recipientAddress(paymentEvent.getCustomerEmail())
                .subject("Payment Confirmed - " + (paymentEvent.getOrderNumber() != null ? 
                        paymentEvent.getOrderNumber() : paymentEvent.getPaymentReference()))
                .templateId(getTemplateId("PAYMENT_CONFIRMATION_EMAIL"))
                .templateVariables(paymentEvent.getTemplateVariables())
                .correlationId(paymentEvent.getCorrelationId())
                .referenceType("PAYMENT")
                .referenceId(paymentEvent.getPaymentId().toString())
                .build();

        notificationService.createNotification(emailRequest);

        // Send SMS confirmation if phone number is available
        if (paymentEvent.getCustomerPhone() != null && !paymentEvent.getCustomerPhone().isEmpty()) {
            CreateNotificationRequest smsRequest = CreateNotificationRequest.builder()
                    .userId(paymentEvent.getUserId())
                    .type(NotificationType.PAYMENT_CONFIRMATION)
                    .channel(NotificationChannel.SMS)
                    .priority(Priority.NORMAL)
                    .recipientAddress(paymentEvent.getCustomerPhone())
                    .content("Payment confirmed: " + paymentEvent.getCurrency() + " " + paymentEvent.getAmount() + 
                            " for " + (paymentEvent.getOrderNumber() != null ? 
                            "order " + paymentEvent.getOrderNumber() : "payment " + paymentEvent.getPaymentReference()))
                    .correlationId(paymentEvent.getCorrelationId())
                    .referenceType("PAYMENT")
                    .referenceId(paymentEvent.getPaymentId().toString())
                    .build();

            notificationService.createNotification(smsRequest);
        }

        // Send in-app notification
        CreateNotificationRequest inAppRequest = CreateNotificationRequest.builder()
                .userId(paymentEvent.getUserId())
                .type(NotificationType.PAYMENT_CONFIRMATION)
                .channel(NotificationChannel.IN_APP)
                .priority(Priority.NORMAL)
                .subject("Payment Confirmed")
                .content("Your payment of " + paymentEvent.getCurrency() + " " + paymentEvent.getAmount() + 
                        " has been successfully processed.")
                .correlationId(paymentEvent.getCorrelationId())
                .referenceType("PAYMENT")
                .referenceId(paymentEvent.getPaymentId().toString())
                .build();

        notificationService.createNotification(inAppRequest);
    }

    /**
     * Handle payment failed event
     */
    private void handlePaymentFailed(PaymentEvent paymentEvent) {
        log.info("Processing payment failed event: paymentId={}, reason={}", 
                paymentEvent.getPaymentId(), paymentEvent.getFailureReason());

        // Send payment failure email
        CreateNotificationRequest emailRequest = CreateNotificationRequest.builder()
                .userId(paymentEvent.getUserId())
                .type(NotificationType.PAYMENT_FAILED)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.HIGH)
                .recipientAddress(paymentEvent.getCustomerEmail())
                .subject("Payment Failed - " + (paymentEvent.getOrderNumber() != null ? 
                        paymentEvent.getOrderNumber() : paymentEvent.getPaymentReference()))
                .templateId(getTemplateId("PAYMENT_FAILED_EMAIL"))
                .templateVariables(paymentEvent.getTemplateVariables())
                .correlationId(paymentEvent.getCorrelationId())
                .referenceType("PAYMENT")
                .referenceId(paymentEvent.getPaymentId().toString())
                .build();

        notificationService.createNotification(emailRequest);

        // Send urgent push notification
        CreateNotificationRequest pushRequest = CreateNotificationRequest.builder()
                .userId(paymentEvent.getUserId())
                .type(NotificationType.PAYMENT_FAILED)
                .channel(NotificationChannel.PUSH)
                .priority(Priority.HIGH)
                .subject("Payment Failed")
                .content("Your payment for " + (paymentEvent.getOrderNumber() != null ? 
                        "order " + paymentEvent.getOrderNumber() : "payment " + paymentEvent.getPaymentReference()) + 
                        " could not be processed. Please try again.")
                .correlationId(paymentEvent.getCorrelationId())
                .referenceType("PAYMENT")
                .referenceId(paymentEvent.getPaymentId().toString())
                .build();

        notificationService.createNotification(pushRequest);

        // Send in-app notification with action
        CreateNotificationRequest inAppRequest = CreateNotificationRequest.builder()
                .userId(paymentEvent.getUserId())
                .type(NotificationType.PAYMENT_FAILED)
                .channel(NotificationChannel.IN_APP)
                .priority(Priority.HIGH)
                .subject("Payment Failed")
                .content("Your payment could not be processed. " + 
                        (paymentEvent.getFailureReason() != null ? "Reason: " + paymentEvent.getFailureReason() : ""))
                .correlationId(paymentEvent.getCorrelationId())
                .referenceType("PAYMENT")
                .referenceId(paymentEvent.getPaymentId().toString())
                .build();

        notificationService.createNotification(inAppRequest);
    }

    /**
     * Handle payment refunded event
     */
    private void handlePaymentRefunded(PaymentEvent paymentEvent) {
        log.info("Processing payment refunded event: paymentId={}, amount={}", 
                paymentEvent.getPaymentId(), paymentEvent.getAmount());

        // Send refund confirmation email
        CreateNotificationRequest emailRequest = CreateNotificationRequest.builder()
                .userId(paymentEvent.getUserId())
                .type(NotificationType.PAYMENT_REFUNDED)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.NORMAL)
                .recipientAddress(paymentEvent.getCustomerEmail())
                .subject("Refund Processed - " + (paymentEvent.getOrderNumber() != null ? 
                        paymentEvent.getOrderNumber() : paymentEvent.getPaymentReference()))
                .templateId(getTemplateId("PAYMENT_REFUNDED_EMAIL"))
                .templateVariables(paymentEvent.getTemplateVariables())
                .correlationId(paymentEvent.getCorrelationId())
                .referenceType("PAYMENT")
                .referenceId(paymentEvent.getPaymentId().toString())
                .build();

        notificationService.createNotification(emailRequest);

        // Send in-app notification
        CreateNotificationRequest inAppRequest = CreateNotificationRequest.builder()
                .userId(paymentEvent.getUserId())
                .type(NotificationType.PAYMENT_REFUNDED)
                .channel(NotificationChannel.IN_APP)
                .priority(Priority.NORMAL)
                .subject("Refund Processed")
                .content("Your refund of " + paymentEvent.getCurrency() + " " + paymentEvent.getAmount() + 
                        " has been processed and will appear in your account within 3-5 business days.")
                .correlationId(paymentEvent.getCorrelationId())
                .referenceType("PAYMENT")
                .referenceId(paymentEvent.getPaymentId().toString())
                .build();

        notificationService.createNotification(inAppRequest);
    }

    /**
     * Handle payment cancelled event
     */
    private void handlePaymentCancelled(PaymentEvent paymentEvent) {
        log.info("Processing payment cancelled event: paymentId={}", paymentEvent.getPaymentId());

        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .userId(paymentEvent.getUserId())
                .type(NotificationType.PAYMENT_CANCELLED)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.NORMAL)
                .recipientAddress(paymentEvent.getCustomerEmail())
                .subject("Payment Cancelled - " + (paymentEvent.getOrderNumber() != null ? 
                        paymentEvent.getOrderNumber() : paymentEvent.getPaymentReference()))
                .templateId(getTemplateId("PAYMENT_CANCELLED_EMAIL"))
                .templateVariables(paymentEvent.getTemplateVariables())
                .correlationId(paymentEvent.getCorrelationId())
                .referenceType("PAYMENT")
                .referenceId(paymentEvent.getPaymentId().toString())
                .build();

        notificationService.createNotification(request);
    }

    /**
     * Get template ID based on template name
     */
    private Long getTemplateId(String templateName) {
        // In a real implementation, this would look up template IDs from a service or cache
        return switch (templateName) {
            case "PAYMENT_CONFIRMATION_EMAIL" -> 6L;
            case "PAYMENT_FAILED_EMAIL" -> 7L;
            case "PAYMENT_REFUNDED_EMAIL" -> 8L;
            case "PAYMENT_CANCELLED_EMAIL" -> 9L;
            default -> null;
        };
    }
}
