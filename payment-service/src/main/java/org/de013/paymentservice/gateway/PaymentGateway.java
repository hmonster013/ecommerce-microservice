package org.de013.paymentservice.gateway;

import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.paymentservice.dto.paymentmethod.CreatePaymentMethodRequest;
import org.de013.paymentservice.dto.refund.RefundRequest;
import org.de013.paymentservice.dto.stripe.*;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.PaymentMethod;
import org.de013.paymentservice.entity.Refund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Payment Gateway abstraction interface
 * Defines common operations for all payment gateways
 */
public interface PaymentGateway {

    /**
     * Get the gateway provider name
     */
    String getProviderName();

    /**
     * Check if the gateway is enabled and configured
     */
    boolean isEnabled();

    /**
     * Health check for the gateway
     */
    boolean isHealthy();

    // ========== CUSTOMER MANAGEMENT ==========

    /**
     * Create a new customer in the gateway
     */
    StripeCustomerResponse createCustomer(StripeCustomerRequest request) throws Exception;

    /**
     * Retrieve customer information
     */
    StripeCustomerResponse getCustomer(String customerId) throws Exception;

    /**
     * Update customer information
     */
    StripeCustomerResponse updateCustomer(String customerId, StripeCustomerRequest request) throws Exception;

    /**
     * Delete customer
     */
    boolean deleteCustomer(String customerId) throws Exception;

    // ========== PAYMENT METHOD MANAGEMENT ==========

    /**
     * Create a payment method
     */
    StripePaymentMethodResponse createPaymentMethod(StripePaymentMethodRequest request) throws Exception;

    /**
     * Retrieve payment method information
     */
    StripePaymentMethodResponse getPaymentMethod(String paymentMethodId) throws Exception;

    /**
     * Attach payment method to customer
     */
    StripePaymentMethodResponse attachPaymentMethod(String paymentMethodId, String customerId) throws Exception;

    /**
     * Detach payment method from customer
     */
    StripePaymentMethodResponse detachPaymentMethod(String paymentMethodId) throws Exception;

    /**
     * List customer's payment methods
     */
    List<StripePaymentMethodResponse> listPaymentMethods(String customerId, String type) throws Exception;

    /**
     * Update payment method
     */
    StripePaymentMethodResponse updatePaymentMethod(String paymentMethodId, StripePaymentMethodRequest request) throws Exception;

    // ========== PAYMENT PROCESSING ==========

    /**
     * Create a payment intent
     */
    StripePaymentResponse createPaymentIntent(StripePaymentRequest request) throws Exception;

    /**
     * Retrieve payment intent
     */
    StripePaymentResponse getPaymentIntent(String paymentIntentId) throws Exception;

    /**
     * Confirm payment intent
     */
    StripePaymentResponse confirmPaymentIntent(String paymentIntentId, String paymentMethodId) throws Exception;

    /**
     * Cancel payment intent
     */
    StripePaymentResponse cancelPaymentIntent(String paymentIntentId) throws Exception;

    /**
     * Capture payment (for manual capture)
     */
    StripePaymentResponse capturePayment(String paymentIntentId, BigDecimal amount) throws Exception;

    // ========== REFUND PROCESSING ==========

    /**
     * Create a refund
     */
    StripePaymentResponse createRefund(String paymentIntentId, BigDecimal amount, String reason) throws Exception;

    /**
     * Retrieve refund information
     */
    StripePaymentResponse getRefund(String refundId) throws Exception;

    /**
     * List refunds for a payment
     */
    List<StripePaymentResponse> listRefunds(String paymentIntentId) throws Exception;

    // ========== WEBHOOK HANDLING ==========

    /**
     * Verify webhook signature
     */
    boolean verifyWebhookSignature(String payload, String signature, String secret) throws Exception;

    /**
     * Parse webhook payload
     */
    StripeWebhookRequest parseWebhookPayload(String payload) throws Exception;

    /**
     * Process webhook event
     */
    void processWebhookEvent(StripeWebhookRequest webhookRequest) throws Exception;

    // ========== UTILITY METHODS ==========

    /**
     * Convert amount to gateway's smallest currency unit (e.g., cents)
     */
    long convertToSmallestUnit(BigDecimal amount, String currency);

    /**
     * Convert amount from gateway's smallest currency unit
     */
    BigDecimal convertFromSmallestUnit(long amount, String currency);

    /**
     * Get supported currencies
     */
    List<String> getSupportedCurrencies();

    /**
     * Get supported payment method types
     */
    List<String> getSupportedPaymentMethodTypes();

    /**
     * Validate payment request
     */
    void validatePaymentRequest(ProcessPaymentRequest request) throws Exception;

    /**
     * Validate payment method request
     */
    void validatePaymentMethodRequest(CreatePaymentMethodRequest request) throws Exception;

    /**
     * Validate refund request
     */
    void validateRefundRequest(RefundRequest request, Payment payment) throws Exception;

    // ========== MAPPING METHODS ==========

    /**
     * Map internal payment to gateway payment request
     */
    StripePaymentRequest mapToGatewayPaymentRequest(ProcessPaymentRequest request) throws Exception;

    /**
     * Map internal payment method to gateway payment method request
     */
    StripePaymentMethodRequest mapToGatewayPaymentMethodRequest(CreatePaymentMethodRequest request) throws Exception;

    /**
     * Map gateway response to internal payment
     */
    void mapGatewayResponseToPayment(StripePaymentResponse response, Payment payment) throws Exception;

    /**
     * Map gateway response to internal payment method
     */
    void mapGatewayResponseToPaymentMethod(StripePaymentMethodResponse response, PaymentMethod paymentMethod) throws Exception;

    /**
     * Map gateway response to internal refund
     */
    void mapGatewayResponseToRefund(StripePaymentResponse response, Refund refund) throws Exception;
}
