package org.de013.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.de013.paymentservice.entity.Payment;
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

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueue(Payment payment) {
        if (!enabled) return;
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
            String payload = mapper.writeValueAsString(new Event(id.toString(), "PaymentSucceeded", 1, now.toString(),
                    payment.getId(), payment.getPaymentNumber(), payment.getOrderId(), provider, payment.getAmount(), currency));
            int inserted = jdbc.update("INSERT INTO payment_outbox(event_id,payment_id,event_type,payload,created_at) VALUES (?,?,?,?,?) ON CONFLICT (payment_id,event_type) DO NOTHING",
                    id, payment.getId(), "PaymentSucceeded", payload, java.sql.Timestamp.from(now));
            if (inserted == 0) {
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

    private record Event(String eventId, String eventType, int schemaVersion, String occurredAt, Long paymentId,
                         String paymentNumber, Long orderId, String provider, BigDecimal amount, String currency) { }
}
