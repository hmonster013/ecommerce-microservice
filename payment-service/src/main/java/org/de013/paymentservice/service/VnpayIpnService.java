package org.de013.paymentservice.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.de013.paymentservice.client.NotificationServiceClient;
import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.entity.ProcessedStripeEvent;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.repository.ProcessedStripeEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class VnpayIpnService {
    private final PaymentOutbox outbox;
    private final PaymentNotificationOutbox notificationOutbox;
    private final PaymentRepository payments;
    private final ProcessedStripeEventRepository events;
    private final PaymentOrderGuard guard;
    private final PaymentGatewayConfig config;
    private final OrderServiceClient orders;
    private final NotificationServiceClient notifications;
    private final UserServiceClient users;
    private final org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway gateway;

    @Transactional
    public Map<String, String> process(Map<String, String> params) {
        if (!gateway.verifyCallback(params)) return Map.of("RspCode", "97", "Message", "Invalid Signature");
        String merchant = config.getGateways().getVnpay().getTmnCode();
        if (merchant == null || merchant.isBlank() || !merchant.equals(params.get("vnp_TmnCode")))
            return Map.of("RspCode", "97", "Message", "Invalid Signature");
        String ref = params.get("vnp_TxnRef");
        Long orderId = payments.findOrderIdByPaymentNumber(ref).orElse(null);
        if (orderId == null) return Map.of("RspCode", "01", "Message", "Order not found");
        boolean held = guard.lock(orderId);
        var payment = payments.findByPaymentNumber(ref).filter(p -> orderId.equals(p.getOrderId())).orElseThrow();
        Long snapshot = payment.getProviderAmountVnd();
        if (snapshot == null || !"VND".equals(payment.getProviderCurrency()) || snapshot <= 0 || snapshot > Long.MAX_VALUE / 100) {
            guard.hold(orderId);
            return Map.of("RspCode", "99", "Message", "Unknown error");
        }
        String amount = params.get("vnp_Amount");
        long received;
        try {
            if (amount == null || !amount.matches("[0-9]+")) throw new NumberFormatException();
            received = Long.parseLong(amount);
        } catch (NumberFormatException e) {
            return Map.of("RspCode", "04", "Message", "Invalid Amount");
        }
        if (snapshot * 100 != received) return Map.of("RspCode", "04", "Message", "Invalid Amount");
        String eventId = "vnpay_" + ref;
        if (events.existsById(eventId) || payment.getStatus() == PaymentStatus.SUCCEEDED)
            return Map.of("RspCode", "02", "Message", "Order already confirmed");
        if ("00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"))) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            guard.recordSuccess(orderId);
            payment.setGatewayResponse(params.toString());
            events.save(ProcessedStripeEvent.builder().eventId(eventId).processedAt(LocalDateTime.now()).build());
            payments.flush();
            events.flush();
            if (!held) {
                if (outbox.enabled()) outbox.enqueue(payment);
                else try {
                    var response = orders.markOrderAsPaid(orderId, payment.getId(), payment.getPaymentNumber());
                    if (!response.getStatusCode().is2xxSuccessful()) log.warn("Failed to update VNPay order {} status to PAID: HTTP {}", orderId, response.getStatusCode().value());
                } catch (Exception e) { log.warn("Failed to update VNPay order {} status to PAID", orderId, e); }
                if (notificationOutbox.enabled()) notificationOutbox.enqueue(payment);
                else try {
                    String email = payment.getReceiptEmail();
                    if (email == null || email.isBlank()) {
                        try {
                            var body = users.getUserById(payment.getUserId()).getBody();
                            if (body != null && body.getData() != null) email = body.getData().getEmail();
                        } catch (Exception e) { log.warn("Failed to fetch VNPay notification recipient", e); }
                    }
                    if (email != null && !email.isBlank()) {
                        Map<String, Object> request = new HashMap<>();
                        request.put("userId", payment.getUserId()); request.put("to", email);
                        request.put("subject", "Thanh toán thành công cho Đơn hàng #" + orderId);
                        request.put("message", "Thanh toán thành công cho đơn hàng #" + orderId);
                        notifications.sendEmail(request);
                    }
                } catch (Exception e) { log.warn("Failed to send VNPay notification for payment {}", payment.getId(), e); }
            }
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("VNPay failed with response code: " + params.get("vnp_ResponseCode"));
            payments.flush();
            if (!held) try {
                var response = orders.markOrderPaymentFailed(orderId, payment.getFailureReason());
                if (!response.getStatusCode().is2xxSuccessful()) log.warn("Failed to update VNPay order {} status to PAYMENT_FAILED: HTTP {}", orderId, response.getStatusCode().value());
            } catch (Exception e) { log.warn("Failed to update VNPay order {} status to PAYMENT_FAILED", orderId, e); }
        }
        return Map.of("RspCode", "00", "Message", "Confirm Success");
    }
}
