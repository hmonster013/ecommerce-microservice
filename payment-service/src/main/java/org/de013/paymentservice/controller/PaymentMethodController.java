package org.de013.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.constant.ApiPaths;
import org.de013.common.controller.BaseController;
import org.de013.paymentservice.constant.PaymentConstants;
import org.de013.paymentservice.dto.paymentmethod.CreatePaymentMethodRequest;
import org.de013.paymentservice.dto.paymentmethod.PaymentMethodResponse;
import org.de013.paymentservice.dto.paymentmethod.UpdatePaymentMethodRequest;
import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.de013.paymentservice.service.PaymentMethodService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST Controller for payment method operations
 */
@RestController
@RequestMapping(ApiPaths.API + ApiPaths.V1 + ApiPaths.PAYMENT_METHODS)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Method", description = "Payment method management APIs")
public class PaymentMethodController extends BaseController {

    private final PaymentMethodService paymentMethodService;

    // ========== PAYMENT METHOD CRUD ==========

    @PostMapping
    @Operation(summary = "Create payment method", description = "Create a new payment method for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment method created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment method request"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Payment method creation failed")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentMethodResponse>> createPaymentMethod(
            @Valid @RequestBody CreatePaymentMethodRequest request) {
        log.info("Creating payment method for user: {}", request.getUserId());

        PaymentMethodResponse response = paymentMethodService.createPaymentMethod(request);
        return created(response, PaymentConstants.PAYMENT_METHOD_CREATED);
    }

    @GetMapping(ApiPaths.PAYMENT_METHOD_ID_PARAM)
    @Operation(summary = "Get payment method by ID", description = "Retrieve payment method details by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method found"),
            @ApiResponse(responseCode = "404", description = "Payment method not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentMethodResponse>> getPaymentMethodById(
            @Parameter(description = "Payment method ID") @PathVariable Long paymentMethodId) {
        log.debug("Getting payment method by ID: {}", paymentMethodId);

        return paymentMethodService.getPaymentMethodById(paymentMethodId)
                .map(paymentMethod -> success(paymentMethod, PaymentConstants.PAYMENT_METHOD_RETRIEVED))
                .orElse(notFound(PaymentConstants.PAYMENT_METHOD_NOT_FOUND));
    }

    @PutMapping(ApiPaths.PAYMENT_METHOD_ID_PARAM)
    @Operation(summary = "Update payment method", description = "Update an existing payment method")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method updated successfully"),
            @ApiResponse(responseCode = "404", description = "Payment method not found"),
            @ApiResponse(responseCode = "400", description = "Invalid update request")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentMethodResponse>> updatePaymentMethod(
            @Parameter(description = "Payment method ID") @PathVariable Long paymentMethodId,
            @Valid @RequestBody UpdatePaymentMethodRequest request) {
        log.info("Updating payment method: {}", paymentMethodId);

        PaymentMethodResponse response = paymentMethodService.updatePaymentMethod(paymentMethodId, request);
        return success(response, PaymentConstants.PAYMENT_METHOD_UPDATED);
    }

    @DeleteMapping(ApiPaths.PAYMENT_METHOD_ID_PARAM)
    @Operation(summary = "Delete payment method", description = "Delete a payment method")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Payment method deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Payment method not found"),
            @ApiResponse(responseCode = "400", description = "Payment method cannot be deleted")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Void>> deletePaymentMethod(
            @Parameter(description = "Payment method ID") @PathVariable Long paymentMethodId) {
        log.info("Deleting payment method: {}", paymentMethodId);

        paymentMethodService.deletePaymentMethod(paymentMethodId);
        return success(null, PaymentConstants.PAYMENT_METHOD_DELETED);
    }

