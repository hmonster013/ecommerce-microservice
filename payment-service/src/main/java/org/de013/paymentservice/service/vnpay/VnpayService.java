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
import org.de013.paymentservice.service.validation.PaymentRequestValidator;
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
    private final PaymentRequestValidator paymentRequestValidator;
    private final HttpServletRequest httpServletRequest;

    /**
     * Creates a VNPay checkout request, saves a PENDING payment, and builds the redirect URL.
     */
    public PaymentResponse createCheckout(ProcessPaymentRequest req) {
        log.info("Creating VNPay checkout for order: {}, amount: {}", req.getOrderId(), req.getAmount());

        // Validate base payment request (order status, user status, amounts mismatch)
        paymentRequestValidator.validatePaymentRequest(req);

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
