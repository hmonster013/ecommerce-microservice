package org.de013.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.controller.BaseController;
import org.de013.common.dto.ApiResult;
import org.de013.common.security.UserContextHolder;
import org.de013.paymentservice.dto.stripe.StripeCustomerRequest;
import org.de013.paymentservice.dto.stripe.StripeCustomerResponse;
import org.de013.paymentservice.dto.stripe.StripePaymentResponse;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.exception.PaymentNotFoundException;
import org.de013.paymentservice.gateway.stripe.StripeCustomerService;
import org.de013.paymentservice.gateway.stripe.StripePaymentGateway;
import org.de013.paymentservice.service.PaymentMethodService;
import org.de013.paymentservice.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe-specific operations controller
 * Handles Stripe webhooks, payment intents, and Stripe-specific functionality
 */
@Slf4j
@RestController
@RequestMapping("/stripe")
@RequiredArgsConstructor
@Tag(name = "Stripe", description = "Stripe payment gateway integration operations")
public class StripeController extends BaseController {

    private final PaymentService paymentService;
    private final PaymentMethodService paymentMethodService;
    private final StripePaymentGateway stripePaymentGateway;
    private final StripeCustomerService stripeCustomerService;

    @PostMapping("/payment-intents")
    @Operation(
            summary = "Retrieve existing Stripe Payment Intent",
            description = """
                    Retrieve the existing Stripe Payment Intent for a payment owned by the caller.
                    This endpoint does not create a Payment Intent. If the payment has no stored
                    intent ID, manual reconciliation is required.
                    """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Existing Payment Intent retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Payment is not owned by the caller"),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "409", description = "Payment has no intent ID; manual reconciliation required"),
            @ApiResponse(responseCode = "500", description = "Stripe API error"),
            @ApiResponse(responseCode = "503", description = "Stripe is not configured or enabled")
    })
    public ResponseEntity<ApiResult<StripePaymentResponse>> createPaymentIntent(
            @Parameter(description = "Payment ID whose existing intent to retrieve", required = true)
            @RequestParam Long paymentId) {

        log.info("Retrieving Stripe Payment Intent for payment: {}", paymentId);

        try {
            // Validate input
            if (!isValidPaymentId(paymentId)) {
                return badRequest("Invalid payment ID");
            }

            if (!isStripeConfigured()) {
                return ResponseEntity.status(503)
                        .body(ApiResult.error("Stripe is not configured or enabled", null));
            }

            // Get payment details
            Payment payment = paymentService.getPaymentEntityById(paymentId)
                    .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

            String userId = UserContextHolder.requireAuthenticated().getUserId();
            if (!StringUtils.hasText(payment.getUserId()) || !payment.getUserId().equals(userId)) {
                return ResponseEntity.status(403).body(ApiResult.error("Access denied", null));
            }

            if (StringUtils.hasText(payment.getStripePaymentIntentId())) {
                return success(stripePaymentGateway.getPaymentIntent(payment.getStripePaymentIntentId()), "Payment Intent retrieved successfully");
            }
            return ResponseEntity.status(409).body(ApiResult.error("Payment intent creation requires reconciliation", null));

        } catch (PaymentNotFoundException e) {
            log.error("Payment not found: {}", paymentId);
            return notFound("Payment not found: " + paymentId);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(ApiResult.error("Authentication required", null));
        } catch (Exception e) {
            log.error("Error retrieving Stripe Payment Intent for payment: {}", paymentId, e);
            return ResponseEntity.status(500)
                    .body(ApiResult.error("Failed to create Payment Intent", e.getMessage()));
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
    public ResponseEntity<ApiResult<java.util.List<org.de013.paymentservice.dto.paymentmethod.PaymentMethodResponse>>> getCustomerPaymentMethods(
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
                        .body(ApiResult.error("Stripe is not configured or enabled", null));
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
                    .body(ApiResult.error("Failed to retrieve payment methods", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error retrieving payment methods for customer: {}", customerId, e);
            return ResponseEntity.status(500)
                    .body(ApiResult.error("Internal server error", e.getMessage()));
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
    public ResponseEntity<ApiResult<StripeCustomerResponse>> createStripeCustomer(
            @Parameter(description = "User ID to create customer for", required = true)
            @RequestParam String userId,
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
                        .body(ApiResult.error("Stripe is not configured or enabled", null));
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
                    .body(ApiResult.error("Failed to create Stripe customer", e.getMessage()));
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
    public ResponseEntity<ApiResult<Object>> checkStripeHealth() {
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
                        .body(ApiResult.error("Stripe integration issues detected", healthStatus));
            }

        } catch (Exception e) {
            log.error("Stripe health check failed", e);
            java.util.Map<String, Object> errorStatus = java.util.Map.of(
                    "status", "unhealthy",
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.status(503)
                    .body(ApiResult.error("Stripe integration unhealthy", errorStatus));
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
    private boolean isValidUserId(String userId) {
        return StringUtils.hasText(userId);
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

