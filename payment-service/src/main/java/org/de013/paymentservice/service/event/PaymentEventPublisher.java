package org.de013.paymentservice.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.Refund;
import org.de013.paymentservice.event.PaymentEvent;
import org.de013.paymentservice.event.RefundEvent;
import org.de013.paymentservice.service.notification.NotificationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Service for publishing payment and refund events
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final NotificationService notificationService;

    /**
     * Publish payment created event
     */
    public void publishPaymentCreated(Payment payment) {
        try {
            log.info("Publishing payment created event: paymentId={}, paymentNumber={}", 
                    payment.getId(), payment.getPaymentNumber());

            PaymentEvent event = PaymentEvent.paymentCreated(
                    payment.getId(),
                    payment.getPaymentNumber(),
                    payment.getOrderId(),
                    payment.getUserId(),
                    payment.getAmount(),
                    payment.getCurrency().name()
            );

            // Publish Spring application event
            applicationEventPublisher.publishEvent(event);

            // Send notification
            notificationService.sendPaymentNotification(event);

            log.info("Payment created event published successfully: paymentId={}", payment.getId());

        } catch (Exception e) {
            log.error("Error publishing payment created event: paymentId={}", payment.getId(), e);
        }
    }

    /**
     * Publish payment succeeded event
     */
    public void publishPaymentSucceeded(Payment payment) {
        try {
            log.info("Publishing payment succeeded event: paymentId={}, paymentNumber={}", 
                    payment.getId(), payment.getPaymentNumber());

            PaymentEvent event = PaymentEvent.paymentSucceeded(
                    payment.getId(),
                    payment.getPaymentNumber(),
                    payment.getOrderId(),
                    payment.getUserId(),
                    payment.getAmount(),
                    payment.getCurrency().name()
            );

            // Publish Spring application event
            applicationEventPublisher.publishEvent(event);

            // Send notification
            notificationService.sendPaymentNotification(event);

            log.info("Payment succeeded event published successfully: paymentId={}", payment.getId());

        } catch (Exception e) {
            log.error("Error publishing payment succeeded event: paymentId={}", payment.getId(), e);
        }
    }

    /**
     * Publish payment failed event
     */
    public void publishPaymentFailed(Payment payment, String reason) {
        try {
            log.info("Publishing payment failed event: paymentId={}, reason={}", 
                    payment.getId(), reason);

            PaymentEvent event = PaymentEvent.paymentFailed(
                    payment.getId(),
                    payment.getPaymentNumber(),
                    payment.getOrderId(),
                    payment.getUserId(),
                    payment.getAmount(),
                    payment.getCurrency().name(),
                    reason
            );

            // Publish Spring application event
            applicationEventPublisher.publishEvent(event);

            // Send notification
            notificationService.sendPaymentNotification(event);

            log.info("Payment failed event published successfully: paymentId={}", payment.getId());

        } catch (Exception e) {
            log.error("Error publishing payment failed event: paymentId={}", payment.getId(), e);
        }
    }

    /**
     * Publish payment canceled event
     */
    public void publishPaymentCanceled(Payment payment, String reason) {
        try {
            log.info("Publishing payment canceled event: paymentId={}, reason={}", 
                    payment.getId(), reason);

            PaymentEvent event = PaymentEvent.paymentCanceled(
                    payment.getId(),
                    payment.getPaymentNumber(),
                    payment.getOrderId(),
                    payment.getUserId(),
                    payment.getAmount(),
                    payment.getCurrency().name(),
                    reason
            );

            // Publish Spring application event
            applicationEventPublisher.publishEvent(event);

            // Send notification
            notificationService.sendPaymentNotification(event);

            log.info("Payment canceled event published successfully: paymentId={}", payment.getId());

        } catch (Exception e) {
            log.error("Error publishing payment canceled event: paymentId={}", payment.getId(), e);
        }
    }

    /**
     * Publish payment requires action event
     */
    public void publishPaymentRequiresAction(Payment payment, String description) {
        try {
            log.info("Publishing payment requires action event: paymentId={}, description={}", 
                    payment.getId(), description);

            PaymentEvent event = PaymentEvent.paymentRequiresAction(
                    payment.getId(),
                    payment.getPaymentNumber(),
                    payment.getOrderId(),
                    payment.getUserId(),
                    payment.getAmount(),
                    payment.getCurrency().name(),
                    description
            );

            // Publish Spring application event
            applicationEventPublisher.publishEvent(event);

            // Send notification
            notificationService.sendPaymentNotification(event);

            log.info("Payment requires action event published successfully: paymentId={}", payment.getId());

        } catch (Exception e) {
            log.error("Error publishing payment requires action event: paymentId={}", payment.getId(), e);
        }
    }

    /**
     * Publish refund created event
     */
    public void publishRefundCreated(Refund refund) {
        try {
            log.info("Publishing refund created event: refundId={}, refundNumber={}", 
                    refund.getId(), refund.getRefundNumber());

            RefundEvent event = RefundEvent.refundCreated(
                    refund.getId(),
                    refund.getRefundNumber(),
                    refund.getPayment().getId(),
                    refund.getPayment().getPaymentNumber(),
                    refund.getPayment().getOrderId(),
                    refund.getPayment().getUserId(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    refund.getRefundType()
            );

            // Publish Spring application event
            applicationEventPublisher.publishEvent(event);

            // Send notification
            notificationService.sendRefundNotification(event);

            log.info("Refund created event published successfully: refundId={}", refund.getId());

        } catch (Exception e) {
            log.error("Error publishing refund created event: refundId={}", refund.getId(), e);
        }
    }

    /**
     * Publish refund succeeded event
     */
    public void publishRefundSucceeded(Refund refund) {
        try {
            log.info("Publishing refund succeeded event: refundId={}, refundNumber={}", 
                    refund.getId(), refund.getRefundNumber());

            RefundEvent event = RefundEvent.refundSucceeded(
                    refund.getId(),
                    refund.getRefundNumber(),
                    refund.getPayment().getId(),
                    refund.getPayment().getPaymentNumber(),
                    refund.getPayment().getOrderId(),
                    refund.getPayment().getUserId(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    refund.getRefundType()
            );

            // Publish Spring application event
            applicationEventPublisher.publishEvent(event);

            // Send notification
            notificationService.sendRefundNotification(event);

            log.info("Refund succeeded event published successfully: refundId={}", refund.getId());

        } catch (Exception e) {
            log.error("Error publishing refund succeeded event: refundId={}", refund.getId(), e);
        }
    }

    /**
     * Publish refund failed event
     */
    public void publishRefundFailed(Refund refund, String reason) {
        try {
            log.info("Publishing refund failed event: refundId={}, reason={}", 
                    refund.getId(), reason);

            RefundEvent event = RefundEvent.refundFailed(
                    refund.getId(),
                    refund.getRefundNumber(),
                    refund.getPayment().getId(),
                    refund.getPayment().getPaymentNumber(),
                    refund.getPayment().getOrderId(),
                    refund.getPayment().getUserId(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    reason
            );

            // Publish Spring application event
            applicationEventPublisher.publishEvent(event);

            // Send notification
            notificationService.sendRefundNotification(event);

            log.info("Refund failed event published successfully: refundId={}", refund.getId());

        } catch (Exception e) {
            log.error("Error publishing refund failed event: refundId={}", refund.getId(), e);
        }
    }

    /**
     * Publish refund approved event
     */
    public void publishRefundApproved(Refund refund, String approvedBy) {
        try {
            log.info("Publishing refund approved event: refundId={}, approvedBy={}", 
                    refund.getId(), approvedBy);

            RefundEvent event = RefundEvent.refundApproved(
                    refund.getId(),
                    refund.getRefundNumber(),
                    refund.getPayment().getId(),
                    refund.getPayment().getPaymentNumber(),
                    refund.getPayment().getOrderId(),
                    refund.getPayment().getUserId(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    approvedBy
            );

            // Publish Spring application event
            applicationEventPublisher.publishEvent(event);

            // Send notification
            notificationService.sendRefundNotification(event);

            log.info("Refund approved event published successfully: refundId={}", refund.getId());

        } catch (Exception e) {
            log.error("Error publishing refund approved event: refundId={}", refund.getId(), e);
        }
    }

    /**
     * Publish refund rejected event
     */
    public void publishRefundRejected(Refund refund, String reason, String rejectedBy) {
        try {
            log.info("Publishing refund rejected event: refundId={}, reason={}, rejectedBy={}", 
                    refund.getId(), reason, rejectedBy);

            RefundEvent event = RefundEvent.refundRejected(
                    refund.getId(),
                    refund.getRefundNumber(),
                    refund.getPayment().getId(),
                    refund.getPayment().getPaymentNumber(),
                    refund.getPayment().getOrderId(),
                    refund.getPayment().getUserId(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    reason,
                    rejectedBy
            );

            // Publish Spring application event
            applicationEventPublisher.publishEvent(event);

            // Send notification
            notificationService.sendRefundNotification(event);

            log.info("Refund rejected event published successfully: refundId={}", refund.getId());

        } catch (Exception e) {
            log.error("Error publishing refund rejected event: refundId={}", refund.getId(), e);
        }
    }
}
