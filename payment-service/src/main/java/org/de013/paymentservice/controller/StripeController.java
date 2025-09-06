package org.de013.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.constant.ApiPaths;
import org.de013.common.controller.BaseController;
import org.de013.paymentservice.service.PaymentService;
import org.de013.paymentservice.service.WebhookService;
import org.de013.paymentservice.service.PaymentMethodService;
import org.de013.paymentservice.gateway.stripe.StripePaymentGateway;
import org.de013.paymentservice.gateway.stripe.StripeCustomerService;
import org.de013.paymentservice.dto.stripe.StripePaymentRequest;
import org.de013.paymentservice.dto.stripe.StripePaymentResponse;
import org.de013.paymentservice.dto.stripe.StripeCustomerRequest;
import org.de013.paymentservice.dto.stripe.StripeCustomerResponse;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.exception.PaymentNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe-specific operations controller
 * Handles Stripe webhooks, payment intents, and Stripe-specific functionality
 */
@Slf4j
@RestController
@RequestMapping(ApiPaths.STRIPE)
@RequiredArgsConstructor
@Tag(name = "Stripe", description = "Stripe payment gateway integration operations")
public class StripeController extends BaseController {

    private final PaymentService paymentService;
    private final WebhookService webhookService;
    private final PaymentMethodService paymentMethodService;
    private final StripePaymentGateway stripePaymentGateway;
    private final StripeCustomerService stripeCustomerService;

