package org.de013.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Test
    void sendEmail_ShouldInvokeMailSender() {
        String to = "user@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        assertDoesNotThrow(() -> emailService.sendEmail(to, subject, body));

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void isValidEmail_WhenValid_ShouldReturnTrue() {
        assertTrue(emailService.isValidEmail("john@example.com"));
        assertTrue(emailService.isValidEmail("john.doe+test@domain.co.uk"));
    }

    @Test
    void isValidEmail_WhenInvalid_ShouldReturnFalse() {
        assertFalse(emailService.isValidEmail(null));
        assertFalse(emailService.isValidEmail("invalid-email"));
        assertFalse(emailService.isValidEmail("john@example"));
    }
}
