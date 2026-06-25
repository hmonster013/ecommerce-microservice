package org.de013.notificationservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.event.*;
import org.de013.notificationservice.entity.ProcessedEvent;
import org.de013.notificationservice.repository.ProcessedEventRepository;
import org.de013.notificationservice.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "notification-service")
    @Transactional
    public void onPaymentEvent(String message) {
        log.info("Received payment event message: {}", message);
        try {
            EventEnvelope<?> env = objectMapper.readValue(message, EventEnvelope.class);
            if (processedEventRepository.existsById(env.getEventId())) {
                log.info("Skip duplicate event {}", env.getEventId());
                return;
            }

            if (EventTypes.PAYMENT_SUCCEEDED.equals(env.getEventType())) {
                PaymentSucceededPayload p = objectMapper.convertValue(env.getPayload(), PaymentSucceededPayload.class);
                String email = p.getReceiptEmail();
                if (email != null && !email.isBlank()) {
                    notificationService.sendEmail(
                        p.getUserId(),
                        email,
                        "Thanh toán thành công cho Đơn hàng #" + p.getOrderId(),
                        buildBody(p)
                    );
                } else {
                    log.warn("Cannot send email: receiptEmail is null or blank for payment order {}", p.getOrderId());
                }
            }

            processedEventRepository.save(new ProcessedEvent(env.getEventId(), LocalDateTime.now()));
            log.info("Successfully processed and saved event {}", env.getEventId());
        } catch (Exception e) {
            log.error("Error processing payment event", e);
            throw new RuntimeException("Failed to process payment event", e);
        }
    }

    private String buildBody(PaymentSucceededPayload p) {
        return "Xin chào,\n\n" +
               "Đơn hàng #" + p.getOrderId() + " của bạn đã được thanh toán thành công.\n" +
               "Mã thanh toán: " + p.getPaymentNumber() + "\n" +
               "Số tiền: " + p.getAmount() + " " + p.getCurrency() + "\n\n" +
               "Cảm ơn bạn đã mua sắm tại cửa hàng của chúng tôi!";
    }
}
