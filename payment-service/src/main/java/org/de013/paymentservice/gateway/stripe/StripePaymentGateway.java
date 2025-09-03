package org.de013.paymentservice.gateway.stripe;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import com.stripe.param.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.config.PaymentGatewayConfig;
import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.paymentservice.dto.paymentmethod.CreatePaymentMethodRequest;
import org.de013.paymentservice.dto.refund.RefundRequest;
import org.de013.paymentservice.dto.stripe.*;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.PaymentMethod;
import org.de013.paymentservice.entity.Refund;
import org.de013.paymentservice.exception.PaymentGatewayException;
import org.de013.paymentservice.gateway.PaymentGateway;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;

/**
 * Stripe Payment Gateway Implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private final PaymentGatewayConfig config;
    private final StripeCustomerService stripeCustomerService;
    private final StripePaymentMethodService stripePaymentMethodService;
    private final StripeWebhookService stripeWebhookService;

    @PostConstruct
    public void initialize() {
        if (config.getGateways().getStripe().isEnabled()) {
            Stripe.apiKey = config.getGateways().getStripe().getApiKey();
            log.info("Stripe payment gateway initialized with API version: {}", 
                    config.getGateways().getStripe().getApiVersion());
        }
    }

    @Override
    public String getProviderName() {
        return "STRIPE";
    }

    @Override
    public boolean isEnabled() {
        return config.getGateways().getStripe().isEnabled();
    }

    @Override
    public boolean isHealthy() {
        try {
            // Test Stripe connection by retrieving account info
            Account.retrieve();
            return true;
        } catch (Exception e) {
            log.warn("Stripe health check failed", e);
            return false;
        }
    }

    // ========== CUSTOMER MANAGEMENT ==========

    @Override
    public StripeCustomerResponse createCustomer(StripeCustomerRequest request) throws Exception {
        try {
            CustomerCreateParams.Builder paramsBuilder = CustomerCreateParams.builder();
            
            if (request.getEmail() != null) {
                paramsBuilder.setEmail(request.getEmail());
            }
            if (request.getName() != null) {
                paramsBuilder.setName(request.getName());
            }
            if (request.getPhone() != null) {
                paramsBuilder.setPhone(request.getPhone());
            }
            if (request.getDescription() != null) {
                paramsBuilder.setDescription(request.getDescription());
            }
            
            // Add address if provided
            if (request.hasAddress()) {
                CustomerCreateParams.Address address = CustomerCreateParams.Address.builder()
                        .setLine1(request.getAddress().getLine1())
                        .setLine2(request.getAddress().getLine2())
                        .setCity(request.getAddress().getCity())
                        .setState(request.getAddress().getState())
                        .setPostalCode(request.getAddress().getPostalCode())
                        .setCountry(request.getAddress().getCountry())
                        .build();
                paramsBuilder.setAddress(address);
            }
            
            // Add metadata if provided
            if (request.hasMetadata()) {
                paramsBuilder.putAllMetadata(request.getMetadata());
            }

            Customer customer = Customer.create(paramsBuilder.build());
            return stripeCustomerService.mapToCustomerResponse(customer);
            
        } catch (StripeException e) {
            log.error("Failed to create Stripe customer", e);
            throw new PaymentGatewayException("Failed to create customer: " + e.getMessage(), e);
        }
    }

    @Override
    public StripeCustomerResponse getCustomer(String customerId) throws Exception {
        try {
            Customer customer = Customer.retrieve(customerId);
            return stripeCustomerService.mapToCustomerResponse(customer);
        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe customer: {}", customerId, e);
            throw new PaymentGatewayException("Failed to retrieve customer: " + e.getMessage(), e);
        }
    }

    @Override
    public StripeCustomerResponse updateCustomer(String customerId, StripeCustomerRequest request) throws Exception {
        try {
            CustomerUpdateParams.Builder paramsBuilder = CustomerUpdateParams.builder();
            
            if (request.getEmail() != null) {
                paramsBuilder.setEmail(request.getEmail());
            }
            if (request.getName() != null) {
                paramsBuilder.setName(request.getName());
            }
            if (request.getPhone() != null) {
                paramsBuilder.setPhone(request.getPhone());
            }
            
            Customer customer = Customer.retrieve(customerId);
            customer = customer.update(paramsBuilder.build());
            
            return stripeCustomerService.mapToCustomerResponse(customer);
        } catch (StripeException e) {
            log.error("Failed to update Stripe customer: {}", customerId, e);
            throw new PaymentGatewayException("Failed to update customer: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteCustomer(String customerId) throws Exception {
        try {
            Customer customer = Customer.retrieve(customerId);
            customer.delete();
            return true;
        } catch (StripeException e) {
            log.error("Failed to delete Stripe customer: {}", customerId, e);
            throw new PaymentGatewayException("Failed to delete customer: " + e.getMessage(), e);
        }
    }

    // ========== PAYMENT METHOD MANAGEMENT ==========

    @Override
    public StripePaymentMethodResponse createPaymentMethod(StripePaymentMethodRequest request) throws Exception {
        return stripePaymentMethodService.createPaymentMethod(request);
    }

    @Override
    public StripePaymentMethodResponse getPaymentMethod(String paymentMethodId) throws Exception {
        return stripePaymentMethodService.getPaymentMethod(paymentMethodId);
    }

    @Override
    public StripePaymentMethodResponse attachPaymentMethod(String paymentMethodId, String customerId) throws Exception {
        return stripePaymentMethodService.attachPaymentMethod(paymentMethodId, customerId);
    }

    @Override
    public StripePaymentMethodResponse detachPaymentMethod(String paymentMethodId) throws Exception {
        return stripePaymentMethodService.detachPaymentMethod(paymentMethodId);
    }

    @Override
    public List<StripePaymentMethodResponse> listPaymentMethods(String customerId, String type) throws Exception {
        return stripePaymentMethodService.listPaymentMethods(customerId, type);
    }

    @Override
    public StripePaymentMethodResponse updatePaymentMethod(String paymentMethodId, StripePaymentMethodRequest request) throws Exception {
        return stripePaymentMethodService.updatePaymentMethod(paymentMethodId, request);
    }

    // ========== PAYMENT PROCESSING ==========

    @Override
    public StripePaymentResponse createPaymentIntent(StripePaymentRequest request) throws Exception {
        try {
            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(request.getAmountInCents())
                    .setCurrency(request.getCurrency().toLowerCase());

            if (request.getPaymentMethodId() != null) {
                paramsBuilder.setPaymentMethod(request.getPaymentMethodId());
            }
            
            if (request.getCustomerId() != null) {
                paramsBuilder.setCustomer(request.getCustomerId());
            }
            
            if (request.getDescription() != null) {
                paramsBuilder.setDescription(request.getDescription());
            }
            
            if (request.getReceiptEmail() != null) {
                paramsBuilder.setReceiptEmail(request.getReceiptEmail());
            }
            
            // Set confirmation method
            if (request.isAutomaticConfirmation()) {
                paramsBuilder.setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC);
            } else {
                paramsBuilder.setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL);
            }
            
            // Set capture method
            if (request.isAutomaticCapture()) {
                paramsBuilder.setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC);
            } else {
                paramsBuilder.setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL);
            }
            
            // Setup future usage if needed
            if (request.shouldSavePaymentMethod()) {
                paramsBuilder.setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION);
            }
            
            // Add metadata
            if (request.getMetadata() != null) {
                paramsBuilder.putAllMetadata(request.getMetadata());
            }

            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());
            return mapPaymentIntentToResponse(paymentIntent);
            
        } catch (StripeException e) {
            log.error("Failed to create Stripe payment intent", e);
            throw new PaymentGatewayException("Failed to create payment intent: " + e.getMessage(), e);
        }
    }

    // Helper method to map PaymentIntent to StripePaymentResponse
    private StripePaymentResponse mapPaymentIntentToResponse(PaymentIntent paymentIntent) {
        return StripePaymentResponse.builder()
                .paymentIntentId(paymentIntent.getId())
                .status(paymentIntent.getStatus())
                .clientSecret(paymentIntent.getClientSecret())
                .amount(BigDecimal.valueOf(paymentIntent.getAmount()).divide(BigDecimal.valueOf(100)))
                .currency(paymentIntent.getCurrency().toUpperCase())
                .customerId(paymentIntent.getCustomer())
                .paymentMethodId(paymentIntent.getPaymentMethod())
                .description(paymentIntent.getDescription())
                .captureMethod(paymentIntent.getCaptureMethod())
                .confirmationMethod(paymentIntent.getConfirmationMethod())
                .created(paymentIntent.getCreated())
                .livemode(paymentIntent.getLivemode())
                .build();
    }

    @Override
    public StripePaymentResponse getPaymentIntent(String paymentIntentId) throws Exception {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            return mapPaymentIntentToResponse(paymentIntent);
        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe payment intent: {}", paymentIntentId, e);
            throw new PaymentGatewayException("Failed to retrieve payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    public StripePaymentResponse confirmPaymentIntent(String paymentIntentId, String paymentMethodId) throws Exception {
        try {
            PaymentIntentConfirmParams.Builder paramsBuilder = PaymentIntentConfirmParams.builder();

            if (paymentMethodId != null) {
                paramsBuilder.setPaymentMethod(paymentMethodId);
            }

            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            paymentIntent = paymentIntent.confirm(paramsBuilder.build());

            return mapPaymentIntentToResponse(paymentIntent);
        } catch (StripeException e) {
            log.error("Failed to confirm Stripe payment intent: {}", paymentIntentId, e);
            throw new PaymentGatewayException("Failed to confirm payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    public StripePaymentResponse cancelPaymentIntent(String paymentIntentId) throws Exception {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            paymentIntent = paymentIntent.cancel();

            return mapPaymentIntentToResponse(paymentIntent);
        } catch (StripeException e) {
            log.error("Failed to cancel Stripe payment intent: {}", paymentIntentId, e);
            throw new PaymentGatewayException("Failed to cancel payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    public StripePaymentResponse capturePayment(String paymentIntentId, BigDecimal amount) throws Exception {
        try {
            PaymentIntentCaptureParams.Builder paramsBuilder = PaymentIntentCaptureParams.builder();

            if (amount != null) {
                paramsBuilder.setAmountToCapture(convertToSmallestUnit(amount, "USD"));
            }

            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            paymentIntent = paymentIntent.capture(paramsBuilder.build());

            return mapPaymentIntentToResponse(paymentIntent);
        } catch (StripeException e) {
            log.error("Failed to capture Stripe payment: {}", paymentIntentId, e);
            throw new PaymentGatewayException("Failed to capture payment: " + e.getMessage(), e);
        }
    }

    // ========== REFUND PROCESSING ==========

    @Override
    public StripePaymentResponse createRefund(String paymentIntentId, BigDecimal amount, String reason) throws Exception {
        try {
            RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId);

            if (amount != null) {
                paramsBuilder.setAmount(convertToSmallestUnit(amount, "USD"));
            }

            if (reason != null) {
                paramsBuilder.setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER);
            }

            com.stripe.model.Refund refund = com.stripe.model.Refund.create(paramsBuilder.build());

            return StripePaymentResponse.builder()
                    .paymentIntentId(paymentIntentId)
                    .status(refund.getStatus())
                    .amount(BigDecimal.valueOf(refund.getAmount()).divide(BigDecimal.valueOf(100)))
                    .currency(refund.getCurrency().toUpperCase())
                    .build();

        } catch (StripeException e) {
            log.error("Failed to create Stripe refund for payment: {}", paymentIntentId, e);
            throw new PaymentGatewayException("Failed to create refund: " + e.getMessage(), e);
        }
    }

    @Override
    public StripePaymentResponse getRefund(String refundId) throws Exception {
        try {
            com.stripe.model.Refund refund = com.stripe.model.Refund.retrieve(refundId);

            return StripePaymentResponse.builder()
                    .paymentIntentId(refund.getPaymentIntent())
                    .status(refund.getStatus())
                    .amount(BigDecimal.valueOf(refund.getAmount()).divide(BigDecimal.valueOf(100)))
                    .currency(refund.getCurrency().toUpperCase())
                    .build();

        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe refund: {}", refundId, e);
            throw new PaymentGatewayException("Failed to retrieve refund: " + e.getMessage(), e);
        }
    }

    @Override
    public List<StripePaymentResponse> listRefunds(String paymentIntentId) throws Exception {
        try {
            RefundListParams params = RefundListParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .build();

            RefundCollection refunds = com.stripe.model.Refund.list(params);

            return refunds.getData().stream()
                    .map(refund -> StripePaymentResponse.builder()
                            .paymentIntentId(refund.getPaymentIntent())
                            .status(refund.getStatus())
                            .amount(BigDecimal.valueOf(refund.getAmount()).divide(BigDecimal.valueOf(100)))
                            .currency(refund.getCurrency().toUpperCase())
                            .build())
                    .toList();

        } catch (StripeException e) {
            log.error("Failed to list Stripe refunds for payment: {}", paymentIntentId, e);
            throw new PaymentGatewayException("Failed to list refunds: " + e.getMessage(), e);
        }
    }

    // ========== WEBHOOK HANDLING ==========

    @Override
    public boolean verifyWebhookSignature(String payload, String signature, String secret) throws Exception {
        return stripeWebhookService.verifyWebhookSignature(payload, signature, secret);
    }

    @Override
    public StripeWebhookRequest parseWebhookPayload(String payload) throws Exception {
        return stripeWebhookService.parseWebhookPayload(payload);
    }

    @Override
    public void processWebhookEvent(StripeWebhookRequest webhookRequest) throws Exception {
        stripeWebhookService.processWebhookEvent(webhookRequest);
    }

    // ========== UTILITY METHODS ==========

    @Override
    public long convertToSmallestUnit(BigDecimal amount, String currency) {
        if (amount == null) return 0;

        // Most currencies use 2 decimal places (cents)
        // Some currencies like JPY use 0 decimal places
        switch (currency.toUpperCase()) {
            case "JPY", "KRW", "VND" -> {
                return amount.longValue();
            }
            default -> {
                return amount.multiply(BigDecimal.valueOf(100)).longValue();
            }
        }
    }

    @Override
    public BigDecimal convertFromSmallestUnit(long amount, String currency) {
        switch (currency.toUpperCase()) {
            case "JPY", "KRW", "VND" -> {
                return BigDecimal.valueOf(amount);
            }
            default -> {
                return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(100));
            }
        }
    }

    @Override
    public List<String> getSupportedCurrencies() {
        return Arrays.asList("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "SGD", "VND");
    }

    @Override
    public List<String> getSupportedPaymentMethodTypes() {
        return Arrays.asList("card", "bank_account", "wallet");
    }

    @Override
    public void validatePaymentRequest(ProcessPaymentRequest request) throws Exception {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException("Payment amount must be greater than zero");
        }

        if (request.getCurrency() == null) {
            throw new PaymentGatewayException("Currency is required");
        }

        if (!getSupportedCurrencies().contains(request.getCurrency().name())) {
            throw new PaymentGatewayException("Unsupported currency: " + request.getCurrency());
        }
    }

    @Override
    public void validatePaymentMethodRequest(CreatePaymentMethodRequest request) throws Exception {
        if (request.getType() == null) {
            throw new PaymentGatewayException("Payment method type is required");
        }

        if (request.getUserId() == null) {
            throw new PaymentGatewayException("User ID is required");
        }
    }

    @Override
    public void validateRefundRequest(RefundRequest request, Payment payment) throws Exception {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException("Refund amount must be greater than zero");
        }

        if (request.getAmount().compareTo(payment.getAmount()) > 0) {
            throw new PaymentGatewayException("Refund amount cannot exceed payment amount");
        }

        BigDecimal totalRefunded = payment.getTotalRefundedAmount();
        BigDecimal remainingAmount = payment.getAmount().subtract(totalRefunded);

        if (request.getAmount().compareTo(remainingAmount) > 0) {
            throw new PaymentGatewayException("Refund amount exceeds remaining refundable amount");
        }
    }

    // ========== MAPPING METHODS ==========

    @Override
    public StripePaymentRequest mapToGatewayPaymentRequest(ProcessPaymentRequest request) throws Exception {
        return StripePaymentRequest.builder()
                .amount(request.getAmount())
                .currency(request.getCurrency().name())
                .paymentMethodId(request.getStripePaymentMethodId())
                .customerId(request.getStripeCustomerId())
                .description(request.getDescription())
                .receiptEmail(request.getReceiptEmail())
                .confirmPayment(request.getConfirmPayment())
                .setupFutureUsage(request.getSavePaymentMethod())
                .build();
    }

    @Override
    public StripePaymentMethodRequest mapToGatewayPaymentMethodRequest(CreatePaymentMethodRequest request) throws Exception {
        StripePaymentMethodRequest.StripePaymentMethodRequestBuilder builder = StripePaymentMethodRequest.builder()
                .type(request.getType().name().toLowerCase())
                .customerId(request.getStripeCustomerId());

        if (request.getBillingAddress() != null) {
            builder.billingDetails(StripePaymentMethodRequest.StripeBillingDetails.builder()
                    .name(request.getBillingAddress().getCustomerName())
                    .address(StripePaymentMethodRequest.StripeAddress.builder()
                            .line1(request.getBillingAddress().getLine1())
                            .line2(request.getBillingAddress().getLine2())
                            .city(request.getBillingAddress().getCity())
                            .state(request.getBillingAddress().getState())
                            .postalCode(request.getBillingAddress().getPostalCode())
                            .country(request.getBillingAddress().getCountry())
                            .build())
                    .build());
        }

        return builder.build();
    }

    @Override
    public void mapGatewayResponseToPayment(StripePaymentResponse response, Payment payment) throws Exception {
        payment.setStripePaymentIntentId(response.getPaymentIntentId());
        payment.setStripeCustomerId(response.getCustomerId());
        payment.setStripePaymentMethodId(response.getPaymentMethodId());
        payment.setStripeResponse(response.toString());

        // Map status
        if (response.getStatus() != null) {
            payment.setStatus(mapStripeStatusToPaymentStatus(response.getStatus()));
        }
    }

    @Override
    public void mapGatewayResponseToPaymentMethod(StripePaymentMethodResponse response, PaymentMethod paymentMethod) throws Exception {
        paymentMethod.setStripePaymentMethodId(response.getPaymentMethodId());
        paymentMethod.setStripeCustomerId(response.getCustomerId());

        if (response.isCardPaymentMethod() && response.getCard() != null) {
            paymentMethod.setMaskedCardNumber(response.getMaskedNumber());
            paymentMethod.setCardBrand(response.getCard().getBrand());
            paymentMethod.setExpiryMonth(response.getCard().getExpMonth());
            paymentMethod.setExpiryYear(response.getCard().getExpYear());
            paymentMethod.setCardCountry(response.getCard().getCountry());
            paymentMethod.setCardFunding(response.getCard().getFunding());
        }
    }

    @Override
    public void mapGatewayResponseToRefund(StripePaymentResponse response, Refund refund) throws Exception {
        // Implementation for refund mapping
        refund.setStripeRefundId(response.getPaymentIntentId()); // This would be refund ID in actual implementation
        refund.setStripePaymentIntentId(response.getPaymentIntentId());
        refund.setStripeResponse(response.toString());
    }

    // Helper method to map Stripe status to internal status
    private org.de013.paymentservice.entity.enums.PaymentStatus mapStripeStatusToPaymentStatus(String stripeStatus) {
        return switch (stripeStatus.toLowerCase()) {
            case "succeeded" -> org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED;
            case "requires_action" -> org.de013.paymentservice.entity.enums.PaymentStatus.REQUIRES_ACTION;
            case "requires_confirmation" -> org.de013.paymentservice.entity.enums.PaymentStatus.REQUIRES_CONFIRMATION;
            case "requires_payment_method" -> org.de013.paymentservice.entity.enums.PaymentStatus.REQUIRES_PAYMENT_METHOD;
            case "canceled" -> org.de013.paymentservice.entity.enums.PaymentStatus.CANCELED;
            case "processing" -> org.de013.paymentservice.entity.enums.PaymentStatus.PROCESSING;
            default -> org.de013.paymentservice.entity.enums.PaymentStatus.FAILED;
        };
    }
}
