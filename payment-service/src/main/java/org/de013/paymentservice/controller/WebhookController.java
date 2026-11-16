package org.de013.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.controller.BaseController;
import org.de013.common.dto.ApiResult;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.service.WebhookService;
import org.de013.paymentservice.exception.InvalidStripeWebhookException;
import org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.repository.ProcessedStripeEventRepository;
import org.de013.paymentservice.entity.ProcessedStripeEvent;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.client.NotificationServiceClient;
import org.de013.paymentservice.client.UserServiceClient;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.util.HtmlUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for webhook operations
 */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhook", description = "Webhook handling APIs")
public class WebhookController extends BaseController {

    private final WebhookService webhookService;
    private final PaymentGatewayConfig paymentGatewayConfig;
    private final VnpayPaymentGateway vnpayPaymentGateway;
    private final PaymentRepository paymentRepository;
    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final OrderServiceClient orderServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final UserServiceClient userServiceClient;
    private final org.de013.paymentservice.service.VnpayIpnService ipnService;

    // ========== STRIPE WEBHOOK ==========

    @PostMapping("/stripe")
    @Operation(summary = "Handle Stripe webhook", description = "Process incoming Stripe webhook events")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid webhook signature or payload"),
            @ApiResponse(responseCode = "500", description = "Webhook processing failed")
    })
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @Parameter(description = "Stripe signature header")
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        log.info("Received Stripe webhook");

        try {
            if (signature == null || signature.isBlank()) return ResponseEntity.badRequest().body("Invalid webhook signature or payload");
            // Process webhook directly (signature verification is done inside)
            webhookService.processStripeWebhook(payload, signature);

            log.info("Stripe webhook processed successfully");
            return ResponseEntity.ok("Webhook processed successfully");

        } catch (InvalidStripeWebhookException e) {
            return ResponseEntity.badRequest().body("Invalid webhook signature or payload");
        } catch (Exception e) {
            log.error("Failed to process Stripe webhook", e);
            webhookService.handleWebhookError(payload, signature, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
        }
    }

    // ========== VNPAY WEBHOOKS ==========

    @GetMapping("/vnpay/ipn")
    @Operation(summary = "Handle VNPay IPN", description = "Process incoming VNPay server-to-server IPN callbacks")
    public ResponseEntity<Map<String, String>> handleVnpayIpn(@RequestParam Map<String, String> params) {
        log.info("Received VNPay IPN with parameters: {}", params);
        Map<String, String> response = new HashMap<>();

        try {
            // 1. Verify signature
            if (!vnpayPaymentGateway.verifyCallback(params)) {
                log.warn("VNPay IPN signature verification failed");
                response.put("RspCode", "97");
                response.put("Message", "Invalid Signature");
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.ok(ipnService.process(params));

        } catch (Exception e) {
            log.error("Error processing VNPay IPN", e);
            response.put("RspCode", "99");
            response.put("Message", "Unknown Error");
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/vnpay/return")
    @Operation(summary = "Handle VNPay Return Redirect", description = "Process VNPay customer redirect callback")
    public ResponseEntity<String> handleVnpayReturn(@RequestParam Map<String, String> params) {
        log.info("Received VNPay Return");

        try {
            boolean isValidSignature = vnpayPaymentGateway.verifyCallback(params);
            String responseCode = params.get("vnp_ResponseCode");
            String txnRef = params.get("vnp_TxnRef");
            String escapedTxnRef = HtmlUtils.htmlEscape(String.valueOf(txnRef));
            String escapedResponseCode = HtmlUtils.htmlEscape(String.valueOf(responseCode));

            String htmlResponse;
            if (isValidSignature && "00".equals(responseCode)) {
                htmlResponse = "<html><body style='font-family: Arial, sans-serif; text-align: center; margin-top: 100px;'>" +
                        "<h1 style='color: green;'>Thanh to\u00e1n th\u00e0nh c\u00f4ng!</h1>" +
                        "<p>M\u00e3 giao d\u1ecbch: <strong>" + escapedTxnRef + "</strong></p>" +
                        "<p>C\u1ea3m \u01a1n b\u1ea1n \u0111\u00e3 mua s\u1eafm!</p>" +
                        "</body></html>";
            } else {
                htmlResponse = "<html><body style='font-family: Arial, sans-serif; text-align: center; margin-top: 100px;'>" +
                        "<h1 style='color: red;'>Thanh to\u00e1n th\u1ea5t b\u1ea1i!</h1>" +
                        "<p>M\u00e3 giao d\u1ecbch: <strong>" + escapedTxnRef + "</strong></p>" +
                        "<p>L\u1ed7i (M\u00e3 ph\u1ea3n h\u1ed3i): " + escapedResponseCode + "</p>" +
                        "</body></html>";
            }
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlResponse);
        } catch (Exception e) {
            log.error("Error processing VNPay Return", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing transaction");
        }
    }

    private void updateOrderStatus(Long orderId, String status, Payment payment) {
        try {
            log.info("Updating order {} status to: {}", orderId, status);
            if ("PAID".equals(status)) {
                orderServiceClient.markOrderAsPaid(orderId, payment.getId(), payment.getPaymentNumber());
            } else if ("PAYMENT_FAILED".equals(status)) {
                orderServiceClient.markOrderPaymentFailed(orderId, payment.getFailureReason() != null ? payment.getFailureReason() : "Payment failed");
            } else {
                org.de013.paymentservice.dto.external.OrderStatusUpdateRequest request = new org.de013.paymentservice.dto.external.OrderStatusUpdateRequest(
                        status,
                        "Payment status updated to " + status
                );
                orderServiceClient.updateOrderStatus(orderId, request);
            }
            log.info("Successfully updated order {} status to: {}", orderId, status);
        } catch (Exception e) {
            log.warn("Failed to update order status for order: {}", orderId, e);
        }
    }

    private void sendPaymentSuccessNotification(Payment payment) {
        try {
            String recipientEmail = payment.getReceiptEmail();
            if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                try {
                    ApiResult<org.de013.paymentservice.dto.external.UserDto> apiResponse = userServiceClient.getUserById(payment.getUserId()).getBody();
                    if (apiResponse != null && apiResponse.getData() != null) {
                        org.de013.paymentservice.dto.external.UserDto userDto = apiResponse.getData();
                        if (userDto.getEmail() != null) {
                            recipientEmail = userDto.getEmail();
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch user email from User Service for user: {}", payment.getUserId(), e);
                }
            }

            if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                log.warn("Cannot send email notification: recipient email is missing");
                return;
            }

            log.info("Sending payment success email notification to: {}", recipientEmail);
            java.util.Map<String, Object> emailRequest = new java.util.HashMap<>();
            emailRequest.put("userId", payment.getUserId());
            emailRequest.put("to", recipientEmail);
            emailRequest.put("subject", "Thanh to\u00e1n th\u00e0nh c\u00f4ng cho \u0110\u01a1n h\u00e0ng #" + payment.getOrderId());
            emailRequest.put("message", String.format(
                    "Xin ch\u00e0o,\n\nGiao d\u1ecbch thanh to\u00e1n c\u1ee7a b\u1ea1n cho \u0110\u01a1n h\u00e0ng #%d \u0111\u00e3 \u0111\u01b0\u1ee3c x\u1eed l\u00fd TH\u00c0NH C\u00d4NG.\n" +
                    "M\u00e3 giao d\u1ecbch: %s\n" +
                    "S\u1ed1 ti\u1ec1n: %s %s\n" +
                    "Ph\u01b0\u01a1ng th\u1ee9c thanh to\u00e1n: %s\n" +
                    "Th\u1eddi gian: %s\n\n" +
                    "C\u1ea3m \u01a1n b\u1ea1n \u0111\u00e3 mua s\u1eafm t\u1ea1i c\u1eeda h\u00e0ng c\u1ee7a ch\u00fang t\u00f4i!\n" +
                    "Tr\u00e2n tr\u1ecdng,\nGlobal Travel Buddy & Local Service Team",
                    payment.getOrderId(),
                    payment.getPaymentNumber(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getMethod(),
                    java.time.LocalDateTime.now().toString()
            ));

            notificationServiceClient.sendEmail(emailRequest);
            log.info("Successfully sent payment success email notification to: {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send email notification for payment: {}", payment.getPaymentNumber(), e);
        }
    }
}
