package org.de013.paymentservice.gateway.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class StripeClosureVerifier {
    public record Observation(UUID operationId, Long paymentId, String paymentIntentId, Boolean livemode,
                              Long amount, String currency, String status, boolean amountReceivedPresent,
                              Long amountReceived, boolean latestChargePresent, String latestCharge, String verifiedAt) { }
    public record Evidence(boolean safe, String reason, Observation observation) { }

    public Evidence verify(String intentId, Boolean livemode, Long minorAmount, String currency,
                           UUID operationId, Long attemptId) throws StripeException {
        if (intentId == null || intentId.isBlank() || livemode == null || minorAmount == null || minorAmount <= 0
                || currency == null || currency.isBlank() || operationId == null || attemptId == null || attemptId <= 0)
            return new Evidence(false, "MISSING_PROVENANCE", null);
        RequestOptions options = RequestOptions.builder().setConnectTimeout(500).setReadTimeout(700).setMaxNetworkRetries(0).build();
        PaymentIntent intent = PaymentIntent.retrieve(intentId, options);
        if (!matches(intent, intentId, livemode, minorAmount, currency)) return new Evidence(false, "IDENTITY_MISMATCH", null);
        if (!"canceled".equals(intent.getStatus())) {
            if (intent.getStatus() == null || !Set.of("requires_payment_method", "requires_confirmation", "requires_action", "requires_capture").contains(intent.getStatus()))
                return new Evidence(false, "NOT_CANCELABLE", null);
            RequestOptions cancelOptions = RequestOptions.builder().setConnectTimeout(500).setReadTimeout(700).setMaxNetworkRetries(0)
                    .setIdempotencyKey("checkout-close-" + operationId + "-" + attemptId + "-cancel").build();
            intent.cancel(cancelOptions);
            intent = PaymentIntent.retrieve(intentId, options);
        }
        if (!matches(intent, intentId, livemode, minorAmount, currency) || !"canceled".equals(intent.getStatus()))
            return new Evidence(false, "UNVERIFIED_TERMINAL", null);
        var raw = intent.getRawJsonObject();
        if (raw == null || !raw.has("amount_received") || raw.get("amount_received").isJsonNull()
                || intent.getAmountReceived() == null || intent.getAmountReceived() != 0L
                || !raw.has("latest_charge") || !raw.get("latest_charge").isJsonNull())
            return new Evidence(false, "CAPTURE_EVIDENCE_UNKNOWN", null);
        // ponytail: any charge history remains HOLD; support it only with complete paginated charge proof.
        return new Evidence(true, "CANCELED_NO_CHARGES", new Observation(operationId, attemptId, intent.getId(),
                intent.getLivemode(), intent.getAmount(), intent.getCurrency(), intent.getStatus(), true,
                intent.getAmountReceived(), true, null, Instant.now().toString()));
    }

    private boolean matches(PaymentIntent intent, String id, Boolean mode, Long amount, String currency) {
        return intent != null && id.equals(intent.getId()) && mode.equals(intent.getLivemode())
                && amount.equals(intent.getAmount()) && intent.getCurrency() != null
                && currency.equalsIgnoreCase(intent.getCurrency());
    }
}
