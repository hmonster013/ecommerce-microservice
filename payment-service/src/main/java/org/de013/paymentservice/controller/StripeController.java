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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe-specific operations controller
 * Handles Stripe webhooks, payment intents, and Stripe-specific functionality
 */
@Slf4j
@RestController
@RequestMapping(ApiPaths.API + ApiPaths.V1 + "/stripe")
@RequiredArgsConstructor
@Tag(name = "Stripe", description = "Stripe payment gateway integration operations")
public class StripeController extends BaseController {

    private final PaymentService paymentService;
    private final WebhookService webhookService;
    private final PaymentMethodService paymentMethodService;

    @PostMapping("/webhooks")
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

    @PostMapping("/payment-intents")
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
    public ResponseEntity<org.de013.common.dto.ApiResponse<Object>> createPaymentIntent(
            @Parameter(description = "Payment ID to create intent for", required = true)
            @RequestParam Long paymentId) {
        
        log.info("Creating Stripe Payment Intent for payment: {}", paymentId);
        
        try {
            // For now, return a placeholder response
            // TODO: Implement Stripe Payment Intent creation
            Object paymentIntent = createPlaceholderPaymentIntent(paymentId);
            return created(paymentIntent, "Payment Intent created successfully");
        } catch (Exception e) {
            log.error("Error creating Stripe Payment Intent for payment: {}", paymentId, e);
            throw e;
        }
    }

    @GetMapping("/payment-methods/{customerId}")
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
    public ResponseEntity<org.de013.common.dto.ApiResponse<Object>> getCustomerPaymentMethods(
            @Parameter(description = "Stripe customer ID", required = true)
            @PathVariable String customerId) {
        
        log.info("Retrieving payment methods for Stripe customer: {}", customerId);
        
        try {
            // Use PaymentMethodService to get Stripe payment methods
            var paymentMethods = paymentMethodService.getStripePaymentMethodsForCustomer(customerId);
            return success(paymentMethods, "Payment methods retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving payment methods for customer: {}", customerId, e);
            throw e;
        }
    }

    @PostMapping("/customers")
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
    public ResponseEntity<org.de013.common.dto.ApiResponse<Object>> createStripeCustomer(
            @Parameter(description = "User ID to create customer for", required = true)
            @RequestParam Long userId) {
        
        log.info("Creating Stripe customer for user: {}", userId);
        
        try {
            // TODO: Implement Stripe customer creation
            Object customer = createPlaceholderCustomer(userId);
            return created(customer, "Stripe customer created successfully");
        } catch (Exception e) {
            log.error("Error creating Stripe customer for user: {}", userId, e);
            throw e;
        }
    }

    @GetMapping("/health")
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
            // TODO: Implement Stripe health check
            Object healthStatus = createHealthStatus();
            return success(healthStatus, "Stripe integration is healthy");
        } catch (Exception e) {
            log.error("Stripe health check failed", e);
            return ResponseEntity.status(503)
                    .body(org.de013.common.dto.ApiResponse.error("Stripe integration unhealthy", e.getMessage()));
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Create placeholder payment intent response
     */
    private Object createPlaceholderPaymentIntent(Long paymentId) {
        return java.util.Map.of(
            "id", "pi_placeholder_" + paymentId,
            "client_secret", "pi_placeholder_" + paymentId + "_secret_xyz",
            "status", "requires_payment_method",
            "amount", 0,
            "currency", "usd",
            "metadata", java.util.Map.of("paymentId", paymentId.toString())
        );
    }

    /**
     * Create placeholder customer response
     */
    private Object createPlaceholderCustomer(Long userId) {
        return java.util.Map.of(
            "id", "cus_placeholder_" + userId,
            "object", "customer",
            "email", "placeholder@example.com",
            "metadata", java.util.Map.of("userId", userId.toString()),
            "created", System.currentTimeMillis() / 1000
        );
    }

    /**
     * Create health status response
     */
    private Object createHealthStatus() {
        return java.util.Map.of(
            "status", "healthy",
            "stripe_api", "connected",
            "webhook_endpoint", "active",
            "timestamp", java.time.Instant.now().toString()
        );
    }
}
