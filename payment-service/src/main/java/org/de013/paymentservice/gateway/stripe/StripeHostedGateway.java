package org.de013.paymentservice.gateway.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.entity.HostedCheckout;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
@RequiredArgsConstructor
public class StripeHostedGateway {
    private final PaymentGatewayConfig config;

    public record Snapshot(String id, String url, String status, String paymentStatus,
            Long amount, String currency, String reference, String paymentIntent) {}

    public Snapshot create(HostedCheckout checkout) throws StripeException {
        var price = SessionCreateParams.LineItem.PriceData.builder()
                .setCurrency("usd")
                .setUnitAmount(checkout.getAmount().movePointRight(2).longValueExact())
                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName("Order " + checkout.getOrderId()).build()).build();
        var params = SessionCreateParams.builder()
                // Hosted account defaults must not convert the approved USD settlement to a local currency.
                .putExtraParam("adaptive_pricing", java.util.Map.of("enabled", false))
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setClientReferenceId(checkout.getOrderId().toString())
                .setSuccessUrl(checkout.getSuccessUrl()).setCancelUrl(checkout.getCancelUrl())
                .putMetadata("orderId", checkout.getOrderId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder().setQuantity(1L).setPriceData(price).build())
                .build();
        return snapshot(Session.create(params, options("hosted-order-" + checkout.getOrderId() + "-" + checkout.getGeneration())));
    }

    public Snapshot retrieve(String sessionId) throws StripeException {
        return snapshot(Session.retrieve(sessionId, options(null)));
    }

    private RequestOptions options(String key) {
        var stripe = config.getGateways().getStripe();
        if (!stripe.isEnabled() || stripe.getApiKey() == null || stripe.getApiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe checkout is not configured");
        }
        var options = RequestOptions.builder().setApiKey(stripe.getApiKey());
        if (key != null) options.setIdempotencyKey(key);
        return options.build();
    }

    private Snapshot snapshot(Session session) {
        return new Snapshot(session.getId(), session.getUrl(), session.getStatus(), session.getPaymentStatus(),
                session.getAmountTotal(), session.getCurrency(), session.getClientReferenceId(), session.getPaymentIntent());
    }
}