    // ========== USER PAYMENT METHODS ==========

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM)
    @Operation(summary = "Get payment methods by user", description = "Get all payment methods for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment methods retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<PaymentMethodResponse>>> getPaymentMethodsByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        log.debug("Getting payment methods for user: {}", userId);

        List<PaymentMethodResponse> paymentMethods = paymentMethodService.getPaymentMethodsByUserId(userId);
        return success(paymentMethods, PaymentConstants.PAYMENT_METHOD_RETRIEVED);
    }

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.ACTIVE)
    @Operation(summary = "Get active payment methods by user", description = "Get all active payment methods for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active payment methods retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<PaymentMethodResponse>>> getActivePaymentMethodsByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        log.debug("Getting active payment methods for user: {}", userId);

        List<PaymentMethodResponse> paymentMethods = paymentMethodService.getActivePaymentMethodsByUserId(userId);
        return success(paymentMethods, PaymentConstants.PAYMENT_METHOD_RETRIEVED);
    }

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.TYPE + ApiPaths.TYPE_PARAM)
    @Operation(summary = "Get payment methods by user and type", description = "Get payment methods for a user by type")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment methods retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<PaymentMethodResponse>>> getPaymentMethodsByUserIdAndType(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Payment method type") @PathVariable PaymentMethodType type) {
        log.debug("Getting payment methods for user: {} and type: {}", userId, type);

        List<PaymentMethodResponse> paymentMethods = paymentMethodService.getPaymentMethodsByUserIdAndType(userId, type);
        return success(paymentMethods, PaymentConstants.PAYMENT_METHOD_RETRIEVED);
    }

    // ========== DEFAULT PAYMENT METHOD ==========

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.DEFAULT)
    @Operation(summary = "Get default payment method", description = "Get the default payment method for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Default payment method found"),
            @ApiResponse(responseCode = "404", description = "No default payment method found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentMethodResponse>> getDefaultPaymentMethodByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        log.debug("Getting default payment method for user: {}", userId);

        return paymentMethodService.getDefaultPaymentMethodByUserId(userId)
                .map(paymentMethod -> success(paymentMethod, PaymentConstants.PAYMENT_METHOD_RETRIEVED))
                .orElse(notFound(PaymentConstants.PAYMENT_METHOD_NOT_FOUND));
    }

    @PutMapping(ApiPaths.PAYMENT_METHOD_ID_PARAM + ApiPaths.SET_DEFAULT)
    @Operation(summary = "Set as default payment method", description = "Set a payment method as default for the user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method set as default successfully"),
            @ApiResponse(responseCode = "404", description = "Payment method not found"),
            @ApiResponse(responseCode = "400", description = "Payment method cannot be set as default")
    })
    public ResponseEntity<PaymentMethodResponse> setAsDefaultPaymentMethod(
            @Parameter(description = "Payment method ID") @PathVariable Long paymentMethodId) {
        log.info("Setting payment method as default: {}", paymentMethodId);
        
        PaymentMethodResponse response = paymentMethodService.setAsDefaultPaymentMethod(paymentMethodId);
        return ResponseEntity.ok(response);
    }

    // ========== PAYMENT METHOD STATUS ==========

    @PutMapping(ApiPaths.PAYMENT_METHOD_ID_PARAM + ApiPaths.ACTIVATE)
    @Operation(summary = "Activate payment method", description = "Activate a payment method")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method activated successfully"),
            @ApiResponse(responseCode = "404", description = "Payment method not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentMethodResponse>> activatePaymentMethod(
            @Parameter(description = "Payment method ID") @PathVariable Long paymentMethodId) {
        log.info("Activating payment method: {}", paymentMethodId);

        PaymentMethodResponse response = paymentMethodService.activatePaymentMethod(paymentMethodId);
        return success(response, PaymentConstants.PAYMENT_METHOD_ACTIVATED);
    }

    @PutMapping(ApiPaths.PAYMENT_METHOD_ID_PARAM + ApiPaths.DEACTIVATE)
    @Operation(summary = "Deactivate payment method", description = "Deactivate a payment method")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Payment method not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentMethodResponse>> deactivatePaymentMethod(
            @Parameter(description = "Payment method ID") @PathVariable Long paymentMethodId) {
        log.info("Deactivating payment method: {}", paymentMethodId);

        PaymentMethodResponse response = paymentMethodService.deactivatePaymentMethod(paymentMethodId);
        return success(response, PaymentConstants.PAYMENT_METHOD_DEACTIVATED);
    }

    // ========== STRIPE INTEGRATION ==========

    @PostMapping(ApiPaths.PAYMENT_METHOD_ID_PARAM + ApiPaths.ATTACH_CUSTOMER)
    @Operation(summary = "Attach payment method to customer", description = "Attach payment method to Stripe customer")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method attached successfully"),
            @ApiResponse(responseCode = "404", description = "Payment method not found"),
            @ApiResponse(responseCode = "500", description = "Stripe operation failed")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentMethodResponse>> attachPaymentMethodToCustomer(
            @Parameter(description = "Payment method ID") @PathVariable Long paymentMethodId,
            @Parameter(description = "Stripe customer ID") @RequestParam String customerId) {
        log.info("Attaching payment method {} to customer: {}", paymentMethodId, customerId);

        PaymentMethodResponse response = paymentMethodService.attachPaymentMethodToCustomer(paymentMethodId, customerId);
        return success(response, "Payment method attached to customer successfully");
    }

    @PostMapping(ApiPaths.PAYMENT_METHOD_ID_PARAM + ApiPaths.DETACH_CUSTOMER)
    @Operation(summary = "Detach payment method from customer", description = "Detach payment method from Stripe customer")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method detached successfully"),
            @ApiResponse(responseCode = "404", description = "Payment method not found"),
            @ApiResponse(responseCode = "500", description = "Stripe operation failed")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentMethodResponse>> detachPaymentMethodFromCustomer(
            @Parameter(description = "Payment method ID") @PathVariable Long paymentMethodId) {
        log.info("Detaching payment method from customer: {}", paymentMethodId);

        PaymentMethodResponse response = paymentMethodService.detachPaymentMethodFromCustomer(paymentMethodId);
        return success(response, "Payment method detached from customer successfully");
    }

    @PostMapping(ApiPaths.PAYMENT_METHOD_ID_PARAM + ApiPaths.SYNC)
    @Operation(summary = "Sync payment method with Stripe", description = "Synchronize payment method with Stripe")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment method synced successfully"),
            @ApiResponse(responseCode = "404", description = "Payment method not found"),
            @ApiResponse(responseCode = "500", description = "Stripe sync failed")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentMethodResponse>> syncPaymentMethodWithStripe(
            @Parameter(description = "Payment method ID") @PathVariable Long paymentMethodId) {
        log.info("Syncing payment method with Stripe: {}", paymentMethodId);

        PaymentMethodResponse response = paymentMethodService.syncPaymentMethodWithStripe(paymentMethodId);
        return success(response, "Payment method synchronized successfully");
    }

    // ========== CARD MANAGEMENT ==========

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.CARDS)
    @Operation(summary = "Get card payment methods", description = "Get all card payment methods for a user")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<PaymentMethodResponse>>> getCardPaymentMethodsByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        log.debug("Getting card payment methods for user: {}", userId);

        List<PaymentMethodResponse> cardMethods = paymentMethodService.getCardPaymentMethodsByUserId(userId);
        return success(cardMethods, PaymentConstants.PAYMENT_METHOD_RETRIEVED);
    }

    @GetMapping(ApiPaths.CARDS + ApiPaths.EXPIRED)
    @Operation(summary = "Get expired card payment methods", description = "Get all expired card payment methods")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<PaymentMethodResponse>>> getExpiredCardPaymentMethods() {
        log.debug("Getting expired card payment methods");

        List<PaymentMethodResponse> expiredCards = paymentMethodService.getExpiredCardPaymentMethods();
        return success(expiredCards, PaymentConstants.PAYMENT_METHOD_RETRIEVED);
    }

    // ========== PAYMENT METHOD SEARCH ==========

    @GetMapping(ApiPaths.SEARCH)
    @Operation(summary = "Search payment methods", description = "Search payment methods with various criteria")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Page<PaymentMethodResponse>>> searchPaymentMethods(
            @Parameter(description = "User ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "Payment method type") @RequestParam(required = false) PaymentMethodType type,
            @Parameter(description = "Is active") @RequestParam(required = false) Boolean isActive,
            @Parameter(description = "Is default") @RequestParam(required = false) Boolean isDefault,
            @Parameter(description = "Provider") @RequestParam(required = false) String provider,
            @Parameter(description = "Brand") @RequestParam(required = false) String brand,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.debug("Searching payment methods with criteria - userId: {}, type: {}, isActive: {}",
                userId, type, isActive);

        Page<PaymentMethodResponse> paymentMethods = paymentMethodService.searchPaymentMethods(
                userId, type, isActive, isDefault, provider, brand, pageable);
        return success(paymentMethods, PaymentConstants.PAYMENT_METHOD_RETRIEVED);
    }

    // ========== PAYMENT METHOD STATISTICS ==========

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.STATISTICS)
    @Operation(summary = "Get payment method statistics", description = "Get payment method statistics for a user")
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentMethodService.PaymentMethodStatistics>> getPaymentMethodStatisticsByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        log.debug("Getting payment method statistics for user: {}", userId);

        PaymentMethodService.PaymentMethodStatistics statistics =
                paymentMethodService.getPaymentMethodStatisticsByUserId(userId);
        return success(statistics, PaymentConstants.PAYMENT_METHOD_RETRIEVED);
    }

    // ========== CLEANUP OPERATIONS ==========

    @PostMapping(ApiPaths.CLEANUP + ApiPaths.INACTIVE)
    @Operation(summary = "Cleanup inactive payment methods", description = "Remove inactive payment methods older than specified days")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Void>> cleanupInactivePaymentMethods(
            @Parameter(description = "Days threshold") @RequestParam(defaultValue = "90") int daysThreshold) {
        log.info("Cleaning up inactive payment methods older than {} days", daysThreshold);

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysThreshold);
        paymentMethodService.cleanupInactivePaymentMethods(cutoffDate);
        return success(null, "Inactive payment methods cleaned up successfully");
    }

    @PostMapping(ApiPaths.CLEANUP + ApiPaths.ORPHANED)
    @Operation(summary = "Cleanup orphaned payment methods", description = "Remove payment methods without valid Stripe references")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Void>> cleanupOrphanedPaymentMethods() {
        log.info("Cleaning up orphaned payment methods");

        paymentMethodService.cleanupOrphanedPaymentMethods();
        return success(null, "Orphaned payment methods cleaned up successfully");
    }
}
