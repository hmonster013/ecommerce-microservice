package org.de013.paymentservice.controller;

import org.de013.paymentservice.client.NotificationServiceClient;
import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.gateway.vnpay.VnpayPaymentGateway;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.repository.ProcessedStripeEventRepository;
import org.de013.paymentservice.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

class WebhookControllerTest {
    private final WebhookService service = mock(WebhookService.class);
    private final VnpayPaymentGateway gateway = mock(VnpayPaymentGateway.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new WebhookController(service, mock(PaymentGatewayConfig.class),
                gateway, mock(PaymentRepository.class), mock(ProcessedStripeEventRepository.class),
                mock(OrderServiceClient.class), mock(NotificationServiceClient.class), mock(UserServiceClient.class),
                mock(org.de013.paymentservice.service.VnpayIpnService.class))).build();
    }

    @Test
    void debugRoutesAreNotMapped() throws Exception {
        for (String route : new String[]{"verify", "parse", "error", "test"}) {
            mvc.perform(post("/webhooks/stripe/" + route).content("{}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/webhooks/stripe/event-types/payment_intent.succeeded/valid"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test
    void invalidReturnEscapesUntrustedValues() throws Exception {
        when(gateway.verifyCallback(anyMap())).thenReturn(false);
        mvc.perform(get("/webhooks/vnpay/return")
                        .param("vnp_TxnRef", "<script>alert('x')</script>")
                        .param("vnp_ResponseCode", "<img src=x onerror=alert(1)>"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;")))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(not(containsString("<img"))));
    }

    @Test
    void stripeWebhookStillDelegates() throws Exception {
        mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", "signature").content("payload"))
                .andExpect(status().isOk());
        verify(service).processStripeWebhook("payload", "signature");
    }
}
