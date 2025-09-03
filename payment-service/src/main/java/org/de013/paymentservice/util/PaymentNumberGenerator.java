package org.de013.paymentservice.util;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for generating unique payment numbers
 */
@Component
public class PaymentNumberGenerator {

    private static final String PREFIX = "PAY";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    /**
     * Generate a unique payment number
     * Format: PAY-YYYYMMDD-HHMMSS-XXXX
     * Example: PAY-20241203-143052-1234
     */
    public String generatePaymentNumber() {
        LocalDateTime now = LocalDateTime.now();
        String datePart = now.format(DATE_FORMAT);
        String timePart = now.format(TIME_FORMAT);
        String randomPart = String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 9999));
        
        return String.format("%s-%s-%s-%s", PREFIX, datePart, timePart, randomPart);
    }

    /**
     * Generate a unique refund number
     * Format: REF-YYYYMMDD-HHMMSS-XXXX
     * Example: REF-20241203-143052-1234
     */
    public String generateRefundNumber() {
        LocalDateTime now = LocalDateTime.now();
        String datePart = now.format(DATE_FORMAT);
        String timePart = now.format(TIME_FORMAT);
        String randomPart = String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 9999));
        
        return String.format("REF-%s-%s-%s", datePart, timePart, randomPart);
    }

    /**
     * Generate a unique transaction number
     * Format: TXN-YYYYMMDD-HHMMSS-XXXX
     * Example: TXN-20241203-143052-1234
     */
    public String generateTransactionNumber() {
        LocalDateTime now = LocalDateTime.now();
        String datePart = now.format(DATE_FORMAT);
        String timePart = now.format(TIME_FORMAT);
        String randomPart = String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 9999));
        
        return String.format("TXN-%s-%s-%s", datePart, timePart, randomPart);
    }

    /**
     * Validate payment number format
     */
    public boolean isValidPaymentNumber(String paymentNumber) {
        if (paymentNumber == null || paymentNumber.trim().isEmpty()) {
            return false;
        }
        
        return paymentNumber.matches("^PAY-\\d{8}-\\d{6}-\\d{4}$");
    }

    /**
     * Validate refund number format
     */
    public boolean isValidRefundNumber(String refundNumber) {
        if (refundNumber == null || refundNumber.trim().isEmpty()) {
            return false;
        }
        
        return refundNumber.matches("^REF-\\d{8}-\\d{6}-\\d{4}$");
    }

    /**
     * Extract date from payment number
     */
    public LocalDateTime extractDateFromPaymentNumber(String paymentNumber) {
        if (!isValidPaymentNumber(paymentNumber)) {
            return null;
        }
        
        try {
            String[] parts = paymentNumber.split("-");
            String datePart = parts[1];
            String timePart = parts[2];
            
            String dateTimeString = datePart + timePart;
            return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        } catch (Exception e) {
            return null;
        }
    }
}
