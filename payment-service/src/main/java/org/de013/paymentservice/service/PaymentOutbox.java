package org.de013.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.de013.common.event.PaymentEventType;
import org.de013.common.event.PaymentSucceeded;
import org.de013.common.event.PaymentTransitionEvent;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final boolean enabled;

    public PaymentOutbox(JdbcTemplate jdbc, ObjectMapper mapper, @Value("${payment.events.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.enabled = enabled;
    }

    public boolean enabled() { return enabled; }

    /** Caller must hold the order guard and supply a freshly loaded managed payment. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean transition(Payment payment, PaymentStatus next, boolean held) {
        if (next == null || payment.getStatus() == null) throw new IllegalArgumentException("Payment status is required");
        PaymentStatus previous = payment.getStatus();
        if (previous == next) return false;
        payment.setStatus(next);
        payment.setRevision(Math.addExact(payment.getRevision(), 1));
        if (enabled) {
            PaymentEventType type = switch (next) {
                case SUCCEEDED -> PaymentEventType.PaymentSucceeded;
                case FAILED -> PaymentEventType.PaymentFailed;
                case CANCELED -> PaymentEventType.PaymentCanceled;
                default -> null;
            };
            if (type != null) write(payment, previous, type, held);
        }
        return true;
    }

    private void write(Payment payment, PaymentStatus previous, PaymentEventType type, boolean held) {
        String provider = payment.getGatewayName();
        String currency = payment.getCurrency() == null ? null : payment.getCurrency().name();
        if (payment.getId() == null || payment.getId() <= 0 || payment.getOrderId() == null || payment.getOrderId() <= 0
                || payment.getPaymentNumber() == null || payment.getPaymentNumber().isBlank()
                || !("STRIPE".equals(provider) || "VNPAY".equals(provider))
                || payment.getAmount() == null || payment.getAmount().signum() <= 0 || currency == null || !currency.matches("[A-Z]{3}"))
            throw new IllegalStateException("Invalid payment event identity or amount");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            Object event = type == PaymentEventType.PaymentSucceeded
                    ? new PaymentSucceeded(id.toString(), type, 1, now.toString(), payment.getId(), payment.getPaymentNumber(), payment.getOrderId(), provider, payment.getAmount(), currency)
                    : new PaymentTransitionEvent(id.toString(), type, 1, now.toString(), payment.getId(), payment.getPaymentNumber(), payment.getOrderId(), provider, payment.getAmount(), currency, payment.getRevision(), previous.name(), payment.getStatus().name());
            String payload = mapper.writeValueAsString(event);
            int inserted = jdbc.update("INSERT INTO payment_outbox(event_id,payment_id,event_type,payload,created_at,revision,state) VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                    id, payment.getId(), type.name(), payload, java.sql.Timestamp.from(now), payment.getRevision(), held ? "RECONCILIATION" : "PENDING");
            if (inserted == 0) {
                if (type != PaymentEventType.PaymentSucceeded) throw new IllegalStateException("Conflicting payment event revision");
                var existing = mapper.readTree(jdbc.queryForObject("SELECT payload FROM payment_outbox WHERE payment_id=? AND event_type=?", String.class, payment.getId(), "PaymentSucceeded"));
                if (existing.path("paymentId").asLong() != payment.getId() || existing.path("orderId").asLong() != payment.getOrderId()
                        || !payment.getPaymentNumber().equals(existing.path("paymentNumber").asText())
                        || !provider.equals(existing.path("provider").asText()) || !currency.equals(existing.path("currency").asText())
                        || !existing.path("amount").isNumber() || existing.path("amount").decimalValue().compareTo(payment.getAmount()) != 0)
                    throw new IllegalStateException("Conflicting payment event identity");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize payment event", e);
        }
    }

}
