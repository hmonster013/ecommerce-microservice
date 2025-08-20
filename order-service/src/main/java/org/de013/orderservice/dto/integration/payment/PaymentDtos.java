package org.de013.orderservice.dto.integration.payment;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public class PaymentDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorizeRequest {
        private Long orderId;
        private String orderNumber;
        private BigDecimal amount;
        private String currency;
        private String methodType; // CREDIT_CARD, PAYPAL, COD, ...
        private String token; // optional token from gateway
        private Long savedPaymentMethodId; // optional
        private Map<String, Object> metadata;
        private Boolean capture; // if true, acts like sale (authorize+capture)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorizeResponse {
        private String paymentId;
        private String status; // AUTHORIZED, CAPTURED, FAILED
        private String authCode;
        private String gateway;
        private LocalDateTime authorizedAt;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaptureRequest {
        private String paymentId; // or by order
        private Long orderId;
        private BigDecimal amount;
        private String currency;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundRequest {
        private String paymentId;
        private Long orderId;
        private BigDecimal amount;
        private String currency;
        private String reason;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundResponse {
        private String refundId;
        private String status; // REFUNDED, PARTIAL, FAILED
        private LocalDateTime refundedAt;
        private Map<String, Object> metadata;
    }
}

