package org.de013.paymentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.de013.paymentservice.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentNotificationOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final boolean enabled;

    public PaymentNotificationOutbox(JdbcTemplate jdbc, ObjectMapper mapper, Validator validator,
            @Value("${payment.notifications.events.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.validator = validator;
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Payment payment) {
        if (!enabled) return;
        if (payment.getId() == null || payment.getId() <= 0 || payment.getOrderId() == null || payment.getOrderId() <= 0
                || !text(payment.getPaymentNumber()) || payment.getPaymentNumber().length() > 16384 || payment.getAmount() == null || payment.getAmount().signum() <= 0
                || payment.getCurrency() == null) throw new IllegalStateException("Invalid payment notification identity or amount");
        String recipient = payment.getReceiptEmail();
        String user = payment.getUserId();
        boolean valid = text(user) && user.length() <= 255 && text(recipient) && recipient.length() <= 254
                && !recipient.contains("\r") && !recipient.contains("\n")
                && !recipient.contains("<") && !recipient.contains(">") && !recipient.contains(",")
                && recipient.indexOf('@') > 0 && recipient.indexOf('@') == recipient.lastIndexOf('@')
                && recipient.indexOf('@') < recipient.length() - 1
                && recipient.chars().noneMatch(Character::isWhitespace)
                && validator.validateValue(Recipient.class, "value", recipient).isEmpty();
        if (!text(user) || user.length() > 255) user = null;
        if (!text(recipient) || recipient.contains("\r") || recipient.contains("\n")) recipient = null;
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String subject = "Payment successful for order #" + payment.getOrderId();
        String content = "Payment " + payment.getPaymentNumber() + " for order #" + payment.getOrderId()
                + " succeeded. Amount: " + payment.getAmount().toPlainString() + " " + payment.getCurrency().name();
        if (content.length() > 16384) throw new IllegalStateException("Payment notification content too long");
        try {
            String payload = mapper.writeValueAsString(new Event(id.toString(), "PaymentEmailRequested", 1, now.toString(),
                    payment.getId(), payment.getPaymentNumber(), payment.getOrderId(), user, "EMAIL", recipient, subject, content));
            var trace = OutboxTrace.capture();
            int inserted = jdbc.update("INSERT INTO payment_notification_outbox(event_id,payment_id,notification_type,payload,state,created_at,traceparent,tracestate) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (payment_id,notification_type) DO NOTHING",
                    id, payment.getId(), "EMAIL", payload, valid ? "PENDING" : "RECONCILIATION", Timestamp.from(now), trace.get("traceparent"), trace.get("tracestate"));
            if (inserted == 0) {
                var existing = mapper.readTree(jdbc.queryForObject("select payload from payment_notification_outbox where payment_id=? and notification_type='EMAIL'", String.class, payment.getId()));
                if (existing.path("paymentId").asLong() != payment.getId() || existing.path("orderId").asLong() != payment.getOrderId()
                        || !payment.getPaymentNumber().equals(existing.path("paymentNumber").asText()))
                    throw new IllegalStateException("Conflicting payment notification identity");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize payment notification", e);
        }
    }

    private static boolean text(String value) { return value != null && !value.isBlank() && value.indexOf('\0') < 0; }

    private static class Recipient { @Email String value; }

    private record Event(String eventId, String eventType, int schemaVersion, String occurredAt, Long paymentId,
            String paymentNumber, Long orderId, String userId, String channel, String recipient, String subject, String content) { }
}
