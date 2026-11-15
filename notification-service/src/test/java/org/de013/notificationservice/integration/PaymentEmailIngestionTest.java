package org.de013.notificationservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.notificationservice.service.PaymentEmailIngestion;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(PaymentEmailIngestionTest.Config.class)
@org.springframework.test.context.ActiveProfiles("payment-email-isolated-test")
class PaymentEmailIngestionTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    @org.springframework.context.annotation.Profile("payment-email-isolated-test") @org.springframework.boot.test.context.TestConfiguration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
            Flyway.configure().dataSource(ds).load().migrate();
            return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean jakarta.validation.Validator validator() { return jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator(); }
        @Bean PaymentEmailIngestion ingestion(JdbcTemplate jdbc, jakarta.validation.Validator validator) { return new PaymentEmailIngestion(jdbc, new ObjectMapper(), validator); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentEmailIngestion ingestion;
    static final String JSON = """
        {"eventId":"9d382afe-7447-4cc6-ad51-6ce447bd32f0","eventType":"PaymentEmailRequested","schemaVersion":1,"occurredAt":"2026-01-01T00:00:00Z","paymentId":12,"paymentNumber":"P12","orderId":2,"userId":"user-1","channel":"EMAIL","recipient":"a@example.com","subject":"Paid","content":"Thank you"}
        """;
    @BeforeEach void clean() { jdbc.execute("TRUNCATE payment_email_dlt_outbox, payment_email_quarantine, payment_email_request, notifications RESTART IDENTITY CASCADE"); }
    void ingest(long offset, String text) { ingestion.ingest("notification.payment-email.v1", 0, offset, "12", text.getBytes(StandardCharsets.UTF_8)); }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }

    @Test void validDuplicateAndConflict() {
        ingest(0, JSON); ingest(1, JSON); ingest(2, JSON.replace("Thank you", "Changed"));
        assertEquals(1, count("notifications")); assertEquals(1, count("payment_email_request"));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM notifications", String.class));
        assertEquals("EVENT_CONFLICT", jdbc.queryForObject("SELECT reason FROM payment_email_quarantine", String.class));
        assertEquals(1, count("payment_email_dlt_outbox"));
        assertEquals("Thank you", jdbc.queryForObject("SELECT content FROM notifications", String.class));
    }
    @Test void concurrentDuplicates() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> ingest(1, JSON));
            var two = executor.submit(() -> ingest(2, JSON));
            one.get(20, TimeUnit.SECONDS); two.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertEquals(1, count("notifications")); assertEquals(1, count("payment_email_request"));
    }
    @Test void invalidRecordsAreBoundedAndDurable() {
        ingest(0, "{"); ingest(1, JSON.replace("Thank you", "\\u0000"));
        ingest(2, JSON.replace("Paid", "bad\\rsubject")); ingest(3, "x".repeat(70000));
        assertEquals(4, count("payment_email_quarantine")); assertEquals(4, count("payment_email_dlt_outbox"));
        assertEquals(0, count("notifications"));
        assertEquals(70000L, jdbc.queryForObject("SELECT raw_length FROM payment_email_quarantine WHERE source_offset=3", Long.class));
        assertTrue(jdbc.queryForObject("SELECT length(raw_sanitized) FROM payment_email_quarantine WHERE source_offset=3", Integer.class) <= 4096);
    }
    @Test void databaseFailureRollsBackHistory() {
        jdbc.execute("ALTER TABLE payment_email_request ADD CONSTRAINT test_failure CHECK (payment_id <> 12)");
        try { assertThrows(RuntimeException.class, () -> ingest(0, JSON)); }
        finally { jdbc.execute("ALTER TABLE payment_email_request DROP CONSTRAINT test_failure"); }
        assertEquals(0, count("notifications")); assertEquals(0, count("payment_email_request"));
    }
    @Test void malformedUtf8IsQuarantinedAndNextRecordSucceeds() {
        ingestion.ingest("notification.payment-email.v1", 0, 1, "12", new byte[]{(byte) 0xc3, 0x28});
        ingest(2, JSON);
        assertEquals(1, count("payment_email_quarantine"));
        assertEquals(1, count("payment_email_request"));
    }

    @Test void recipientRulesMatchSharedValidatorAndRawAddressRestrictions() {
        String[] addresses = {"o'brien@example.com", "user@例え.テスト", "\"quoted\"@example.com", "Name <a@example.com>", "a@example.com,b@example.com", "a@example.com\r\nBcc:x@example.com"};
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        for (int i = 0; i < addresses.length; i++) {
            String address = addresses[i];
            int at = address.indexOf('@');
            boolean expected = !address.isBlank() && address.length() <= 254 && address.indexOf('\0') < 0 && address.indexOf('\r') < 0 && address.indexOf('\n') < 0 && at > 0 && at == address.lastIndexOf('@') && at < address.length() - 1 && address.indexOf('<') < 0 && address.indexOf('>') < 0 && address.indexOf(',') < 0 && address.codePoints().noneMatch(Character::isWhitespace) && validator.validate(new EmailValue(address)).isEmpty();
            String json;
            try { json = JSON.replace("\"a@example.com\"", new ObjectMapper().writeValueAsString(address)).replace("9d382afe-7447-4cc6-ad51-6ce447bd32f0", new java.util.UUID(0, i + 1).toString()); }
            catch (Exception e) { throw new AssertionError(e); }
            int before = count("payment_email_request"); ingest(100 + i, json);
            assertEquals(before + (expected ? 1 : 0), count("payment_email_request"), address);
        }
    }
    private record EmailValue(@jakarta.validation.constraints.Email String value) { }

}
