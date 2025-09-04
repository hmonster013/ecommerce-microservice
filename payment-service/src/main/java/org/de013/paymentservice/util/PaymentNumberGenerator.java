package org.de013.paymentservice.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for generating unique payment numbers and identifiers.
 */
@Slf4j
@Component
public class PaymentNumberGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final AtomicLong SEQUENCE_COUNTER = new AtomicLong(0);

    // Date formatters for different number formats
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter YYYYMMDDHHMM = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /**
     * Generates a unique payment number in format: PAY-YYYYMMDD-XXXXXX
     * Example: PAY-20241201-123456
     */
    public static String generatePaymentNumber() {
        String dateStr = LocalDateTime.now().format(YYYYMMDD);
        String sequence = String.format("%06d", getNextSequence());
        return String.format("PAY-%s-%s", dateStr, sequence);
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

    /**
     * Gets next sequence number (thread-safe).
     */
    private static long getNextSequence() {
        return SEQUENCE_COUNTER.incrementAndGet();
    }

    /**
     * Generates random alphanumeric string.
     */
    private static String generateRandomAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int index = SECURE_RANDOM.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }

        return sb.toString();
    }
}
