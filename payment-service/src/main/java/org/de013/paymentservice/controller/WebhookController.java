package org.de013.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.constant.ApiPaths;
import org.de013.common.controller.BaseController;
import org.de013.paymentservice.dto.payment.StripeWebhookRequest;
import org.de013.paymentservice.service.WebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for webhook operations
 */
@RestController
@RequestMapping(ApiPaths.API + ApiPaths.V1 + ApiPaths.WEBHOOKS)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhook", description = "Webhook handling APIs")
public class WebhookController extends BaseController {

    private final WebhookService webhookService;

    @Value("${stripe.webhook.secret:whsec_test}")
    private String webhookSecret;

    // ========== STRIPE WEBHOOK ==========

    @PostMapping(ApiPaths.STRIPE)
    @Operation(summary = "Handle Stripe webhook", description = "Process incoming Stripe webhook events")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid webhook signature or payload"),
            @ApiResponse(responseCode = "500", description = "Webhook processing failed")
    })
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @Parameter(description = "Stripe signature header")
            @RequestHeader("Stripe-Signature") String signature) {
        
        log.info("Received Stripe webhook with signature: {}", signature);
        
        try {
            // Process webhook directly (signature verification is done inside)
            webhookService.processStripeWebhook(payload, signature);

            log.info("Stripe webhook processed successfully");
            return ResponseEntity.ok("Webhook processed successfully");

        } catch (Exception e) {
            log.error("Failed to process Stripe webhook", e);
            webhookService.handleWebhookError(payload, signature, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
        }
    }

    // ========== WEBHOOK VALIDATION ==========

    @PostMapping(ApiPaths.STRIPE + ApiPaths.VERIFY)
    @Operation(summary = "Verify Stripe webhook signature", description = "Verify the signature of a Stripe webhook")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Signature verified"),
            @ApiResponse(responseCode = "400", description = "Invalid signature")
    })
    public ResponseEntity<Boolean> verifyStripeWebhookSignature(
            @RequestBody String payload,
            @Parameter(description = "Stripe signature header")
            @RequestHeader("Stripe-Signature") String signature) {

        log.debug("Verifying Stripe webhook signature");

        try {
            boolean isValid = webhookService.verifyWebhookSignature(payload, signature, webhookSecret);
            return ResponseEntity.ok(isValid);
        } catch (Exception e) {
            log.error("Failed to verify webhook signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(false);
        }
    }

    @PostMapping(ApiPaths.STRIPE + ApiPaths.PARSE)
    @Operation(summary = "Parse Stripe webhook payload", description = "Parse and validate Stripe webhook payload")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payload parsed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payload")
    })
    public ResponseEntity<StripeWebhookRequest> parseStripeWebhookPayload(
            @RequestBody String payload) {

        log.debug("Parsing Stripe webhook payload");

        try {
            StripeWebhookRequest webhookRequest = webhookService.parseWebhookPayload(payload);
            if (webhookRequest == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            return ResponseEntity.ok(webhookRequest);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // ========== WEBHOOK EVENT VALIDATION ==========

    @GetMapping(ApiPaths.STRIPE + ApiPaths.EVENT_TYPES + ApiPaths.EVENT_TYPE_PARAM + ApiPaths.VALID)
    @Operation(summary = "Check if event type is valid", description = "Check if a Stripe event type is supported")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Event type validation result")
    })
    public ResponseEntity<Boolean> isValidEventType(
            @Parameter(description = "Stripe event type") @PathVariable String eventType) {

        log.debug("Checking if event type is valid: {}", eventType);

        boolean isValid = webhookService.isValidEventType(eventType);
        return ResponseEntity.ok(isValid);
    }

    // ========== WEBHOOK ERROR HANDLING ==========

    @PostMapping(ApiPaths.STRIPE + ApiPaths.ERROR)
    @Operation(summary = "Handle webhook error", description = "Log and handle webhook processing errors")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Error handled successfully")
    })
    public ResponseEntity<String> handleWebhookError(
            @RequestBody String payload,
            @Parameter(description = "Stripe signature") @RequestParam(required = false) String signature,
            @Parameter(description = "Error message") @RequestParam String errorMessage) {

        log.info("Handling webhook error: {}", errorMessage);

        webhookService.handleWebhookError(payload, signature, new RuntimeException(errorMessage));
        return ResponseEntity.ok("Error handled successfully");
    }

    // ========== WEBHOOK TESTING ==========

    @PostMapping(ApiPaths.STRIPE + ApiPaths.TEST)
    @Operation(summary = "Test webhook processing", description = "Test webhook processing with sample data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Test completed successfully"),
            @ApiResponse(responseCode = "400", description = "Test failed")
    })
    public ResponseEntity<String> testWebhookProcessing(
            @Parameter(description = "Event type to test") @RequestParam String eventType,
            @Parameter(description = "Test payload") @RequestBody(required = false) String testPayload) {

        log.info("Testing webhook processing for event type: {}", eventType);

        try {
            // Process test webhook (use test payload)
            String testPayloadStr = testPayload != null ? testPayload :
                    String.format("{\"id\":\"test_event_%d\",\"type\":\"%s\",\"created\":%d,\"livemode\":false}",
                            System.currentTimeMillis(), eventType, System.currentTimeMillis() / 1000);
            webhookService.processStripeWebhook(testPayloadStr, "test_signature");

            return ResponseEntity.ok("Test webhook processed successfully");
        } catch (Exception e) {
            log.error("Test webhook processing failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Test failed: " + e.getMessage());
        }
    }


}
