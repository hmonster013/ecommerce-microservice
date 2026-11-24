package org.de013.paymentservice.controller;

import org.de013.paymentservice.dto.stripe.StripePaymentResponse;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.enums.Currency;
import org.de013.paymentservice.gateway.stripe.StripeCustomerService;
import org.de013.paymentservice.gateway.stripe.StripePaymentGateway;
import org.de013.paymentservice.service.PaymentMethodService;
import org.de013.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StripeControllerTest {
    private final PaymentService payments = mock(PaymentService.class);
    private final StripePaymentGateway gateway = mock(StripePaymentGateway.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(gateway.isEnabled()).thenReturn(true);
        when(gateway.getProviderName()).thenReturn("STRIPE");
        mvc = MockMvcBuilders.standaloneSetup(new StripeController(payments, mock(PaymentMethodService.class),
                gateway, mock(StripeCustomerService.class))).build();
    }

    @Test void rejectsAlreadyClaimedIntent() throws Exception {
        when(payments.getPaymentEntityById(1L)).thenReturn(Optional.of(Payment.builder().userId("owner").build()));
        mvc.perform(post("/stripe/payment-intents").param("paymentId", "1")
                .header("X-User-Id", "owner").header("X-User-Username", "user"))
                .andExpect(status().isConflict());
        verify(gateway, never()).createPaymentIntent(any());
    }

    @Test void ownerCanRetrieveExistingIntent() throws Exception {
        when(payments.getPaymentEntityById(1L)).thenReturn(Optional.of(Payment.builder().userId("owner").stripePaymentIntentId("pi_existing").build()));
        when(gateway.getPaymentIntent("pi_existing")).thenReturn(StripePaymentResponse.builder().paymentIntentId("pi_existing").build());
        mvc.perform(post("/stripe/payment-intents").param("paymentId", "1")
                .header("X-User-Id", "owner").header("X-User-Username", "user")).andExpect(status().isOk());
        verify(gateway).getPaymentIntent("pi_existing");
        verify(gateway, never()).createPaymentIntent(any());
        verify(payments, never()).savePaymentEntity(any());
    }

    @Test
    void ownerCannotCreateAnotherIntent() throws Exception {
        Payment payment = Payment.builder().userId("owner").orderId(2L).amount(new BigDecimal("10.00"))
                .currency(Currency.USD).build();
        when(payments.getPaymentEntityById(1L)).thenReturn(Optional.of(payment));
        mvc.perform(post("/stripe/payment-intents").param("paymentId", "1")
                .header("X-User-Id", "owner").header("X-User-Username", "user"))
                .andExpect(status().isConflict());
        verify(gateway, never()).createPaymentIntent(any());
        verify(payments, never()).savePaymentEntity(any());
    }

    @Test
    void rejectsNonOwnerAndMissingOwner() throws Exception {
        for (String owner : new String[]{"other", "", "  ", null}) {
            when(payments.getPaymentEntityById(1L)).thenReturn(Optional.of(Payment.builder().userId(owner).build()));
            mvc.perform(post("/stripe/payment-intents").param("paymentId", "1")
                    .header("X-User-Id", "owner").header("X-User-Username", "user"))
                    .andExpect(status().isForbidden());
        }
        verify(gateway, never()).createPaymentIntent(any());
        verify(payments, never()).savePaymentEntity(any());
    }

    @Test
    void missingIdentityFailsClosed() throws Exception {
        when(payments.getPaymentEntityById(1L)).thenReturn(Optional.of(Payment.builder().userId("owner").build()));
        mvc.perform(post("/stripe/payment-intents").param("paymentId", "1"))
                .andExpect(status().isUnauthorized());
        verify(gateway, never()).createPaymentIntent(any());
        verify(payments, never()).savePaymentEntity(any());
    }

    @Test
    void invalidAndMissingPayment() throws Exception {
        mvc.perform(post("/stripe/payment-intents").param("paymentId", "0"))
                .andExpect(status().isBadRequest());
        when(payments.getPaymentEntityById(1L)).thenReturn(Optional.empty());
        mvc.perform(post("/stripe/payment-intents").param("paymentId", "1"))
                .andExpect(status().isNotFound());
        verify(gateway, never()).createPaymentIntent(any());
        verify(payments, never()).savePaymentEntity(any());
    }
}
