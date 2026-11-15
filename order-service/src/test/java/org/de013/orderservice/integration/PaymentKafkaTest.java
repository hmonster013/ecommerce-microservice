package org.de013.orderservice.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "spring.cloud.bus.enabled=false", "eureka.client.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "order.allocation.recovery-delay-ms=3600000", "order.payment-events.enabled=true", "spring.kafka.consumer.auto-offset-reset=earliest"})
class PaymentKafkaTest {
    static final String TOPIC = "payment.succeeded.v1", GROUP = "order-payment-v1";
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");
    @Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) throws Exception {
        r.add("spring.datasource.url", DB::getJdbcUrl); r.add("spring.datasource.username", DB::getUsername); r.add("spring.datasource.password", DB::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        try (var admin = admin()) { admin.createTopics(java.util.List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get(30, TimeUnit.SECONDS); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaListenerEndpointRegistry registry;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    static AdminClient admin() { return AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers())); }
    long order() {
        return jdbc.queryForObject("INSERT INTO orders(order_number,user_id,status,order_type,created_at,updated_at,total_amount,currency,allocation_phase,shipping_first_name,shipping_last_name,shipping_street_address,shipping_city,shipping_state,shipping_postal_code,shipping_country) VALUES (?, 'owner','PENDING','STANDARD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,12.50,'USD','READY','Test','Owner','Street','City','State','12345','US') RETURNING id", Long.class, UUID.randomUUID().toString());
    }
    String event(UUID id, long order) {
        return "{\"eventId\":\"" + id + "\",\"eventType\":\"PaymentSucceeded\",\"schemaVersion\":1,\"occurredAt\":\"2026-01-01T00:00:00Z\",\"paymentId\":71,\"paymentNumber\":\"P-71\",\"orderId\":" + order + ",\"provider\":\"STRIPE\",\"amount\":12.50,\"currency\":\"USD\"}";
    }
    long send(String key, String value) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.of("bootstrap.servers", KAFKA.getBootstrapServers(), "key.serializer", "org.apache.kafka.common.serialization.StringSerializer", "value.serializer", "org.apache.kafka.common.serialization.StringSerializer"))) {
            return producer.send(new ProducerRecord<>(TOPIC, 0, key, value)).get(30, TimeUnit.SECONDS).offset();
        }
    }
    long committed() throws Exception {
        try (var admin = admin()) {
            var offsets = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            var offset = offsets.get(new TopicPartition(TOPIC, 0)); return offset == null ? 0 : offset.offset();
        }
    }
    void awaitOffset(long offset) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < end) { if (committed() >= offset) return; Thread.sleep(200); }
        fail("offset not committed: " + offset);
    }

    @Test void nulPoisonRecordsQuarantineBeforeGoodRecord() throws Exception {
        long id = order();
        long raw = send(Long.toString(id), "bad\0record");
        long escaped = send(Long.toString(id), event(UUID.randomUUID(), id).replace("P-71", "P-\\u000071"));
        UUID goodEvent = UUID.randomUUID();
        long good = send(Long.toString(id), event(goodEvent, id));
        awaitOffset(good + 1);
        for (long offset : new long[]{raw, escaped}) {
            assertEquals("INVALID_EVENT", jdbc.queryForObject("SELECT reason FROM payment_event_quarantine WHERE topic=? AND partition_id=0 AND record_offset=?", String.class, TOPIC, offset));
        }
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
        assertEquals("ACCEPTED", jdbc.queryForObject("SELECT result FROM payment_event_inbox WHERE event_id=?", String.class, goodEvent));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "payment.contract.capture", matches = ".*")
    void capturedPaymentRelayPayloadIsAcceptedAndReplayIsIdempotent() throws Exception {
        String path = System.getProperty("payment.contract.capture");
        assertFalse(path.isBlank(), "payment.contract.capture must name a capture file");
        String raw = java.nio.file.Files.readString(java.nio.file.Path.of(path), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(raw.isBlank(), "capture file must not be empty");
        var payload = mapper.readTree(raw);
        long id = payload.required("orderId").longValue();
        long paymentId = payload.required("paymentId").longValue();
        String paymentNumber = payload.required("paymentNumber").textValue();
        UUID eventId = UUID.fromString(payload.required("eventId").textValue());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders WHERE id=?", Integer.class, id));
        jdbc.update("INSERT INTO orders(id,order_number,user_id,status,order_type,created_at,updated_at,total_amount,currency,allocation_phase,shipping_first_name,shipping_last_name,shipping_street_address,shipping_city,shipping_state,shipping_postal_code,shipping_country) VALUES (?,?,'owner','PENDING','STANDARD',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?,'READY','Test','Owner','Street','City','State','12345','US')",
                id, UUID.randomUUID().toString(), payload.required("amount").decimalValue(), payload.required("currency").textValue());
        long first = send(Long.toString(id), raw); awaitOffset(first + 1);
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
        assertEquals("FULFILL_PENDING", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
        assertEquals(paymentId, jdbc.queryForObject("SELECT accepted_payment_id FROM orders WHERE id=?", Long.class, id));
        assertEquals(paymentNumber, jdbc.queryForObject("SELECT accepted_payment_number FROM orders WHERE id=?", String.class, id));
        assertEquals("ACCEPTED", jdbc.queryForObject("SELECT result FROM payment_event_inbox WHERE event_id=?", String.class, eventId));
        long replay = send(Long.toString(id), raw); awaitOffset(replay + 1);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_event_inbox WHERE event_id=?", Integer.class, eventId));
        assertEquals("FULFILL_PENDING", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
    }

    @Test void realBrokerCommitQuarantineRollbackAndRedelivery() throws Exception {
        long id = order(); UUID event = UUID.randomUUID(); String raw = event(event, id);
        long first = send(Long.toString(id), raw); awaitOffset(first + 1);
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, id));
        assertEquals("FULFILL_PENDING", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
        assertEquals(71L, jdbc.queryForObject("SELECT accepted_payment_id FROM orders WHERE id=?", Long.class, id));
        assertEquals("P-71", jdbc.queryForObject("SELECT accepted_payment_number FROM orders WHERE id=?", String.class, id));
        assertEquals("ACCEPTED", jdbc.queryForObject("SELECT result FROM payment_event_inbox WHERE event_id=?", String.class, event));
        long duplicate = send(Long.toString(id), raw); awaitOffset(duplicate + 1);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_event_inbox WHERE event_id=?", Integer.class, event));
        long bad = send("1", "{"); awaitOffset(bad + 1);
        assertEquals("INVALID_EVENT", jdbc.queryForObject("SELECT reason FROM payment_event_quarantine WHERE topic=? AND partition_id=0 AND record_offset=?", String.class, TOPIC, bad));
        long version = send(Long.toString(id), event(UUID.randomUUID(), id).replace("\"schemaVersion\":1", "\"schemaVersion\":2")); awaitOffset(version + 1);
        assertEquals("INVALID_EVENT", jdbc.queryForObject("SELECT reason FROM payment_event_quarantine WHERE topic=? AND record_offset=?", String.class, TOPIC, version));
        long amount = send(Long.toString(id), event(UUID.randomUUID(), id).replace("12.50", "13.00")); awaitOffset(amount + 1);
        assertEquals("AMOUNT_CURRENCY_MISMATCH", jdbc.queryForObject("SELECT reason FROM payment_event_quarantine WHERE topic=? AND record_offset=?", String.class, TOPIC, amount));

        long blockedId = order(); UUID blockedEvent = UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION kafka_fail_inbox_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected failure'; END $$");
        jdbc.execute("CREATE TRIGGER kafka_fail_inbox_test BEFORE UPDATE ON payment_event_inbox FOR EACH ROW EXECUTE FUNCTION kafka_fail_inbox_test()");
        long blocked;
        try {
            blocked = send(Long.toString(blockedId), event(blockedEvent, blockedId));
            Thread.sleep(4500);
            assertEquals(blocked, committed());
            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, blockedId));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM payment_event_inbox WHERE event_id=?", Integer.class, blockedEvent));
        } finally { jdbc.execute("DROP TRIGGER kafka_fail_inbox_test ON payment_event_inbox"); jdbc.execute("DROP FUNCTION kafka_fail_inbox_test()"); }
        awaitOffset(blocked + 1);
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, blockedId));

        var container = registry.getListenerContainer("order-payment-events"); assertNotNull(container);
        var stopped = new java.util.concurrent.CountDownLatch(1); container.stop(stopped::countDown); assertTrue(stopped.await(30, TimeUnit.SECONDS));
        try (var admin = admin()) { admin.alterConsumerGroupOffsets(GROUP, Map.of(new TopicPartition(TOPIC, 0), new OffsetAndMetadata(first))).all().get(30, TimeUnit.SECONDS); }
        container.start(); awaitOffset(blocked + 1);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_event_inbox WHERE event_id=?", Integer.class, event));
        assertEquals("FULFILL_PENDING", jdbc.queryForObject("SELECT allocation_phase FROM orders WHERE id=?", String.class, id));
    }
}
