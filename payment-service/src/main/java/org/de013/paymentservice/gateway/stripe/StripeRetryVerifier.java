package org.de013.paymentservice.gateway.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.util.CurrencyUtils;
import org.springframework.stereotype.Service;

@Service
public class StripeRetryVerifier {
    private final StripePaymentGateway gateway;

    public StripeRetryVerifier(StripePaymentGateway gateway) { this.gateway = gateway; }

    public boolean eligible(Payment payment) throws StripeException {
        PaymentIntent intent = current(payment);
        return intent != null && "requires_payment_method".equals(intent.getStatus());
    }

    public PaymentIntent current(Payment payment) throws StripeException {
        if (minorAmount(payment) == null) return null;
        RequestOptions options = RequestOptions.builder().setConnectTimeout(500).setReadTimeout(700).setMaxNetworkRetries(0).build();
        PaymentIntent intent = PaymentIntent.retrieve(payment.getStripePaymentIntentId(), options);
        return matches(payment, intent) ? intent : null;
    }

    public boolean matches(Payment payment, PaymentIntent intent) {
        Long amount = minorAmount(payment);
        return amount != null && intent != null && payment.getStripePaymentIntentId().equals(intent.getId())
                && payment.getStripeIntentLivemode().equals(intent.getLivemode()) && amount.equals(intent.getAmount())
                && intent.getCurrency() != null && payment.getCurrency().name().equalsIgnoreCase(intent.getCurrency());
    }

    private Long minorAmount(Payment payment) {
        if (!"STRIPE".equals(payment.getGatewayName()) || !payment.isStripeIntentCreatedByService()
                || payment.getStripeIntentLivemode() == null || payment.getStripePaymentIntentId() == null
                || payment.getStripePaymentIntentId().isBlank() || payment.getGatewayTxnRef() != null && payment.getGatewayTxnRef().startsWith("cs_")
                || payment.getCurrency() == null || payment.getAmount() == null || payment.getAmount().signum() <= 0) return null;
        String currency = payment.getCurrency().name();
        if (!gateway.getSupportedCurrencies().contains(currency)) return null;
        long amount;
        try { amount = payment.getAmount().movePointRight(CurrencyUtils.isZeroDecimalCurrency(currency) ? 0 : 2).longValueExact(); }
        catch (ArithmeticException e) { return null; }
        return amount > 0 ? amount : null;
    }
}
