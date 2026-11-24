package org.de013.notificationservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.de013.notificationservice.config.PaymentEmailKafkaConfig;
import org.de013.notificationservice.service.PaymentEmailIngestion;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PaymentEmailKafkaIntegrationTest {
    static final String TOPIC = "notification.payment-email.v1";
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    KafkaProducer<String, byte[]> producer;
    AdminClient admin;

    @org.springframework.context.annotation.Profile("payment-email-isolated-test") @org.springframework.boot.test.context.TestConfiguration @EnableTransactionManagement
    static class Beans {
        @Bean DataSource dataSource() {
            var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
            Flyway.configure().dataSource(ds).load().migrate(); return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean jakarta.validation.Validator validator() { return jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator(); }
        @Bean PaymentEmailIngestion ingestion(JdbcTemplate jdbc, jakarta.validation.Validator validator) { return new PaymentEmailIngestion(jdbc, new ObjectMapper(), validator); }
        @Bean KafkaProperties kafkaProperties() { var p = new KafkaProperties(); p.setBootstrapServers(List.of(KAFKA.getBootstrapServers())); return p; }
    }

    @BeforeEach void setup() throws Exception {
        admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()));
        try { admin.createTopics(List.of(new org.apache.kafka.clients.admin.NewTopic(TOPIC, 1, (short) 1))).all().get(20, TimeUnit.SECONDS); }
        catch (java.util.concurrent.ExecutionException ignored) { }
        var partition = new TopicPartition(TOPIC, 0);
        long end = admin.listOffsets(Map.of(partition, org.apache.kafka.clients.admin.OffsetSpec.latest())).all().get(20, TimeUnit.SECONDS).get(partition).offset();
        admin.deleteRecords(Map.of(partition, org.apache.kafka.clients.admin.RecordsToDelete.beforeOffset(end))).all().get(20, TimeUnit.SECONDS);
        try { admin.deleteConsumerGroups(List.of("notification-payment-email-v1")).all().get(20, TimeUnit.SECONDS); }
        catch (java.util.concurrent.ExecutionException e) {
            if (!(e.getCause() instanceof org.apache.kafka.common.errors.GroupIdNotFoundException)) throw e;
        }
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        var cleanup = new JdbcTemplate(ds);
        cleanup.execute("ALTER TABLE payment_email_request DROP CONSTRAINT IF EXISTS integration_fail");
        cleanup.execute("TRUNCATE payment_email_replay_audit,payment_email_dlt_outbox,payment_email_quarantine,payment_email_request,notifications RESTART IDENTITY CASCADE");
        producer = new KafkaProducer<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(), ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        startContext();
    }
    void startContext() {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("payment-email-isolated-test");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("notification.payment-email.events.enabled", "true")));
        context.register(Beans.class, PaymentEmailKafkaConfig.class); context.refresh();
        jdbc = context.getBean(JdbcTemplate.class);
    }
    @AfterEach void close() { context.close(); producer.close(); admin.close(); }
    long send(String key, byte[] data) throws Exception { return producer.send(new ProducerRecord<>(TOPIC, 0, key, data)).get(20, TimeUnit.SECONDS).offset(); }
    long committed() {
        try {
            OffsetAndMetadata offset = admin.listConsumerGroupOffsets("notification-payment-email-v1").partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS).get(new TopicPartition(TOPIC, 0));
            return offset == null ? 0 : offset.offset();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    static void await(BooleanSupplier condition) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < until) { if (condition.getAsBoolean()) return; Thread.sleep(100); }
        fail("Timed out waiting for condition");
    }
    @Test void committedOnlyAfterDatabaseRecoveryAndBadRecordsDoNotBlockNext() throws Exception {
        jdbc.execute("ALTER TABLE payment_email_request ADD CONSTRAINT integration_fail CHECK (payment_id <> 12)");
        long first = send("12", PaymentEmailIngestionTest.JSON.getBytes(StandardCharsets.UTF_8));
        Thread.sleep(7000);
        assertTrue(committed() <= first);
        try { assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class)); }
        finally { jdbc.execute("ALTER TABLE payment_email_request DROP CONSTRAINT IF EXISTS integration_fail"); }
        await(() -> committed() > first);
        send("12", PaymentEmailIngestionTest.JSON.getBytes(StandardCharsets.UTF_8));
        send("12", PaymentEmailIngestionTest.JSON.replace("Thank you", "\\u0000").getBytes(StandardCharsets.UTF_8));
        send("12", new byte[]{(byte) 0xc3, 0x28});
        send("12", "x".repeat(70000).getBytes(StandardCharsets.UTF_8));
        send("12", PaymentEmailIngestionTest.JSON.replace("Thank you", "conflict").getBytes(StandardCharsets.UTF_8));
        long last = send("12", PaymentEmailIngestionTest.JSON.getBytes(StandardCharsets.UTF_8));
        await(() -> committed() > last);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM payment_email_quarantine", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM payment_email_dlt_outbox", Integer.class));
    }

    @Test void configuredDltTemplateSerializesStrings() throws Exception {
        @SuppressWarnings("unchecked")
        var template = (org.springframework.kafka.core.KafkaTemplate<String, String>) context.getBean("paymentEmailDltTemplate");
        var result = template.send("notification.payment-email.v1.dlt", "key", "value").get(20, TimeUnit.SECONDS);
        assertEquals("notification.payment-email.v1.dlt", result.getRecordMetadata().topic());
    }

    @Test void lz4RecordRoundTripsThroughKafka() throws Exception {
        byte[] bytes = PaymentEmailIngestionTest.JSON.getBytes(StandardCharsets.UTF_8);
        try (var compressed = new KafkaProducer<String, byte[]>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class,
                ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4"));
             var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]>(Map.of(
                     org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                     org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class,
                     org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.ByteArrayDeserializer.class))) {
            long offset = compressed.send(new ProducerRecord<>(TOPIC, 0, "12", bytes)).get(20, TimeUnit.SECONDS).offset();
            var partition = new TopicPartition(TOPIC, 0);
            consumer.assign(List.of(partition));
            consumer.seek(partition, offset);
            var records = consumer.poll(java.time.Duration.ofSeconds(20)).records(partition);
            assertFalse(records.isEmpty());
            assertEquals(offset, records.get(0).offset());
            assertEquals("12", records.get(0).key());
            assertArrayEquals(bytes, records.get(0).value());
            await(() -> committed() > offset);
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_email_request WHERE event_id=?::uuid", Integer.class, "9d382afe-7447-4cc6-ad51-6ce447bd32f0"));
        }
    }

    @Test void freshGroupConsumesExistingTopicBacklog() throws Exception {
        context.close();
        try { admin.deleteConsumerGroups(List.of("notification-payment-email-v1")).all().get(20, TimeUnit.SECONDS); }
        catch (java.util.concurrent.ExecutionException e) { if (!(e.getCause() instanceof org.apache.kafka.common.errors.GroupIdNotFoundException)) throw e; }
        long offset = send("12", PaymentEmailIngestionTest.JSON.getBytes(StandardCharsets.UTF_8));
        startContext();
        await(() -> committed() > offset);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_email_request WHERE event_id=?::uuid", Integer.class, "9d382afe-7447-4cc6-ad51-6ce447bd32f0"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE status='PENDING'", Integer.class));
    }

    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "notification.contract.capture", matches = ".*")
    void producerCapturePassesUnchangedThroughKafka() throws Exception {
        Path path = Path.of(System.getProperty("notification.contract.capture"));
        assertTrue(path.isAbsolute()); byte[] bytes = Files.readAllBytes(path); assertTrue(bytes.length > 0);
        var json = new ObjectMapper().readTree(bytes);
        long offset = send(json.get("paymentId").asText(), bytes);
        await(() -> committed() > offset);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_email_request WHERE event_id=?::uuid", Integer.class, json.get("eventId").asText()));
    }
}
