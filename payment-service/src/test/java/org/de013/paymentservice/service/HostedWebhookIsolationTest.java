package org.de013.paymentservice.service;

import org.de013.paymentservice.client.*;
import org.de013.paymentservice.dto.payment.StripeWebhookRequest;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.gateway.PaymentGatewayFactory;
import org.de013.paymentservice.gateway.stripe.StripeWebhookService;
import org.de013.paymentservice.repository.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HostedWebhookIsolationTest {
    @Test void individualIntentEventsMustNotDowngradeOrPrematurelyCompleteHostedCheckout() {
        var payments = mock(PaymentRepository.class);
        var orders = mock(OrderServiceClient.class);
        var service = new WebhookServiceImpl(mock(PaymentGatewayFactory.class), mock(StripeWebhookService.class), payments,
                mock(PaymentMethodRepository.class), mock(RefundRepository.class), orders,
                mock(ProcessedStripeEventRepository.class), mock(UserServiceClient.class), mock(NotificationServiceClient.class), mock(PaymentOrderGuard.class));
        var payment = Payment.builder().gatewayName("STRIPE").gatewayTxnRef("cs_fixture").status(PaymentStatus.PENDING).build();
        when(payments.findByStripePaymentIntentId("pi_fixture")).thenReturn(Optional.of(payment));
        var request = StripeWebhookRequest.builder().data(StripeWebhookRequest.WebhookData.builder()
                .object(Map.of("object", "payment_intent", "id", "pi_fixture")).build()).build();
        assertThrows(SecurityException.class, () -> service.handlePaymentIntentFailed(request));
        assertThrows(SecurityException.class, () -> service.handlePaymentIntentCanceled(request));
        assertThrows(SecurityException.class, () -> service.handlePaymentIntentRequiresAction(request));
        assertThrows(SecurityException.class, () -> service.handlePaymentIntentSucceeded(request));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(payments, never()).save(any()); verifyNoInteractions(orders);
    }
}
