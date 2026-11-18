package org.de013.paymentservice.gateway.stripe;

import com.stripe.Stripe;
import com.stripe.model.Account;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.ChargeListParams;
import com.stripe.param.RefundListParams;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StripeCompensationVerifier {
    public record Attempt(long paymentId, String paymentNumber, String intentId, Boolean livemode,
                          boolean createdByService, BigDecimal amount, String currency, boolean hosted, String provider) { }
    public record RefundEvidence(String id, long amount, String status) { }
    public record ChargeEvidence(String id, String intentId, long amount, String status, List<RefundEvidence> refunds) { }
    public record Evidence(String accountId, boolean livemode, Instant observedAt, List<ChargeEvidence> charges) { }
    public record Result(boolean safe, String reason, Evidence evidence) { }

    public Result verify(String expectedAccount, List<Attempt> attempts) {
        if (expectedAccount == null || !expectedAccount.matches("acct_[A-Za-z0-9]+") || attempts == null || attempts.isEmpty())
            return hold("MISSING_CONFIGURATION_OR_ATTEMPTS");
        String key = Stripe.apiKey;
        if (key == null || key.isBlank()) return hold("MISSING_CREDENTIAL");
        var options = RequestOptions.builder().setApiKey(key).setConnectTimeout(500).setReadTimeout(700).setMaxNetworkRetries(0).build();
        long deadline = System.nanoTime() + 3_000_000_000L;
        try {
            checkDeadline(deadline);
            Account account = Account.retrieve(options);
            if (account == null || !expectedAccount.equals(account.getId())) return hold("ACCOUNT_MISMATCH");
            var evidence = new ArrayList<ChargeEvidence>();
            var intents = new HashSet<String>();
            var allCharges = new HashSet<String>();
            var allRefunds = new HashSet<String>();
            Boolean mode = null;
            int successes = 0;
            for (Attempt a : attempts) {
                if (a.hosted()) return hold("HOSTED_PROOF_UNSUPPORTED");
                if (!"STRIPE".equals(a.provider()) || !a.createdByService() || a.paymentId() <= 0 || a.paymentNumber() == null
                        || a.paymentNumber().isBlank() || a.intentId() == null || a.intentId().isBlank() || a.livemode() == null
                        || a.amount() == null || !"USD".equals(a.currency()) || !intents.add(a.intentId())) return hold("UNSUPPORTED_ATTEMPT");
                long amount = a.amount().movePointRight(2).longValueExact();
                if (amount <= 0 || (mode != null && !mode.equals(a.livemode()))) return hold("ATTEMPT_MISMATCH");
                mode = a.livemode();
                checkDeadline(deadline);
                PaymentIntent intent = PaymentIntent.retrieve(a.intentId(), options);
                if (intent == null || !present(intent.getRawJsonObject(), "id", "livemode", "amount", "currency", "status", "amount_received")
                        || !a.intentId().equals(intent.getId()) || !mode.equals(intent.getLivemode())
                        || !Long.valueOf(amount).equals(intent.getAmount()) || !"usd".equals(intent.getCurrency())
                        || intent.getStatus() == null) return hold("INTENT_MISMATCH");
                String cursor = null;
                int attemptSuccesses = 0;
                for (int page = 0; ; page++) {
                    if (page >= 100) return hold("PAGINATION_INCOMPLETE");
                    checkDeadline(deadline);
                    var params = ChargeListParams.builder().setPaymentIntent(a.intentId()).setLimit(100L);
                    if (cursor != null) params.setStartingAfter(cursor);
                    var list = Charge.list(params.build(), options);
                    if (list == null || list.getData() == null || list.getData().size() > 100 || list.getHasMore() == null) return hold("PAGINATION_INCOMPLETE");
                    String last = null;
                    for (Charge c : list.getData()) {
                        if (c == null || c.getId() == null || c.getId().isBlank() || !allCharges.add(c.getId()) || !a.intentId().equals(c.getPaymentIntent())
                                || !mode.equals(c.getLivemode()) || c.getAmount() == null || c.getAmountCaptured() == null
                                || c.getAmountRefunded() == null || c.getCaptured() == null || c.getPaid() == null
                                || c.getStatus() == null || !"usd".equals(c.getCurrency())) return hold("CHARGE_MISMATCH");
                        last = c.getId();
                        var refunds = new ArrayList<RefundEvidence>();
                        long refundTotal = 0;
                        String refundCursor = null;
                        for (int rp = 0; ; rp++) {
                            if (rp >= 100) return hold("PAGINATION_INCOMPLETE");
                            checkDeadline(deadline);
                            var refundParams = RefundListParams.builder().setCharge(c.getId()).setLimit(100L);
                            if (refundCursor != null) refundParams.setStartingAfter(refundCursor);
                            var pageResult = Refund.list(refundParams.build(), options);
                            if (pageResult == null || pageResult.getData() == null || pageResult.getData().size() > 100 || pageResult.getHasMore() == null) return hold("PAGINATION_INCOMPLETE");
                            String refundLast = null;
                            for (Refund r : pageResult.getData()) {
                                if (r == null || r.getId() == null || r.getId().isBlank() || !allRefunds.add(r.getId()) || !c.getId().equals(r.getCharge())
                                        || !a.intentId().equals(r.getPaymentIntent()) || r.getAmount() == null || r.getAmount() <= 0
                                        || !"usd".equals(r.getCurrency()) || !"succeeded".equals(r.getStatus())) return hold("REFUND_MISMATCH");
                                var raw = r.getRawJsonObject();
                                if (raw != null && raw.has("livemode") && (raw.get("livemode").isJsonNull() || raw.get("livemode").getAsBoolean() != mode)) return hold("REFUND_MODE_MISMATCH");
                                refundTotal = Math.addExact(refundTotal, r.getAmount());
                                refunds.add(new RefundEvidence(r.getId(), r.getAmount(), r.getStatus()));
                                refundLast = r.getId();
                            }
                            if (!pageResult.getHasMore()) break;
                            if (refundLast == null || refundLast.equals(refundCursor)) return hold("PAGINATION_INCOMPLETE");
                            refundCursor = refundLast;
                        }
                        if ("succeeded".equals(c.getStatus()) && c.getPaid() && c.getCaptured()) {
                            if (c.getAmount() != amount || c.getAmountCaptured() != amount || c.getAmountRefunded() != amount || refundTotal != amount)
                                return hold("REFUND_INCOMPLETE");
                            successes++; attemptSuccesses++;
                        } else if (!"failed".equals(c.getStatus()) || c.getPaid() || c.getCaptured() || c.getAmountCaptured() != 0 || refundTotal != 0 || c.getAmountRefunded() != 0)
                            return hold("CHARGE_NOT_TERMINAL");
                        evidence.add(new ChargeEvidence(c.getId(), a.intentId(), c.getAmount(), c.getStatus(), List.copyOf(refunds)));
                    }
                    if (!list.getHasMore()) break;
                    if (last == null || last.equals(cursor)) return hold("PAGINATION_INCOMPLETE");
                    cursor = last;
                }
                if (attemptSuccesses == 0 && (!"canceled".equals(intent.getStatus()) || !Long.valueOf(0).equals(intent.getAmountReceived()))) return hold("ATTEMPT_NOT_TERMINAL");
                if (attemptSuccesses > 0 && (!"succeeded".equals(intent.getStatus()) || !Long.valueOf(amount).equals(intent.getAmountReceived()))) return hold("INTENT_NOT_SUCCEEDED");
            }
            checkDeadline(deadline);
            return successes == 1 ? new Result(true, null, new Evidence(expectedAccount, mode, Instant.now(), List.copyOf(evidence))) : hold("SUCCESS_COUNT");
        } catch (Exception e) {
            return hold("PROVIDER_OR_DATA_UNAVAILABLE");
        }
    }

    private static void checkDeadline(long deadline) {
        if (System.nanoTime() >= deadline) throw new IllegalStateException("Verification deadline exceeded");
    }

    private static boolean present(com.google.gson.JsonObject raw, String... names) {
        if (raw == null) return false;
        for (String name : names) if (!raw.has(name) || raw.get(name).isJsonNull()) return false;
        return true;
    }

    private static Result hold(String reason) { return new Result(false, reason, null); }
}
