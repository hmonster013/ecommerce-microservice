package org.de013.paymentservice.service;

import org.de013.paymentservice.client.HostedOrderClient;
import org.de013.paymentservice.client.OrderServiceClientFallback;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.entity.HostedCheckout;
import org.de013.paymentservice.gateway.stripe.StripeHostedGateway;
import org.de013.paymentservice.repository.HostedCheckoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HostedCheckoutServiceTest {
    HostedOrderClient orders = mock(HostedOrderClient.class);
    HostedCheckoutRepository repository = mock(HostedCheckoutRepository.class);
    HostedCheckoutStore store = mock(HostedCheckoutStore.class);
    StripeHostedGateway stripe = mock(StripeHostedGateway.class);
    PaymentGatewayConfig config = new PaymentGatewayConfig();
    org.de013.paymentservice.service.validation.PaymentRequestValidator eligibility = mock(org.de013.paymentservice.service.validation.PaymentRequestValidator.class);
    HostedCheckoutService service = new HostedCheckoutService(orders, repository, store, stripe, config, eligibility);
    HostedCheckout checkout;
    OrderDto order;

    @BeforeEach void setup() {
        order = new OrderDto(); order.setId(1L); order.setUserId("owner"); order.setStatus("PENDING"); order.setOrderNumber("ORD-1");
        var money = new OrderDto.MoneyDto(); money.setAmount(new BigDecimal("25.00")); money.setCurrency("USD"); order.setTotalAmount(money);
        when(orders.read(eq(1L), anyString(), eq("hosted-checkout"))).thenReturn(ResponseEntity.ok(order));
        checkout = new HostedCheckout(); checkout.setOrderId(1L); checkout.setUserId("owner"); checkout.setAmount(new BigDecimal("25.00"));
        checkout.setPaymentId(9L); checkout.setSessionId("cs_fixture"); checkout.setReservedAt(Instant.now());
        when(repository.findById(1L)).thenReturn(Optional.of(checkout));
        config.getGateways().getStripe().setCheckoutReturnUrl("http://localhost:4200/checkout/result");
    }

    StripeHostedGateway.Snapshot snapshot(String state, String paid, String url) {
        return new StripeHostedGateway.Snapshot("cs_fixture", url, state, paid, 2500L, "usd", "1", "pi_fixture");
    }

    @Test void redirectsOnlyOpenSessionAndDoesNotMarkPaid() throws Exception {
        when(stripe.retrieve("cs_fixture")).thenReturn(snapshot("open", "unpaid", "https://checkout.stripe.com/c/pay/cs_fixture"));
        assertEquals("OPEN", service.start(1L, "owner").state());
        verify(stripe, never()).create(any()); verify(orders, never()).markPaid(any(), any(), any(), any(), any());
    }

    @Test void verifiesPaidAndMarksOrderBeforeReturningPaid() throws Exception {
        when(stripe.retrieve("cs_fixture")).thenReturn(snapshot("complete", "paid", null));
        when(orders.markPaid(1L, 9L, "HOSTED-1", "owner", "hosted-checkout")).thenReturn(ResponseEntity.ok().build());
        assertEquals("PAID", service.read(1L, "owner").state());
        verify(store).record(1L, 0, "cs_fixture", "PAID", "pi_fixture");
    }

    @Test void orderSyncFailureDoesNotReturnSuccess() throws Exception {
        when(stripe.retrieve("cs_fixture")).thenReturn(snapshot("complete", "paid", null));
        when(orders.markPaid(any(), any(), any(), any(), any())).thenReturn(ResponseEntity.status(503).build());
        assertThrows(ResponseStatusException.class, () -> service.read(1L, "owner"));
    }

    @Test void fallbackCannotReportSuccessfulOrderUpdate() {
        assertEquals(503, new OrderServiceClientFallback().markOrderAsPaid(1L, 9L, "HOSTED-1").getStatusCode().value());
    }

    @Test void rejectsOtherOwnerBeforeProviderCall() {
        assertThrows(AccessDeniedException.class, () -> service.start(1L, "other"));
        verifyNoInteractions(stripe, store);
    }

    @Test void validatesCurrencyFromOrder() {
        order.getTotalAmount().setCurrency("VND");
        assertThrows(ResponseStatusException.class, () -> service.start(1L, "owner")); verifyNoInteractions(stripe);
    }

    @Test void refusesMissingCanonicalCurrencyAndFractionalCents() {
        order.getTotalAmount().setCurrency(null);
        assertThrows(ResponseStatusException.class, () -> service.start(1L, "owner"));
        order.getTotalAmount().setCurrency("USD"); order.getTotalAmount().setAmount(new BigDecimal("25.001"));
        assertThrows(ResponseStatusException.class, () -> service.start(1L, "owner"));
    }

    @Test void validatesAmountAndReferenceFromStripe() throws Exception {
        when(stripe.retrieve("cs_fixture")).thenReturn(new StripeHostedGateway.Snapshot("cs_fixture", null, "complete", "paid", 1L, "usd", "1", "pi_fixture"));
        assertThrows(ResponseStatusException.class, () -> service.read(1L, "owner")); verifyNoInteractions(store);
        when(stripe.retrieve("cs_fixture")).thenReturn(new StripeHostedGateway.Snapshot("cs_fixture", null, "complete", "paid", 2500L, "usd", "2", "pi_fixture"));
        assertThrows(ResponseStatusException.class, () -> service.read(1L, "owner")); verifyNoInteractions(store);
    }

    @Test void unknownReadNeverCreatesSession() {
        checkout.setSessionId(null);
        assertEquals("UNKNOWN", service.read(1L, "owner").state()); verifyNoInteractions(stripe);
    }

    @Test void unknownPostUsesSameDurableReservation() throws Exception {
        checkout.setSessionId(null);
        when(stripe.create(checkout)).thenReturn(snapshot("open", "unpaid", "https://checkout.stripe.com/c/pay/cs_fixture"));
        assertEquals("OPEN", service.start(1L, "owner").state());
        verify(store, never()).reserve(any(), any(), any(), any()); verify(stripe).create(checkout);
    }

    @Test void unknownOutsideStripeIdempotencyWindowDoesNotReplayCreate() {
        checkout.setSessionId(null); checkout.setReservedAt(Instant.now().minusSeconds(24 * 3600));
        assertThrows(ResponseStatusException.class, () -> service.start(1L, "owner")); verifyNoInteractions(stripe);
    }

    @Test void renewsOnlyAfterProviderConfirmsExpired() throws Exception {
        when(stripe.retrieve("cs_fixture")).thenReturn(snapshot("expired", "unpaid", null));
        var next = new HostedCheckout(); next.setOrderId(1L); next.setAmount(checkout.getAmount()); next.setGeneration(1); next.setReservedAt(Instant.now());
        when(store.renew(1L, 0)).thenReturn(next);
        when(stripe.create(next)).thenReturn(snapshot("open", "unpaid", "https://checkout.stripe.com/c/pay/cs_fixture"));
        assertEquals("OPEN", service.start(1L, "owner").state()); verify(store).renew(1L, 0);
    }

    @Test void rejectsUntrustedRedirect() throws Exception {
        when(stripe.retrieve("cs_fixture")).thenReturn(snapshot("open", "unpaid", "https://attacker.test/checkout"));
        assertThrows(ResponseStatusException.class, () -> service.start(1L, "owner"));
    }

    @Test void noRecordReadDoesNotCreatePayment() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertEquals("NOT_STARTED", service.read(1L, "owner").state()); verifyNoInteractions(store, stripe);
    }

    @Test void createsUsingCanonicalServerAmountAndConfiguredReturnUrl() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.empty()); checkout.setSessionId(null);
        when(store.reserve(1L, "owner", new BigDecimal("25.00"), "http://localhost:4200/checkout/result")).thenReturn(checkout);
        when(stripe.create(checkout)).thenReturn(snapshot("open", "unpaid", "https://checkout.stripe.com/c/pay/cs_fixture"));
        assertEquals("OPEN", service.start(1L, "owner").state());
        verify(store).reserve(1L, "owner", new BigDecimal("25.00"), "http://localhost:4200/checkout/result");
    }

    @Test void invalidReturnConfigFailsBeforeReservation() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        config.getGateways().getStripe().setCheckoutReturnUrl("javascript:alert(1)");
        assertThrows(ResponseStatusException.class, () -> service.start(1L, "owner")); verifyNoInteractions(store, stripe);
    }

    @Test void canceledOrderCannotBePaidAgain() {
        order.setStatus("CANCELLED");
        assertThrows(ResponseStatusException.class, () -> service.start(1L, "owner")); verifyNoInteractions(stripe);
    }

    @Test void webhookUsesPersistedOwnerAndVerifiesStripe() throws Exception {
        when(orders.read(1L, "owner", "hosted-checkout")).thenReturn(ResponseEntity.ok(order));
        when(stripe.retrieve("cs_fixture")).thenReturn(snapshot("open", "unpaid", "https://checkout.stripe.com/c/pay/cs_fixture"));
        service.refreshWebhook(1L, "cs_fixture"); verify(stripe).retrieve("cs_fixture");
        assertThrows(ResponseStatusException.class, () -> service.refreshWebhook(1L, "cs_other"));
    }
}
