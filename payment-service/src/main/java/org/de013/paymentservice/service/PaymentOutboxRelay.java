package org.de013.paymentservice.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.de013.common.event.PaymentEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "payment.events.enabled", havingValue = "true")
@Slf4j
public class PaymentOutboxRelay {
    private final JdbcTemplate jdbc;
    private final PaymentOrderGuard guard;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private UUID cursor = new UUID(0, 0);

    public PaymentOutboxRelay(JdbcTemplate jdbc, PaymentOrderGuard guard, @org.springframework.beans.factory.annotation.Qualifier("paymentKafkaTemplate") KafkaTemplate<String, String> kafka,
                              PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.kafka = kafka;
        this.transaction = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${payment.events.relay-delay-ms:5000}")
    public synchronized void relay() {
        var candidates = jdbc.query("select o.event_id,p.order_id from payment_outbox o join payments p on p.id=o.payment_id where o.state='PENDING' and o.next_attempt_at <= current_timestamp and o.event_id > ? order by o.event_id limit 50",
                (rs, row) -> new Candidate(rs.getObject(1, UUID.class), rs.getLong(2)), cursor);
        if (candidates.isEmpty()) { cursor = new UUID(0, 0); return; }
        for (var candidate : candidates) {
            cursor = candidate.id();
            transaction.executeWithoutResult(status -> dispatch(candidate));
            if (Thread.currentThread().isInterrupted()) return;
        }
    }

    private void dispatch(Candidate candidate) {
        boolean held = guard.lock(candidate.orderId());
        var payloads = jdbc.query("select payload,event_type,traceparent,tracestate from payment_outbox where event_id=? and state='PENDING' and next_attempt_at <= current_timestamp for update skip locked",
                (rs, row) -> new Event(rs.getString(1), PaymentEventType.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4)), candidate.id());
        if (payloads.isEmpty()) return;
        if (held) {
            jdbc.update("update payment_outbox set state='RECONCILIATION' where event_id=?", candidate.id());
            return;
        }
        var event = payloads.get(0);
        try (var scope = OutboxTrace.restore(event.parent(), event.state()).makeCurrent()) {
            kafka.send(event.type().topic(), Long.toString(candidate.orderId()), event.payload()).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed(candidate, "INTERRUPTED");
            return;
        } catch (Exception e) {
            failed(candidate, "BROKER_FAILURE");
            return;
        }
        jdbc.update("update payment_outbox set state='PUBLISHED',published_at=clock_timestamp(),last_error=null where event_id=?", candidate.id());
    }

    private void failed(Candidate candidate, String code) {
        jdbc.update("update payment_outbox set attempts=attempts+1,last_error=?,next_attempt_at=clock_timestamp() + (least(60, power(2, least(attempts, 6))) * interval '1 second') where event_id=?", code, candidate.id());
    }

    private record Candidate(UUID id, long orderId) { }
    private record Event(String payload, PaymentEventType type, String parent, String state) { }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "payment.events.enabled", havingValue = "true")
    static class Scheduling {
        @Bean
        KafkaTemplate<String, String> paymentKafkaTemplate(KafkaProperties properties) {
            var config = properties.buildProducerProperties(null);
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.ACKS_CONFIG, "all");
            config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
            config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
            config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<String, String>(config));
        }
    }
}
