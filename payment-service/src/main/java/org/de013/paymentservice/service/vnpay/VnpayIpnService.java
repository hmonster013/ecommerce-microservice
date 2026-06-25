package org.de013.paymentservice.service.vnpay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.event.EventTypes;
import org.de013.common.event.PaymentFailedPayload;
import org.de013.common.event.PaymentSucceededPayload;
import org.de013.common.event.Topics;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.ProcessedStripeEvent;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.outbox.OutboxWriter;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.repository.ProcessedStripeEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnpayIpnService {

    private final PaymentRepository paymentRepository;
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public void confirmPayment(Payment payment, String responseCode, Map<String, String> params, String eventId) {
        if ("00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setGatewayResponse(params.toString());
            paymentRepository.save(payment);

            // Save processed event for idempotency
            processedStripeEventRepository.save(
                    ProcessedStripeEvent.builder()
                            .eventId(eventId)
                            .processedAt(LocalDateTime.now())
                            .build()
            );

            // Write outbox event for payment succeeded
            outboxWriter.write(
                    Topics.PAYMENT_EVENTS,
                    "PAYMENT",
                    payment.getPaymentNumber(),
                    EventTypes.PAYMENT_SUCCEEDED,
                    PaymentSucceededPayload.builder()
                            .orderId(payment.getOrderId())
                            .paymentId(payment.getId())
                            .paymentNumber(payment.getPaymentNumber())
                            .userId(payment.getUserId())
                            .amount(payment.getAmount())
                            .currency(payment.getCurrency().name())
                            .receiptEmail(payment.getReceiptEmail())
                            .build()
            );

            log.info("VNPay payment {} confirmed successfully. Saved payment + processed-event + written outbox in single transaction.", payment.getPaymentNumber());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("VNPay failed with response code: " + responseCode);
            paymentRepository.save(payment);

            // Write outbox event for payment failed
            outboxWriter.write(
                    Topics.PAYMENT_EVENTS,
                    "PAYMENT",
                    payment.getPaymentNumber(),
                    EventTypes.PAYMENT_FAILED,
                    PaymentFailedPayload.builder()
                            .orderId(payment.getOrderId())
                            .paymentNumber(payment.getPaymentNumber())
                            .userId(payment.getUserId())
                            .failureReason(payment.getFailureReason())
                            .build()
            );

            log.info("VNPay payment {} marked as failed. Saved payment + written outbox in single transaction.", payment.getPaymentNumber());
        }
    }
}
