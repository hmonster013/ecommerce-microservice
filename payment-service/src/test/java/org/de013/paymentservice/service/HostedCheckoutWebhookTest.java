package org.de013.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.controller.HostedCheckoutWebhookController;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

class HostedCheckoutWebhookTest {
    @Test void rejectsTestSignatureBypass() {
        var config = new PaymentGatewayConfig(); config.getGateways().getStripe().setWebhookSecret("fixture-secret");
        var service = mock(HostedCheckoutService.class);
        var controller = new HostedCheckoutWebhookController(config, service, new ObjectMapper());
        assertThrows(ResponseStatusException.class, () -> controller.receive("{}", "test_signature"));
        verifyNoInteractions(service);
    }

    @Test void acceptsValidSignatureThenReadsAuthoritativeProviderState() throws Exception {
        var config = new PaymentGatewayConfig(); config.getGateways().getStripe().setWebhookSecret("fixture-secret");
        var service = mock(HostedCheckoutService.class);
        var controller = new HostedCheckoutWebhookController(config, service, new ObjectMapper());
        String payload = "{\"id\":\"evt_fixture\",\"object\":\"event\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_fixture\",\"object\":\"checkout.session\",\"client_reference_id\":\"1\"}}}";
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("fixture-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        controller.receive(payload, "t=" + timestamp + ",v1=" + signature);
        verify(service).refreshWebhook(1L, "cs_fixture");
    }
}
