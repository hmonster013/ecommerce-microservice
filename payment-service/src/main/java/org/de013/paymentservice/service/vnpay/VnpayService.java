package org.de013.paymentservice.service.vnpay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.dto.payment.PaymentResponse;
import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.Currency;
import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway;
import org.de013.paymentservice.mapper.PaymentMapper;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.util.PaymentNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VnpayService {

    private final PaymentRepository paymentRepository;
    private final VnpayPaymentGateway vnpayPaymentGateway;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayConfig config;
    private final OrderServiceClient orderServiceClient;
    private final UserServiceClient userServiceClient;
    private final HttpServletRequest httpServletRequest;

    /**
     * Creates a VNPay checkout request, saves a PENDING payment, and builds the redirect URL.
     */
    public PaymentResponse createCheckout(ProcessPaymentRequest req) {
        log.info("Creating VNPay checkout for order: {}, amount: {}", req.getOrderId(), req.getAmount());

        // Validate base payment request (order status, user status, amounts mismatch)
        validatePaymentRequest(req);

        // Generate payment number
        String paymentNumber = generatePaymentNumber();

        // Convert USD to VND using config rate
        long vndRate = config.getGateways().getVnpay().getVndRate();
        long amountVnd = Math.round(req.getAmount().doubleValue() * vndRate);

        // Build createDate in GMT+7 format yyyyMMddHHmmss
        LocalDateTime nowGmt7 = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String createDate = nowGmt7.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        // Get Client IP
        String ipAddr = getClientIp(httpServletRequest);

        // Create Order Info
        String orderInfo = "Thanh toan don hang #" + req.getOrderId();

        // Build Payment URL
        String redirectUrl = vnpayPaymentGateway.buildPaymentUrl(
                paymentNumber,
                amountVnd,
                orderInfo,
                ipAddr,
                createDate
        );

        // Build & Save Payment entity
        Payment payment = Payment.builder()
                .paymentNumber(paymentNumber)
                .orderId(req.getOrderId())
                .userId(req.getUserId())
                .amount(req.getAmount()) // Store original USD amount
                .currency(Currency.USD)  // Keep original USD currency to match Order Service
                .status(PaymentStatus.PENDING)
                .method(PaymentMethodType.WALLET)
                .gatewayName("VNPAY")
                .gatewayTxnRef(paymentNumber)
                .description(req.getDescription() != null ? req.getDescription() : orderInfo)
                .receiptEmail(req.getReceiptEmail())
                .createdBy("SYSTEM")
                .updatedBy("SYSTEM")
                .build();

        payment = paymentRepository.save(payment);

        // Map and set redirect URL
        PaymentResponse resp = paymentMapper.toPaymentResponse(payment);
        resp.setRedirectUrl(redirectUrl);

        log.info("Successfully created VNPay checkout for payment {}. Redirect URL built.", paymentNumber);
        return resp;
    }

    private void validatePaymentRequest(ProcessPaymentRequest request) {
        if (request == null) {
            throw new org.de013.paymentservice.exception.PaymentProcessingException("Payment request cannot be null");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new org.de013.paymentservice.exception.PaymentProcessingException("Payment amount must be greater than zero");
        }
        if (request.getOrderId() == null) {
            throw new org.de013.paymentservice.exception.PaymentProcessingException("Order ID is required");
        }
        if (request.getUserId() == null) {
            throw new org.de013.paymentservice.exception.PaymentProcessingException("User ID is required");
        }

        // Validate payment amount and status with Order Service
        validatePaymentAmount(request.getOrderId(), request.getAmount());

        // Validate user can make payment with User Service (Fail-Closed)
        validateUserCanMakePayment(request.getUserId());
    }

    private void validatePaymentAmount(Long orderId, java.math.BigDecimal amount) {
        log.info("Validating payment amount {} for order {}", amount, orderId);
        try {
            org.springframework.http.ResponseEntity<org.de013.paymentservice.dto.external.OrderDto> response = orderServiceClient.getOrderById(orderId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                org.de013.paymentservice.dto.external.OrderDto order = response.getBody();
                
                // Business rule validation: Do not allow payment for an order in a non-payable state
                if ("PAID".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus())) {
                    log.error("Order {} is already paid or completed. Status: {}", orderId, order.getStatus());
                    throw new org.de013.common.exception.ConflictException("Order is already paid: " + orderId);
                }
                if ("CANCELLED".equals(order.getStatus())) {
                    log.error("Order {} is cancelled, cannot process payment", orderId);
                    throw new org.de013.common.exception.ConflictException("Order is cancelled: " + orderId);
                }

                java.math.BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount().getAmount() : java.math.BigDecimal.ZERO;
                if (orderTotal.compareTo(amount) != 0) {
                    log.error("Payment amount mismatch: request amount={}, order total={}", amount, orderTotal);
                    throw new org.de013.paymentservice.exception.PaymentProcessingException("Payment amount mismatch. Required: " + orderTotal + ", Provided: " + amount);
                }
                log.info("Payment amount matches order total: {}", orderTotal);
            } else {
                throw new org.de013.paymentservice.exception.PaymentProcessingException("Failed to validate payment: Order not found: " + orderId);
            }
        } catch (org.de013.common.exception.ConflictException | org.de013.paymentservice.exception.PaymentProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error communicating with Order Service for verification: ", e);
            throw new org.de013.paymentservice.exception.PaymentProcessingException("Failed to verify payment amount: " + e.getMessage(), e);
        }
    }

    private void validateUserCanMakePayment(String userId) {
        log.info("Validating with User Service if user can make payment: {}", userId);
        try {
            org.springframework.http.ResponseEntity<org.de013.paymentservice.dto.external.UserValidationResponse> response = userServiceClient.validateUserForPayment(userId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                org.de013.paymentservice.dto.external.UserValidationResponse validation = response.getBody();
                if (!validation.isValid()) {
                    log.error("User validation failed: {}", validation.getMessage());
                    throw new org.de013.paymentservice.exception.PaymentProcessingException("User is not allowed to make payment: " + validation.getMessage());
                }
                if (!validation.isCanMakePayments()) {
                    log.error("User cannot make payments. Reason: {}", validation.getPaymentBlockReason());
                    throw new org.de013.paymentservice.exception.PaymentProcessingException("User payment authorization is disabled: " + validation.getPaymentBlockReason());
                }
                log.info("User validation successful for user: {}", userId);
            } else {
                throw new org.de013.paymentservice.exception.PaymentProcessingException("Failed to validate user: User not found: " + userId);
            }
        } catch (org.de013.paymentservice.exception.PaymentProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error communicating with User Service for verification: ", e);
            throw new org.de013.paymentservice.exception.PaymentProcessingException("User validation failed due to communication error: " + e.getMessage(), e);
        }
    }

    private String generatePaymentNumber() {
        String paymentNumber;
        do {
            paymentNumber = PaymentNumberGenerator.generatePaymentNumber();
        } while (paymentRepository.existsByPaymentNumber(paymentNumber));
        return paymentNumber;
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        return ipAddress == null || ipAddress.isEmpty() ? "127.0.0.1" : ipAddress;
    }
}
