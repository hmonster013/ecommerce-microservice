package org.de013.notificationservice.service;

import org.de013.notificationservice.controller.NotificationController;
import org.de013.notificationservice.dto.BothNotificationRequest;
import org.de013.notificationservice.dto.EmailNotificationRequest;
import org.de013.notificationservice.dto.SmsNotificationRequest;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSubmissionTest {
    @Test
    void singleChannelsReportProviderOutcome() {
        for (boolean failed : List.of(false, true)) {
            Fixture f = new Fixture();
            if (failed) {
                doThrow(new RuntimeException()).when(f.email).sendEmail(anyString(), anyString(), anyString());
                doThrow(new RuntimeException()).when(f.sms).sendSms(anyString(), anyString());
            }
            var email = f.controller.sendEmail(new EmailNotificationRequest("u", "a@b.com", "s", "m"));
            var sms = f.controller.sendSms(new SmsNotificationRequest("u", "+12345678", "m"));
            assertEquals(failed ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.CREATED, email.getStatusCode());
            assertEquals(failed ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.CREATED, sms.getStatusCode());
            assertEquals(!failed, email.getBody().success());
            assertEquals(!failed, sms.getBody().success());
            assertEquals(1L, email.getBody().notificationId());
            assertEquals(2L, sms.getBody().notificationId());
            assertEquals(failed ? "FAILED" : "SENT", email.getBody().status());
            assertEquals(failed ? "FAILED" : "SENT", sms.getBody().status());
            f.saved.forEach(n -> assertRecord(n, failed));
        }
    }

    @Test
    void bothPreservesEachOutcome() {
        for (boolean emailFailed : List.of(false, true)) for (boolean smsFailed : List.of(false, true)) {
            Fixture f = new Fixture();
            if (emailFailed) doThrow(new RuntimeException()).when(f.email).sendEmail(anyString(), anyString(), anyString());
            if (smsFailed) doThrow(new RuntimeException()).when(f.sms).sendSms(anyString(), anyString());
            var result = f.controller.sendBoth(new BothNotificationRequest("u", "a@b.com", "+12345678", "s", "m"));
            assertEquals(emailFailed || smsFailed ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.CREATED, result.getStatusCode());
            assertEquals(!(emailFailed || smsFailed), result.getBody().success());
            assertEquals(1L, result.getBody().emailNotificationId());
            assertEquals(2L, result.getBody().smsNotificationId());
            assertEquals(emailFailed ? "FAILED" : "SENT", result.getBody().emailStatus());
            assertEquals(smsFailed ? "FAILED" : "SENT", result.getBody().smsStatus());
            assertRecord(f.saved.get(0), emailFailed);
            assertRecord(f.saved.get(1), smsFailed);
            verify(f.email, times(1)).sendEmail(anyString(), anyString(), anyString());
            verify(f.sms, times(1)).sendSms(anyString(), anyString());
        }
    }

    @Test
    void unavailableSmsConfigurationsFailWithoutSubmission() {
        for (String provider : List.of("mock", "AWS", "unknown", "TWILIO")) {
            SmsService sms = new SmsService();
            ReflectionTestUtils.setField(sms, "smsEnabled", true);
            ReflectionTestUtils.setField(sms, "mockMode", false);
            ReflectionTestUtils.setField(sms, "smsProvider", provider);
            assertThrows(IllegalStateException.class, () -> sms.sendSms("+12345678", "m"));
        }
        SmsService sms = new SmsService();
        assertThrows(IllegalStateException.class, () -> sms.sendSms("+12345678", "m"));
        ReflectionTestUtils.setField(sms, "smsEnabled", true);
        ReflectionTestUtils.setField(sms, "mockMode", true);
        assertThrows(IllegalStateException.class, () -> sms.sendSms("+12345678", "m"));
    }

    private static void assertRecord(Notification n, boolean failed) {
        assertEquals(failed ? NotificationStatus.FAILED : NotificationStatus.SENT, n.getStatus());
        if (failed) assertNull(n.getSentAt()); else assertNotNull(n.getSentAt());
    }

    private static class Fixture {
        final NotificationRepository repository = mock(NotificationRepository.class);
        final EmailService email = mock(EmailService.class);
        final SmsService sms = mock(SmsService.class);
        final List<Notification> saved = new ArrayList<>();
        final NotificationController controller;
        Fixture() {
            AtomicLong ids = new AtomicLong();
            when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
                Notification n = invocation.getArgument(0);
                if (n.getId() == null) { n.setId(ids.incrementAndGet()); saved.add(n); }
                return n;
            });
            controller = new NotificationController(new NotificationService(repository, email, sms));
        }
    }
}
