package org.de013.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Base class for refund-related events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundEvent {
    
    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;
    private String source;
    private String version;
    
    // Refund details
    private Long refundId;
    private String refundNumber;
    private Long paymentId;
    private String paymentNumber;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String previousStatus;
    private String refundType; // FULL, PARTIAL
    
    // User details
    private String userEmail;
    private String userName;
    
    // Order details
    private String orderNumber;
    
    // Refund details
    private String refundReason;
    private String initiatedBy;
    private BigDecimal originalPaymentAmount;
    private BigDecimal totalRefundedAmount;
    private BigDecimal remainingRefundableAmount;
    
    // Additional metadata
    private Map<String, Object> metadata;
    private String description;
    
    // Notification preferences
    private boolean sendEmailNotification;
    private boolean sendSmsNotification;
    private boolean sendPushNotification;
    
    // Event types
    public static final String REFUND_CREATED = "refund.created";
    public static final String REFUND_PROCESSING = "refund.processing";
    public static final String REFUND_SUCCEEDED = "refund.succeeded";
    public static final String REFUND_FAILED = "refund.failed";
    public static final String REFUND_CANCELED = "refund.canceled";
    public static final String REFUND_APPROVED = "refund.approved";
    public static final String REFUND_REJECTED = "refund.rejected";
    
    // Factory methods for common events
    public static RefundEvent refundCreated(Long refundId, String refundNumber, Long paymentId, 
                                          String paymentNumber, Long orderId, Long userId, 
                                          BigDecimal amount, String currency, String refundType) {
        return RefundEvent.builder()
                .eventId(generateEventId())
                .eventType(REFUND_CREATED)
                .timestamp(LocalDateTime.now())
                .source("payment-service")
                .version("1.0")
                .refundId(refundId)
                .refundNumber(refundNumber)
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status("PENDING")
                .refundType(refundType)
                .sendEmailNotification(true)
                .sendSmsNotification(false)
                .sendPushNotification(true)
                .build();
    }
    
    public static RefundEvent refundSucceeded(Long refundId, String refundNumber, Long paymentId, 
                                            String paymentNumber, Long orderId, Long userId, 
                                            BigDecimal amount, String currency, String refundType) {
        return RefundEvent.builder()
                .eventId(generateEventId())
                .eventType(REFUND_SUCCEEDED)
                .timestamp(LocalDateTime.now())
                .source("payment-service")
                .version("1.0")
                .refundId(refundId)
                .refundNumber(refundNumber)
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status("SUCCEEDED")
                .previousStatus("PROCESSING")
                .refundType(refundType)
                .sendEmailNotification(true)
                .sendSmsNotification(true)
                .sendPushNotification(true)
                .build();
    }
    
    public static RefundEvent refundFailed(Long refundId, String refundNumber, Long paymentId, 
                                         String paymentNumber, Long orderId, Long userId, 
                                         BigDecimal amount, String currency, String reason) {
        return RefundEvent.builder()
                .eventId(generateEventId())
                .eventType(REFUND_FAILED)
                .timestamp(LocalDateTime.now())
                .source("payment-service")
                .version("1.0")
                .refundId(refundId)
                .refundNumber(refundNumber)
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status("FAILED")
                .previousStatus("PROCESSING")
                .refundReason(reason)
                .sendEmailNotification(true)
                .sendSmsNotification(false)
                .sendPushNotification(true)
                .build();
    }
    
    public static RefundEvent refundApproved(Long refundId, String refundNumber, Long paymentId, 
                                           String paymentNumber, Long orderId, Long userId, 
                                           BigDecimal amount, String currency, String approvedBy) {
        return RefundEvent.builder()
                .eventId(generateEventId())
                .eventType(REFUND_APPROVED)
                .timestamp(LocalDateTime.now())
                .source("payment-service")
                .version("1.0")
                .refundId(refundId)
                .refundNumber(refundNumber)
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status("APPROVED")
                .previousStatus("PENDING")
                .initiatedBy(approvedBy)
                .sendEmailNotification(true)
                .sendSmsNotification(false)
                .sendPushNotification(true)
                .build();
    }
    
    public static RefundEvent refundRejected(Long refundId, String refundNumber, Long paymentId, 
                                           String paymentNumber, Long orderId, Long userId, 
                                           BigDecimal amount, String currency, String reason, String rejectedBy) {
        return RefundEvent.builder()
                .eventId(generateEventId())
                .eventType(REFUND_REJECTED)
                .timestamp(LocalDateTime.now())
                .source("payment-service")
                .version("1.0")
                .refundId(refundId)
                .refundNumber(refundNumber)
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status("REJECTED")
                .previousStatus("PENDING")
                .refundReason(reason)
                .initiatedBy(rejectedBy)
                .sendEmailNotification(true)
                .sendSmsNotification(false)
                .sendPushNotification(true)
                .build();
    }
    
    private static String generateEventId() {
        return "ref_evt_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
}
