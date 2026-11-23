package org.de013.notificationservice.integration;

import org.de013.notificationservice.controller.NotificationController;
import org.de013.notificationservice.dto.BothNotificationRequest;
import org.de013.notificationservice.dto.SmsNotificationRequest;
import org.de013.notificationservice.service.EmailService;
import org.de013.notificationservice.service.NotificationService;
import org.de013.notificationservice.service.SmsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.test.database.replace=none", "app.notification.sms.enabled=false"})
@Import({NotificationService.class, SmsService.class, NotificationController.class})
@ContextConfiguration(classes = NotificationSubmissionPostgresTest.TestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationSubmissionPostgresTest {
    @SpringBootApplication(scanBasePackages = "org.de013.notificationservice.integration.empty")
    @EntityScan("org.de013.notificationservice.entity")
    @EnableJpaRepositories("org.de013.notificationservice.repository")
    static class TestApplication { }
    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired NotificationController controller;
    @MockitoBean EmailService email;
    @MockitoSpyBean SmsService sms;

    private JdbcTemplate externalJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
    }

    @Test
    void partialResultsAreCommittedAndReturnedWithIds() {
        var request = new BothNotificationRequest("u", "a@b.com", "+12345678", "subject", "message");
        // The real disabled SMS implementation must fail, not pretend to submit.
        var first = controller.sendBoth(request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, first.getStatusCode());
        assertFalse(first.getBody().success());
        assertEquals("SENT", first.getBody().emailStatus());
        assertEquals("FAILED", first.getBody().smsStatus());
        assertRow(first.getBody().emailNotificationId(), "SENT");
        assertRow(first.getBody().smsNotificationId(), "FAILED");

        doThrow(new RuntimeException("provider failure")).when(email).sendEmail(anyString(), anyString(), anyString());
        doNothing().when(sms).sendSms(anyString(), anyString());
        var second = controller.sendBoth(request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, second.getStatusCode());
        assertFalse(second.getBody().success());
        assertEquals("FAILED", second.getBody().emailStatus());
        assertEquals("SENT", second.getBody().smsStatus());
        assertRow(second.getBody().emailNotificationId(), "FAILED");
        assertRow(second.getBody().smsNotificationId(), "SENT");
    }

    @Test
    void disabledSmsPersistsFailureForSingleRequest() {
        var result = controller.sendSms(new SmsNotificationRequest("u", "+12345678", "message"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertFalse(result.getBody().success());
        assertEquals("FAILED", result.getBody().status());
        assertRow(result.getBody().notificationId(), "FAILED");
    }

    @Test
    void databaseFailureIsTechnicalErrorNotPartialResult() {
        JdbcTemplate jdbc = externalJdbc();
        jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT reject_submission CHECK (recipient <> 'failure@example.com')");
        try {
            var result = controller.sendBoth(new BothNotificationRequest("u", "failure@example.com", "+12345678", "subject", "message"));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
            assertFalse(result.getBody().success());
            assertNull(result.getBody().emailNotificationId());
            assertNull(result.getBody().smsNotificationId());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient = 'failure@example.com'", Integer.class));
        } finally {
            jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT reject_submission");
        }
    }

    @Test
    void smsInsertFailureRollsBackRecordsAfterEmailSubmission() {
        JdbcTemplate jdbc = externalJdbc();
        jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT reject_sms CHECK (recipient <> '+19876543210')");
        try {
            var result = controller.sendBoth(new BothNotificationRequest("u", "late@example.com", "+19876543210", "subject", "message"));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
            assertFalse(result.getBody().success());
            assertNull(result.getBody().emailNotificationId());
            assertNull(result.getBody().smsNotificationId());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient IN ('late@example.com', '+19876543210')", Integer.class));
            verify(email, times(1)).sendEmail("late@example.com", "subject", "message");
            verify(sms, never()).sendSms(anyString(), anyString());
        } finally {
            jdbc.execute("ALTER TABLE notifications DROP CONSTRAINT reject_sms");
        }
    }

    private void assertRow(Long id, String status) {
        assertNotNull(id);
        var row = externalJdbc().queryForMap("SELECT status, sent_at FROM notifications WHERE id = ?", id);
        assertEquals(status, row.get("status"));
        if ("SENT".equals(status)) assertNotNull(row.get("sent_at"));
        else assertNull(row.get("sent_at"));
    }
}
