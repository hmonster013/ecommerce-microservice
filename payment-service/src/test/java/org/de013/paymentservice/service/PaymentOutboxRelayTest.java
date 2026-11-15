package org.de013.paymentservice.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"spring.cloud.bus.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true", "payment.events.enabled=true", "payment.events.relay-delay-ms=3600000"})
@ActiveProfiles("test")
class PaymentOutboxRelayTest {
    @Container static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17-alpine");
    @Container static KafkaContainer broker = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
    @DynamicPropertySource static void config(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", db::getJdbcUrl); p.add("spring.datasource.username", db::getUsername);
        p.add("spring.datasource.password", db::getPassword); p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        p.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        p.add("spring.kafka.bootstrap-servers", broker::getBootstrapServers);
    }
    @Autowired HostedCheckoutStore store;
    @Autowired PaymentOutboxRelay relay;
    @Autowired PaymentOrderGuard guard;
    @Autowired PlatformTransactionManager tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.kafka.core.KafkaTemplate<String, String> kafka;
    @MockBean org.de013.paymentservice.client.HostedOrderClient orders;

    private long create(long order) {
        var checkout = store.reserve(order, "owner", new BigDecimal("25.00"), "https://fixture.test/checkout/result");
        store.record(order, 0, "cs_" + order, "PAID", "pi_" + order);
        return checkout.getPaymentId();
    }
    private String state(long id) { return jdbc.queryForObject("select state from payment_outbox where payment_id=?", String.class, id); }
    private KafkaConsumer<String, String> consumer() {
        var consumer = new KafkaConsumer<String, String>(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(), ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(java.util.List.of("payment.succeeded.v1")); return consumer;
    }
    @Test void brokerReceivesStablePayloadAndPublishedOnlyAfterAck() throws Exception {
        long id = create(99301); String payload = jdbc.queryForObject("select payload from payment_outbox where payment_id=?", String.class, id);
        relay.relay(); relay.relay();
        assertEquals("PUBLISHED", state(id));
        try (var consumer = consumer()) {
            boolean found = false; long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (!found && System.nanoTime() < deadline) for (var record : consumer.poll(Duration.ofSeconds(1)))
                if (record.key().equals("99301")) {
                    assertEquals(payload, record.value());
                    String capture = System.getProperty("payment.contract.capture");
                    if (capture != null) {
                        var path = java.nio.file.Path.of(capture);
                        if (!path.isAbsolute()) throw new IllegalArgumentException("Contract capture path must be absolute");
                        java.nio.file.Files.writeString(path, record.value(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                    found = true;
                }
            assertTrue(found);
        }
    }
    @Test void heldEventIsRetainedForReconciliation() {
        long id = create(99302);
        new TransactionTemplate(tx).executeWithoutResult(s -> guard.hold(99302L));
        relay.relay(); relay.relay();
        assertEquals("RECONCILIATION", state(id));
    }

    private java.util.List<String> records(String key, int expected) {
        var values = new java.util.ArrayList<String>();
        try (var consumer = consumer()) {
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (values.size() < expected && System.nanoTime() < deadline)
                for (var record : consumer.poll(Duration.ofMillis(500))) if (key.equals(record.key())) values.add(record.value());
        }
        return values;
    }

    @Test void concurrentRelayInstancesPublishOnce() throws Exception {
        long id = create(99303);
        var other = new PaymentOutboxRelay(jdbc, guard, kafka, tx);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> { start.await(); relay.relay(); return null; });
            var b = executor.submit(() -> { start.await(); other.relay(); return null; });
            start.countDown(); a.get(30, java.util.concurrent.TimeUnit.SECONDS); b.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("PUBLISHED", state(id));
            assertEquals(1, records("99303", 2).size());
        } finally { executor.shutdownNow(); }
    }

    @Test void acknowledgedRecordIsRedeliveredWithIdenticalEventAfterDatabaseFailure() {
        long id = create(99304);
        jdbc.execute("create function fail_publish_99304() returns trigger language plpgsql as $$ begin if new.payment_id = " + id + " and new.state = 'PUBLISHED' then raise exception 'injected'; end if; return new; end $$");
        jdbc.execute("create trigger fail_publish_99304 before update on payment_outbox for each row execute function fail_publish_99304()");
        try { relay.relay(); assertEquals("PENDING", state(id)); }
        finally { jdbc.execute("drop trigger fail_publish_99304 on payment_outbox"); jdbc.execute("drop function fail_publish_99304()"); }
        relay.relay(); relay.relay();
        assertEquals("PUBLISHED", state(id));
        var records = records("99304", 2);
        assertEquals(2, records.size()); assertEquals(records.get(0), records.get(1));
    }

    @Test void unavailableBrokerRetainsPendingAndRecoveryPublishes() {
        long id = create(99401);
        String payload = jdbc.queryForObject("select payload from payment_outbox where payment_id=?", String.class, id);
        var props = new java.util.HashMap<String, Object>();
        props.put("bootstrap.servers", "127.0.0.1:1");
        props.put("key.serializer", org.apache.kafka.common.serialization.StringSerializer.class);
        props.put("value.serializer", org.apache.kafka.common.serialization.StringSerializer.class);
        props.put("max.block.ms", 1000);
        var factory = new org.springframework.kafka.core.DefaultKafkaProducerFactory<String, String>(props);
        try {
            new PaymentOutboxRelay(jdbc, guard, new org.springframework.kafka.core.KafkaTemplate<>(factory), tx).relay();
            assertEquals("PENDING", state(id));
            assertEquals(payload, jdbc.queryForObject("select payload from payment_outbox where payment_id=?", String.class, id));
        } finally { factory.destroy(); }
        new PaymentOutboxRelay(jdbc, guard, kafka, tx).relay();
        assertEquals("PUBLISHED", state(id)); assertEquals(payload, records("99401", 1).get(0));
    }

    @Test void publishedAckHoldsGuardUntilCommitAndCannotBeRecalled() throws Exception {
        long id = create(99402);
        var accepted = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        @SuppressWarnings("unchecked") var spy = org.mockito.Mockito.spy(kafka);
        org.mockito.Mockito.doAnswer(inv -> {
            @SuppressWarnings("unchecked") var actual = (java.util.concurrent.CompletableFuture<org.springframework.kafka.support.SendResult<String, String>>) inv.callRealMethod();
            return actual.thenApply(result -> {
                accepted.countDown();
                try { if (!release.await(30, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return result;
            });
        }).when(spy).send(org.mockito.ArgumentMatchers.eq("payment.succeeded.v1"), org.mockito.ArgumentMatchers.eq("99402"), org.mockito.ArgumentMatchers.anyString());
        var instance = new PaymentOutboxRelay(jdbc, guard, spy, tx);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var dispatch = pool.submit(instance::relay);
            assertTrue(accepted.await(20, java.util.concurrent.TimeUnit.SECONDS));
            var started = new java.util.concurrent.CountDownLatch(1);
            var hold = pool.submit(() -> new TransactionTemplate(tx).executeWithoutResult(s -> { started.countDown(); guard.hold(99402L); }));
            assertTrue(started.await(10, java.util.concurrent.TimeUnit.SECONDS));
            long end = System.nanoTime() + Duration.ofSeconds(10).toNanos(); boolean waiting = false;
            while (!waiting && System.nanoTime() < end)
                waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%payment_order_guard%'", Integer.class) > 0;
            assertTrue(waiting); assertFalse(hold.isDone());
            release.countDown(); dispatch.get(30, java.util.concurrent.TimeUnit.SECONDS); hold.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("PUBLISHED", state(id));
            assertTrue(jdbc.queryForObject("select reconciliation_hold from payment_order_guard where order_id=99402", Boolean.class));
            assertEquals(1, records("99402", 1).size());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void failedFirstBatchDoesNotStarveLaterRowsAndCursorWraps() {
        for (int i = 0; i < 51; i++) create(99500L + i);
        jdbc.update("update payment_outbox set event_id = (lpad(to_hex(payment_id), 32, '0'))::uuid where payment_id in (select id from payments where order_id between 99500 and 99550)");
        var ids = jdbc.queryForList("select o.payment_id from payment_outbox o join payments p on p.id=o.payment_id where p.order_id between 99500 and 99550 order by o.event_id", Long.class);
        long last = ids.get(50);
        jdbc.execute("create function fail_first_batch() returns trigger language plpgsql as $$ begin if new.payment_id <> " + last + " and new.state='PUBLISHED' and new.payment_id in (select id from payments where order_id between 99500 and 99550) then raise exception 'injected batch'; end if; return new; end $$");
        jdbc.execute("create trigger fail_first_batch before update on payment_outbox for each row execute function fail_first_batch()");
        var instance = new PaymentOutboxRelay(jdbc, guard, kafka, tx);
        try {
            instance.relay(); assertEquals("PENDING", state(last));
            instance.relay(); assertEquals("PUBLISHED", state(last));
        } finally { jdbc.execute("drop trigger fail_first_batch on payment_outbox"); jdbc.execute("drop function fail_first_batch()"); }
        instance.relay(); instance.relay();
        for (long id : ids) assertEquals("PUBLISHED", state(id));
    }
}
