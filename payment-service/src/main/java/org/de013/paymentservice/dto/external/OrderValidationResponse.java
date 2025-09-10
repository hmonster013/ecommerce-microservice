package org.de013.paymentservice.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for order validation from Order Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderValidationResponse {
    
    private boolean valid;
    private String message;
    private List<String> errors;
    
    // Order details for validation
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime createdAt;
    
    // Validation flags
    private boolean orderExists;
    private boolean orderActive;
    private boolean orderPaid;
    private boolean orderCanceled;
    private boolean orderExpired;
    private boolean inventoryAvailable;
    private boolean userValid;
    
    // Payment validation
    private boolean canProcessPayment;
    private String paymentBlockReason;
    private BigDecimal minimumPaymentAmount;
    private BigDecimal maximumPaymentAmount;
    
    // Inventory information
    private boolean allItemsInStock;
    private List<String> outOfStockItems;
    private boolean reservationRequired;
    private LocalDateTime reservationExpiry;
    
    // Helper methods
    @JsonIgnore
    public boolean isPaymentAllowed() {
        return valid && canProcessPayment && !orderPaid && !orderCanceled;
    }

    @JsonIgnore
    public boolean requiresInventoryReservation() {
        return reservationRequired && allItemsInStock;
    }
    
    @JsonIgnore
    public boolean hasPaymentRestrictions() {
        return paymentBlockReason != null && !paymentBlockReason.trim().isEmpty();
    }
    
    // Factory methods
    public static OrderValidationResponse valid(Long orderId, String orderNumber, BigDecimal totalAmount) {
        return OrderValidationResponse.builder()
                .valid(true)
                .orderId(orderId)
                .orderNumber(orderNumber)
                .totalAmount(totalAmount)
                .orderExists(true)
                .orderActive(true)
                .orderPaid(false)
                .orderCanceled(false)
                .orderExpired(false)
                .canProcessPayment(true)
                .allItemsInStock(true)
                .userValid(true)
                .message("Order is valid for payment processing")
                .build();
    }
    
    public static OrderValidationResponse invalid(String message, List<String> errors) {
        return OrderValidationResponse.builder()
                .valid(false)
                .message(message)
                .errors(errors)
                .canProcessPayment(false)
                .build();
    }
    
    public static OrderValidationResponse orderNotFound(Long orderId) {
        return OrderValidationResponse.builder()
                .valid(false)
                .orderId(orderId)
                .orderExists(false)
                .canProcessPayment(false)
                .message("Order not found")
                .build();
    }
    
    public static OrderValidationResponse alreadyPaid(Long orderId, String orderNumber) {
        return OrderValidationResponse.builder()
                .valid(false)
                .orderId(orderId)
                .orderNumber(orderNumber)
                .orderExists(true)
                .orderPaid(true)
                .canProcessPayment(false)
                .message("Order has already been paid")
                .build();
    }
}
