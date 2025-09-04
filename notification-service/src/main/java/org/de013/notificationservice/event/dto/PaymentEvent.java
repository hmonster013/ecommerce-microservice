package org.de013.notificationservice.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment event DTO for notification processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    private Long paymentId;
    private Long orderId;
    private Long userId;
    private String orderNumber;
    private String paymentReference;
    private PaymentStatus status;
    private PaymentMethod method;
    private BigDecimal amount;
    private String currency;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String failureReason;
    private String transactionId;
    private String gatewayResponse;
    private Map<String, Object> metadata;
    private String eventType; // PAYMENT_CONFIRMED, PAYMENT_FAILED, PAYMENT_REFUNDED
    private String correlationId;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paymentDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTimestamp;

    public enum PaymentStatus {
        PENDING,
        PROCESSING,
        CONFIRMED,
        FAILED,
        CANCELLED,
        REFUNDED,
        PARTIALLY_REFUNDED
    }

    public enum PaymentMethod {
        CREDIT_CARD,
        DEBIT_CARD,
        PAYPAL,
        BANK_TRANSFER,
        DIGITAL_WALLET,
        CASH_ON_DELIVERY
    }

    /**
     * Get notification template variables
     */
    public Map<String, Object> getTemplateVariables() {
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("paymentId", paymentId);
        variables.put("orderId", orderId);
        variables.put("orderNumber", orderNumber != null ? orderNumber : "");
        variables.put("paymentReference", paymentReference != null ? paymentReference : "");
        variables.put("customerName", customerName);
        variables.put("amount", amount);
        variables.put("currency", currency);
        variables.put("paymentMethod", method.name());
        variables.put("paymentDate", paymentDate);
        variables.put("transactionId", transactionId != null ? transactionId : "");
        variables.put("failureReason", failureReason != null ? failureReason : "");
        return variables;
    }

    /**
     * Get notification priority based on payment status
     */
    public String getNotificationPriority() {
        return switch (status) {
            case FAILED, CANCELLED -> "HIGH";
            case CONFIRMED -> "NORMAL";
            case REFUNDED, PARTIALLY_REFUNDED -> "NORMAL";
            default -> "LOW";
        };
    }

    /**
     * Get notification type based on event
     */
    public String getNotificationType() {
        return switch (eventType) {
            case "PAYMENT_CONFIRMED" -> "PAYMENT_CONFIRMATION";
            case "PAYMENT_FAILED" -> "PAYMENT_FAILED";
            case "PAYMENT_REFUNDED" -> "PAYMENT_REFUNDED";
            default -> "PAYMENT_UPDATE";
        };
    }

    /**
     * Check if notification should be sent
     */
    public boolean shouldSendNotification() {
        // Don't send notifications for pending/processing states
        return status != PaymentStatus.PENDING && status != PaymentStatus.PROCESSING;
    }
}
