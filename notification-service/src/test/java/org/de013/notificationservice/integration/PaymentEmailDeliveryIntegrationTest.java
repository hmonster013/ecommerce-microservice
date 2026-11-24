package org.de013.notificationservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.de013.notificationservice.service.PaymentEmailIngestion;
import org.de013.notificationservice.service.PaymentEmailWorkers;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PaymentEmailDeliveryIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final KafkaContainer broker = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    PaymentEmailIngestion ingestion;
    PaymentEmailWorkers worker;
    GreenMail smtp;
    DefaultKafkaProducerFactory<String, String> producer;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("TRUNCATE payment_email_replay_audit,payment_email_dlt_outbox,payment_email_quarantine,payment_email_request,notifications RESTART IDENTITY CASCADE");
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        ingestion = new PaymentEmailIngestion(jdbc, new ObjectMapper(), jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
        smtp = new GreenMail(new ServerSetup(0, "127.0.0.1", "smtp"));
        smtp.start();
        producer = new DefaultKafkaProducerFactory<>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers(), ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        worker = worker(smtp.getSmtp().getPort(), new KafkaTemplate<>(producer));
    }
    PaymentEmailWorkers worker(int port, KafkaTemplate<String, String> kafka) {
        var mail = new MailProperties(); mail.setHost("127.0.0.1"); mail.setPort(port);
        return new PaymentEmailWorkers(jdbc, tx, kafka, new ObjectMapper(), mail, "from@example.com", 5);
    }
    @AfterEach void cleanup() { smtp.stop(); producer.destroy(); }
    void ingest() { tx.executeWithoutResult(s -> ingestion.ingest("notification.payment-email.v1", 0, 0, "12", PaymentEmailIngestionTest.JSON.getBytes(StandardCharsets.UTF_8))); }
    String status() { return jdbc.queryForObject("SELECT delivery_status FROM payment_email_request", String.class); }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }

    @Test void deliveryIsPlaintextAndPreservesReadAndSingleHistory() throws Exception {
        ingest(); jdbc.update("UPDATE notifications SET status='READ',read_at=CURRENT_TIMESTAMP");
        worker.deliver(); worker.deliver();
        assertEquals("SENT", status()); assertEquals(1, count("notifications"));
        assertEquals("READ", jdbc.queryForObject("SELECT status FROM notifications", String.class));
        assertEquals(1, smtp.getReceivedMessages().length);
        assertTrue(smtp.getReceivedMessages()[0].getContent().toString().contains("Thank you"));
        assertTrue(smtp.getReceivedMessages()[0].isMimeType("text/plain"));
    }

    @Test void twoWorkersCannotSendTheSameRowConcurrently() throws Exception {
        ingest(); var other = worker(smtp.getSmtp().getPort(), new KafkaTemplate<>(producer));
        var pool = Executors.newFixedThreadPool(2);
        try { var a = pool.submit(worker::deliver); var b = pool.submit(other::deliver); a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS); }
        finally { pool.shutdownNow(); }
        assertEquals(1, smtp.getReceivedMessages().length); assertEquals(1, count("notifications"));
    }

    @Test void transientFailureBacksOffAndRecovers() {
        ingest(); int port = smtp.getSmtp().getPort(); smtp.stop(); worker.deliver();
        assertEquals("PENDING", status());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM payment_email_request", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT next_attempt_at > now() FROM payment_email_request", Boolean.class));
        smtp = new GreenMail(new ServerSetup(port, "127.0.0.1", "smtp")); smtp.start();
        jdbc.update("UPDATE payment_email_request SET next_attempt_at=now()"); worker.deliver();
        assertEquals("SENT", status()); assertEquals(1, smtp.getReceivedMessages().length);
    }

    @Test void terminalFailurePublishesDurableDltToRealBroker() {
        ingest(); smtp.stop();
        for (int i = 0; i < 5; i++) { jdbc.update("UPDATE payment_email_request SET next_attempt_at=now()"); worker.deliver(); }
        assertEquals("FAILED", status()); assertEquals(1, count("payment_email_dlt_outbox"));
        try (var consumer = new KafkaConsumer<String, String>(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers(), ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(), ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest", ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class, ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("notification.payment-email.v1.dlt")); worker.relay();
            boolean found = false; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!found && System.nanoTime() < deadline) for (var record : consumer.poll(Duration.ofMillis(500))) if (record.value().contains("DELIVERY_FAILED")) found = true;
            assertTrue(found); assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_email_dlt_outbox WHERE published_at IS NOT NULL", Integer.class));
        }
    }

    @Test void acceptedSmtpThenDatabaseFailureCanDuplicateButHistoryStaysStable() {
        ingest(); jdbc.execute("ALTER TABLE payment_email_request ADD CONSTRAINT fail_sent CHECK (delivery_status <> 'SENT')");
        try { assertThrows(RuntimeException.class, worker::deliver); }
        finally { jdbc.execute("ALTER TABLE payment_email_request DROP CONSTRAINT fail_sent"); }
        assertEquals("PENDING", status()); worker.deliver();
        assertEquals(2, smtp.getReceivedMessages().length); assertEquals(1, count("notifications"));
    }

    @Test void unavailableDltLeavesOutboxThenRealBrokerAcceptsIt() {
        ingest(); smtp.stop();
        for (int i = 0; i < 5; i++) { jdbc.update("UPDATE payment_email_request SET next_attempt_at=now()"); worker.deliver(); }
        var bad = new DefaultKafkaProducerFactory<String, String>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:1", ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class, ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000, ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 2000, ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000));
        try { worker(1, new KafkaTemplate<>(bad)).relay(); } finally { bad.destroy(); }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_email_dlt_outbox WHERE published_at IS NULL", Integer.class));
        jdbc.update("UPDATE payment_email_dlt_outbox SET next_attempt_at=now()"); worker.relay();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_email_dlt_outbox WHERE published_at IS NOT NULL", Integer.class));
    }

    @Test void replaySqlOnlyResetsFailedAndRecordsAudit() throws Exception {
        ingest(); worker.deliver();
        String sql = new String(getClass().getResourceAsStream("/payment-email-replay.sql").readAllBytes(), StandardCharsets.UTF_8)
                .replace(":'event_id'", "'9d382afe-7447-4cc6-ad51-6ce447bd32f0'")
                .replace(":'operator_name'", "'operator'").replace(":'reason'", "'approved replay'");
        jdbc.execute(sql); assertEquals("SENT", status()); assertEquals(0, count("payment_email_replay_audit"));
        jdbc.update("UPDATE payment_email_request SET delivery_status='FAILED', attempts=5");
        jdbc.update("UPDATE notifications SET status='FAILED'");
        jdbc.execute(sql); assertEquals("PENDING", status()); assertEquals(1, count("payment_email_replay_audit"));
        assertEquals(1, count("notifications")); assertEquals(0, jdbc.queryForObject("SELECT attempts FROM payment_email_request", Integer.class));
    }

    @Test void stalledSmtpReadIsBoundedAndRecordedForRetry() throws Exception {
        ingest();
        var pool = Executors.newSingleThreadExecutor();
        try (var server = new java.net.ServerSocket(0)) {
            var accepted = pool.submit(() -> {
                try (var socket = server.accept()) { Thread.sleep(14000); }
                catch (Exception ignored) { Thread.currentThread().interrupt(); }
            });
            long start = System.nanoTime();
            worker(server.getLocalPort(), new KafkaTemplate<>(producer)).deliver();
            long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
            assertTrue(seconds < 13, "SMTP call exceeded configured timeout");
            assertEquals("PENDING", status());
            assertEquals(1, jdbc.queryForObject("SELECT attempts FROM payment_email_request", Integer.class));
            accepted.cancel(true);
        } finally { pool.shutdownNow(); }
    }

    @Test void rejectedRecipientIsTerminalOnFirstAttempt() throws Exception {
        ingest(); var pool = Executors.newSingleThreadExecutor();
        try (var server = new java.net.ServerSocket(0)) {
            var smtpServer = pool.submit(() -> {
                try (var socket = server.accept();
                     var reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                     var writer = new java.io.PrintWriter(socket.getOutputStream(), true, StandardCharsets.US_ASCII)) {
                    writer.print("220 localhost ESMTP\r\n"); writer.flush();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String response = line.startsWith("RCPT TO:") ? "550 5.1.1 No such recipient" : line.startsWith("QUIT") ? "221 Bye" : "250 OK";
                        writer.print(response + "\r\n"); writer.flush();
                        if (line.startsWith("QUIT")) break;
                    }
                } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            });
            var rejecting = worker(server.getLocalPort(), new KafkaTemplate<>(producer));
            rejecting.deliver(); smtpServer.get(15, TimeUnit.SECONDS);
            assertEquals("FAILED", status());
            assertEquals(1, jdbc.queryForObject("SELECT attempts FROM payment_email_request", Integer.class));
            assertEquals(1, count("payment_email_dlt_outbox"));
            rejecting.deliver();
            assertEquals(1, jdbc.queryForObject("SELECT attempts FROM payment_email_request", Integer.class));
        } finally { pool.shutdownNow(); }
    }
}
