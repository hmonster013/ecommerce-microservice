package org.de013.paymentservice.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
        var candidates = jdbc.query("select o.event_id,p.order_id from payment_outbox o join payments p on p.id=o.payment_id where o.state='PENDING' and o.event_id > ? order by o.event_id limit 50",
                (rs, row) -> new Candidate(rs.getObject(1, UUID.class), rs.getLong(2)), cursor);
        if (candidates.isEmpty()) { cursor = new UUID(0, 0); return; }
        for (var candidate : candidates) {
            cursor = candidate.id();
            try {
                transaction.executeWithoutResult(status -> dispatch(candidate));
            } catch (RuntimeException e) {
                log.warn("Payment event {} dispatch failed; retained for retry", candidate.id(), e);
            }
        }
    }

    private void dispatch(Candidate candidate) {
        boolean held = guard.lock(candidate.orderId());
        var payloads = jdbc.query("select payload from payment_outbox where event_id=? and state='PENDING' for update skip locked",
                (rs, row) -> rs.getString(1), candidate.id());
        if (payloads.isEmpty()) return;
        if (held) {
            jdbc.update("update payment_outbox set state='RECONCILIATION' where event_id=?", candidate.id());
            return;
        }
        try {
            kafka.send("payment.succeeded.v1", Long.toString(candidate.orderId()), payloads.get(0)).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment event dispatch interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Payment event broker acknowledgement failed", e);
        }
        jdbc.update("update payment_outbox set state='PUBLISHED' where event_id=?", candidate.id());
    }

    private record Candidate(UUID id, long orderId) { }

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
