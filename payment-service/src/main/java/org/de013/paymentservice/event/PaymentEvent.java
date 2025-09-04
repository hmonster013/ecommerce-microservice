package org.de013.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Base class for payment-related events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
    
    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private String source;
    private String version;
    
    // Payment details
    private Long paymentId;
    private String paymentNumber;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String previousStatus;
    
    // User details
    private String userEmail;
    private String userName;
    
    // Order details
    private String orderNumber;
    
    // Payment method details
    private String paymentMethodType;
    private String paymentMethodDetails;
    
    // Additional metadata
    private Map<String, Object> metadata;
    private String reason;
    private String description;
    
    // Notification preferences
    private boolean sendEmailNotification;
    private boolean sendSmsNotification;
    private boolean sendPushNotification;
    
    // Event types
    public static final String PAYMENT_CREATED = "payment.created";
    public static final String PAYMENT_PROCESSING = "payment.processing";
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_CANCELED = "payment.canceled";
    public static final String PAYMENT_REQUIRES_ACTION = "payment.requires_action";
    
    // Factory methods for common events
    public static PaymentEvent paymentCreated(Long paymentId, String paymentNumber, Long orderId, 
                                            Long userId, BigDecimal amount, String currency) {
        return PaymentEvent.builder()
                .eventId(generateEventId())
                .eventType(PAYMENT_CREATED)
                .timestamp(LocalDateTime.now())
                .source("payment-service")
                .version("1.0")
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status("PENDING")
                .sendEmailNotification(true)
                .sendSmsNotification(false)
                .sendPushNotification(true)
                .build();
    }
    
    public static PaymentEvent paymentSucceeded(Long paymentId, String paymentNumber, Long orderId, 
                                              Long userId, BigDecimal amount, String currency) {
        return PaymentEvent.builder()
                .eventId(generateEventId())
                .eventType(PAYMENT_SUCCEEDED)
                .timestamp(LocalDateTime.now())
                .source("payment-service")
                .version("1.0")
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status("SUCCEEDED")
                .previousStatus("PROCESSING")
                .sendEmailNotification(true)
                .sendSmsNotification(true)
                .sendPushNotification(true)
                .build();
    }
    
    public static PaymentEvent paymentFailed(Long paymentId, String paymentNumber, Long orderId, 
                                           Long userId, BigDecimal amount, String currency, String reason) {
        return PaymentEvent.builder()
                .eventId(generateEventId())
                .eventType(PAYMENT_FAILED)
                .timestamp(LocalDateTime.now())
                .source("payment-service")
                .version("1.0")
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status("FAILED")
                .previousStatus("PROCESSING")
                .reason(reason)
                .sendEmailNotification(true)
                .sendSmsNotification(false)
                .sendPushNotification(true)
                .build();
    }
    
    public static PaymentEvent paymentCanceled(Long paymentId, String paymentNumber, Long orderId, 
                                             Long userId, BigDecimal amount, String currency, String reason) {
        return PaymentEvent.builder()
                .eventId(generateEventId())
                .eventType(PAYMENT_CANCELED)
                .timestamp(LocalDateTime.now())
                .source("payment-service")
                .version("1.0")
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status("CANCELED")
                .reason(reason)
                .sendEmailNotification(true)
                .sendSmsNotification(false)
                .sendPushNotification(true)
                .build();
    }
    
    public static PaymentEvent paymentRequiresAction(Long paymentId, String paymentNumber, Long orderId, 
                                                   Long userId, BigDecimal amount, String currency, String description) {
        return PaymentEvent.builder()
                .eventId(generateEventId())
                .eventType(PAYMENT_REQUIRES_ACTION)
                .timestamp(LocalDateTime.now())
                .source("payment-service")
                .version("1.0")
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status("REQUIRES_ACTION")
                .previousStatus("PENDING")
                .description(description)
                .sendEmailNotification(true)
                .sendSmsNotification(true)
                .sendPushNotification(true)
                .build();
    }
    
    private static String generateEventId() {
        return "evt_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
}
