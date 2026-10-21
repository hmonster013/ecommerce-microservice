package org.de013.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.service.HostedCheckoutService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/webhooks/stripe/hosted")
@RequiredArgsConstructor
public class HostedCheckoutWebhookController {
    private final PaymentGatewayConfig config;
    private final HostedCheckoutService checkout;
    private final ObjectMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
        String secret = config.getGateways().getStripe().getWebhookSecret();
        if (secret == null || secret.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        try {
            var event = Webhook.constructEvent(payload, signature, secret);
            if (!event.getType().startsWith("checkout.session.")) return;
            var object = mapper.readTree(payload).path("data").path("object");
            String reference = object.path("client_reference_id").asText();
            String sessionId = object.path("id").asText();
            if (!reference.matches("[1-9][0-9]*") || !sessionId.startsWith("cs_")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            // Signed metadata is a lookup hint only; provider read-back determines the outcome.
            checkout.refreshWebhook(Long.valueOf(reference), sessionId);
        } catch (SignatureVerificationException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe signature");
        } catch (com.fasterxml.jackson.core.JsonProcessingException | NumberFormatException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid checkout event");
        }
    }
}