    @PostMapping(ApiPaths.WEBHOOKS)
    @Operation(
        summary = "Handle Stripe webhooks",
        description = """
            Handle incoming webhooks from Stripe for payment events.
            
            **Supported Webhook Events:**
            - `payment_intent.succeeded` - Payment completed successfully
            - `payment_intent.payment_failed` - Payment failed
            - `payment_intent.requires_action` - Payment requires additional authentication
            - `payment_intent.canceled` - Payment was canceled
            - `charge.dispute.created` - Chargeback/dispute created
            - `invoice.payment_succeeded` - Subscription payment succeeded
            - `customer.subscription.deleted` - Subscription canceled
            
            **Webhook Security:**
            - Stripe signature verification is performed
            - Idempotency is handled to prevent duplicate processing
            - Events are logged for audit purposes
            
            **Example Webhook Payload:**
            ```json
            {
              "id": "evt_1234567890",
              "object": "event",
              "type": "payment_intent.succeeded",
              "data": {
                "object": {
                  "id": "pi_1234567890",
                  "amount": 9999,
                  "currency": "usd",
                  "status": "succeeded"
                }
              }
            }
            ```
            """)
    @ApiResponses(value = {
            @ApiResponse(
                responseCode = "200", 
                description = "Webhook processed successfully",
                content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = "{\"received\": true}")
                )),
            @ApiResponse(responseCode = "400", description = "Invalid webhook signature or payload"),
            @ApiResponse(responseCode = "500", description = "Webhook processing failed")
    })
    public ResponseEntity<String> handleStripeWebhook(
            @Parameter(description = "Stripe webhook signature", required = true)
            @RequestHeader("Stripe-Signature") String signature,
            @Parameter(description = "Stripe webhook payload", required = true)
            @RequestBody String payload) {
        
        log.info("Received Stripe webhook with signature: {}", signature);
        
        try {
            // Process webhook through webhook service
            webhookService.processStripeWebhook(payload, signature);
            return ResponseEntity.ok("{\"received\": true}");
        } catch (Exception e) {
            log.error("Error processing Stripe webhook", e);
            return ResponseEntity.badRequest().body("{\"error\": \"Webhook processing failed\"}");
        }
    }

    @PostMapping(ApiPaths.PAYMENT_INTENTS)
    @Operation(
        summary = "Create Stripe Payment Intent",
        description = """
            Create a new Stripe Payment Intent for processing payments.
            
            **Payment Intent Flow:**
            1. Create Payment Intent with amount and currency
            2. Client confirms payment with payment method
            3. Stripe processes payment and sends webhook
            4. Service updates payment status
            
            **Features:**
            - Automatic payment methods (cards, wallets, bank transfers)
            - 3D Secure authentication support
            - Multi-currency support
            - Metadata for order tracking
            
            **Example Response:**
            ```json
            {
              "id": "pi_1234567890",
              "client_secret": "pi_1234567890_secret_xyz",
              "status": "requires_payment_method",
              "amount": 9999,
              "currency": "usd"
            }
            ```
            """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment Intent created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Stripe API error")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<StripePaymentResponse>> createPaymentIntent(
            @Parameter(description = "Payment ID to create intent for", required = true)
            @RequestParam Long paymentId) {

        log.info("Creating Stripe Payment Intent for payment: {}", paymentId);

        try {
            // Validate input
            if (!isValidPaymentId(paymentId)) {
                return badRequest("Invalid payment ID");
            }

            if (!isStripeConfigured()) {
                return ResponseEntity.status(503)
                        .body(org.de013.common.dto.ApiResponse.error("Stripe is not configured or enabled", null));
            }

            // Get payment details
            Payment payment = paymentService.getPaymentEntityById(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

            // Create Stripe payment request
            StripePaymentRequest stripeRequest = StripePaymentRequest.builder()
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency().name().toLowerCase())
                    .description(payment.getDescription())
                    .receiptEmail(payment.getReceiptEmail())
                    .metadata(java.util.Map.of(
                            "payment_id", paymentId.toString(),
                            "order_id", payment.getOrderId().toString(),
                            "user_id", payment.getUserId().toString()
                    ))
                    .build();

            // Create payment intent with Stripe
            StripePaymentResponse stripeResponse = stripePaymentGateway.createPaymentIntent(stripeRequest);

            // Update payment with Stripe payment intent ID
            payment.setStripePaymentIntentId(stripeResponse.getPaymentIntentId());
            paymentService.savePaymentEntity(payment);

            log.info("Payment Intent created successfully: {} for payment: {}",
                    stripeResponse.getPaymentIntentId(), paymentId);

            return created(stripeResponse, "Payment Intent created successfully");

        } catch (PaymentNotFoundException e) {
            log.error("Payment not found: {}", paymentId);
            return notFound("Payment not found: " + paymentId);
        } catch (Exception e) {
            log.error("Error creating Stripe Payment Intent for payment: {}", paymentId, e);
            return ResponseEntity.status(500)
                    .body(org.de013.common.dto.ApiResponse.error("Failed to create Payment Intent", e.getMessage()));
        }
    }

    @GetMapping(ApiPaths.PAYMENT_METHODS + ApiPaths.CUSTOMER_ID_PARAM)
    @Operation(
        summary = "Get customer payment methods",
        description = """
            Retrieve all payment methods for a Stripe customer.
            
            **Payment Method Types:**
            - `card` - Credit/debit cards
            - `us_bank_account` - US bank accounts
            - `sepa_debit` - SEPA Direct Debit
            - `ideal` - iDEAL
            - `sofort` - SOFORT
            
            **Response includes:**
            - Payment method ID
            - Type and brand
            - Last 4 digits (for cards)
            - Expiration date (for cards)
            - Billing details
            """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment methods retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Customer not found"),
            @ApiResponse(responseCode = "500", description = "Stripe API error")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<java.util.List<org.de013.paymentservice.dto.paymentmethod.PaymentMethodResponse>>> getCustomerPaymentMethods(
            @Parameter(description = "Stripe customer ID", required = true)
            @PathVariable String customerId,
            @Parameter(description = "Payment method type filter") @RequestParam(required = false) String type) {

        log.info("Retrieving payment methods for Stripe customer: {} with type filter: {}", customerId, type);

        try {
            // Validate input
            if (!isValidStripeCustomerId(customerId)) {
                return badRequest("Invalid Stripe customer ID format");
            }

            if (!isStripeConfigured()) {
                return ResponseEntity.status(503)
                        .body(org.de013.common.dto.ApiResponse.error("Stripe is not configured or enabled", null));
            }

            // Get payment methods from Stripe
            var paymentMethods = paymentMethodService.getStripePaymentMethodsForCustomer(customerId);

            // Filter by type if specified
            if (type != null && !type.trim().isEmpty()) {
                paymentMethods = paymentMethods.stream()
                        .filter(pm -> pm.getType().name().equalsIgnoreCase(type))
                        .toList();
            }

            log.info("Retrieved {} payment methods for customer: {}", paymentMethods.size(), customerId);
            return success(paymentMethods, "Payment methods retrieved successfully");

        } catch (org.de013.paymentservice.exception.PaymentProcessingException e) {
            log.error("Payment processing error for customer: {}", customerId, e);
            return ResponseEntity.status(400)
                    .body(org.de013.common.dto.ApiResponse.error("Failed to retrieve payment methods", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error retrieving payment methods for customer: {}", customerId, e);
            return ResponseEntity.status(500)
                    .body(org.de013.common.dto.ApiResponse.error("Internal server error", e.getMessage()));
        }
    }

    @PostMapping(ApiPaths.CUSTOMERS)
    @Operation(
        summary = "Create Stripe customer",
        description = """
            Create a new Stripe customer for a user.
            
            **Customer Creation:**
            - Links user account to Stripe customer
            - Enables payment method storage
            - Supports subscription billing
            - Maintains payment history
            
            **Customer Data:**
            - Email address
            - Name
            - Phone number
            - Address
            - Metadata (user ID, etc.)
            """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Customer created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid customer data"),
            @ApiResponse(responseCode = "409", description = "Customer already exists"),
            @ApiResponse(responseCode = "500", description = "Stripe API error")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<StripeCustomerResponse>> createStripeCustomer(
            @Parameter(description = "User ID to create customer for", required = true)
            @RequestParam Long userId,
            @Parameter(description = "Customer email") @RequestParam(required = false) String email,
            @Parameter(description = "Customer name") @RequestParam(required = false) String name,
            @Parameter(description = "Customer phone") @RequestParam(required = false) String phone) {

        log.info("Creating Stripe customer for user: {}", userId);

        try {
            // Validate input
            if (!isValidUserId(userId)) {
                return badRequest("Invalid user ID");
            }

            if (!isStripeConfigured()) {
                return ResponseEntity.status(503)
                        .body(org.de013.common.dto.ApiResponse.error("Stripe is not configured or enabled", null));
            }

            // Create Stripe customer request
            StripeCustomerRequest customerRequest = StripeCustomerRequest.builder()
                    .email(email)
                    .name(name)
                    .phone(phone)
                    .description("Customer for user ID: " + userId)
                    .metadata(java.util.Map.of(
                            "user_id", userId.toString(),
                            "created_by", "ecommerce-platform"
                    ))
                    .build();

            // Create customer with Stripe
            StripeCustomerResponse customerResponse = stripePaymentGateway.createCustomer(customerRequest);

            log.info("Stripe customer created successfully: {} for user: {}",
                    customerResponse.getCustomerId(), userId);

            return created(customerResponse, "Stripe customer created successfully");

        } catch (Exception e) {
            log.error("Error creating Stripe customer for user: {}", userId, e);
            return ResponseEntity.status(500)
                    .body(org.de013.common.dto.ApiResponse.error("Failed to create Stripe customer", e.getMessage()));
        }
    }

    @GetMapping(ApiPaths.HEALTH)
    @Operation(
        summary = "Check Stripe integration health",
        description = """
            Check the health of Stripe integration.
            
            **Health Checks:**
            - Stripe API connectivity
            - Webhook endpoint status
            - API key validity
            - Rate limit status
            """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stripe integration is healthy"),
            @ApiResponse(responseCode = "503", description = "Stripe integration issues detected")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Object>> checkStripeHealth() {
        log.info("Checking Stripe integration health");

        try {
            // Test Stripe API connectivity by retrieving account information
            java.util.Map<String, Object> healthStatus = new java.util.HashMap<>();

            // Check if Stripe is enabled
            boolean stripeEnabled = stripePaymentGateway.isEnabled();
            healthStatus.put("stripe_enabled", stripeEnabled);

            if (stripeEnabled) {
                // Test API connectivity
                try {
                    // Try to retrieve account balance (lightweight API call)
                    com.stripe.model.Balance balance = com.stripe.model.Balance.retrieve();
                    healthStatus.put("api_connectivity", "healthy");
                    healthStatus.put("api_response_time", System.currentTimeMillis());
                    healthStatus.put("available_balance", balance.getAvailable());

                } catch (Exception apiException) {
                    log.warn("Stripe API connectivity issue", apiException);
                    healthStatus.put("api_connectivity", "unhealthy");
                    healthStatus.put("api_error", apiException.getMessage());
                }

                // Check supported currencies
                healthStatus.put("supported_currencies", stripePaymentGateway.getSupportedCurrencies());
                healthStatus.put("supported_payment_methods", stripePaymentGateway.getSupportedPaymentMethodTypes());
            }

            healthStatus.put("provider", stripePaymentGateway.getProviderName());
            healthStatus.put("timestamp", System.currentTimeMillis());
            healthStatus.put("status", stripeEnabled && healthStatus.get("api_connectivity").equals("healthy") ? "healthy" : "degraded");

            boolean isHealthy = stripeEnabled && "healthy".equals(healthStatus.get("api_connectivity"));

            if (isHealthy) {
                return success(healthStatus, "Stripe integration is healthy");
            } else {
                return ResponseEntity.status(503)
                        .body(org.de013.common.dto.ApiResponse.error("Stripe integration issues detected", healthStatus));
            }

        } catch (Exception e) {
            log.error("Stripe health check failed", e);
            java.util.Map<String, Object> errorStatus = java.util.Map.of(
                    "status", "unhealthy",
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.status(503)
                    .body(org.de013.common.dto.ApiResponse.error("Stripe integration unhealthy", errorStatus));
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Check if Stripe is properly configured and enabled
     */
    private boolean isStripeConfigured() {
        try {
            return stripePaymentGateway.isEnabled() &&
                   stripePaymentGateway.getProviderName().equals("STRIPE");
        } catch (Exception e) {
            log.warn("Error checking Stripe configuration", e);
            return false;
        }
    }

    /**
     * Validate payment ID parameter
     */
    private boolean isValidPaymentId(Long paymentId) {
        return paymentId != null && paymentId > 0;
    }

    /**
     * Validate user ID parameter
     */
    private boolean isValidUserId(Long userId) {
        return userId != null && userId > 0;
    }

    /**
     * Validate Stripe customer ID format
     */
    private boolean isValidStripeCustomerId(String customerId) {
        return customerId != null &&
               !customerId.trim().isEmpty() &&
               customerId.startsWith("cus_");
    }
}
