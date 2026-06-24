package org.de013.notificationservice.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmsServiceTest {

    private final SmsService smsService = new SmsService();

    @Test
    void isValidPhoneNumber_WhenValid_ShouldReturnTrue() {
        assertTrue(smsService.isValidPhoneNumber("+84987654321"));
        assertTrue(smsService.isValidPhoneNumber("123456789"));
    }

    @Test
    void isValidPhoneNumber_WhenInvalid_ShouldReturnFalse() {
        assertFalse(smsService.isValidPhoneNumber(null));
        assertFalse(smsService.isValidPhoneNumber("invalid-phone"));
    }
}
