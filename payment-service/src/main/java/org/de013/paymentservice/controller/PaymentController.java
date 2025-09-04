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
import org.de013.common.dto.PageResponse;
import org.de013.paymentservice.constant.PaymentConstants;
import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.paymentservice.dto.payment.PaymentResponse;
import org.de013.paymentservice.dto.payment.PaymentStatusResponse;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.service.PaymentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST Controller for payment operations
 */
@RestController
@RequestMapping(ApiPaths.API + ApiPaths.V1 + ApiPaths.PAYMENTS)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment", description = "Payment management APIs")
public class PaymentController extends BaseController {

    private final PaymentService paymentService;

    // ========== PAYMENT PROCESSING ==========

    @PostMapping(ApiPaths.PROCESS)
    @Operation(summary = "Process a payment", description = "Process a new payment for an order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request"),
            @ApiResponse(responseCode = "404", description = "Order or user not found"),
            @ApiResponse(responseCode = "500", description = "Payment processing failed")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request) {
        log.info("Processing payment request for order: {}", request.getOrderId());

        PaymentResponse response = paymentService.processPayment(request);
        return created(response, PaymentConstants.PAYMENT_PROCESSED);
    }

    @PostMapping(ApiPaths.PAYMENT_ID_PARAM + ApiPaths.CONFIRM)
    @Operation(summary = "Confirm a payment", description = "Confirm a payment with payment method")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment confirmed successfully"),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "400", description = "Payment cannot be confirmed")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentResponse>> confirmPayment(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId,
            @Parameter(description = "Payment method ID") @RequestParam String paymentMethodId) {
        log.info("Confirming payment: {} with payment method: {}", paymentId, paymentMethodId);

        PaymentResponse response = paymentService.confirmPayment(paymentId, paymentMethodId);
        return success(response, PaymentConstants.PAYMENT_CONFIRMED);
    }

    @PutMapping(ApiPaths.PAYMENT_ID_PARAM + ApiPaths.CANCEL)
    @Operation(summary = "Cancel a payment", description = "Cancel a pending payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment canceled successfully"),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "400", description = "Payment cannot be canceled")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentResponse>> cancelPayment(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId,
            @Parameter(description = "Cancellation reason") @RequestParam(required = false) String reason) {
        log.info("Canceling payment: {} with reason: {}", paymentId, reason);

        PaymentResponse response = paymentService.cancelPayment(paymentId, reason);
        return success(response, PaymentConstants.PAYMENT_CANCELED);
    }

    @PostMapping(ApiPaths.PAYMENT_ID_PARAM + ApiPaths.CAPTURE)
    @Operation(summary = "Capture a payment", description = "Capture an authorized payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment captured successfully"),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "400", description = "Payment cannot be captured")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentResponse>> capturePayment(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId,
            @Parameter(description = "Amount to capture") @RequestParam(required = false) BigDecimal amount) {
        log.info("Capturing payment: {} with amount: {}", paymentId, amount);

        PaymentResponse response = paymentService.capturePayment(paymentId, amount);
        return success(response, PaymentConstants.PAYMENT_CAPTURED);
    }

    // ========== PAYMENT RETRIEVAL ==========

    @GetMapping(ApiPaths.PAYMENT_ID_PARAM)
    @Operation(summary = "Get payment by ID", description = "Retrieve payment details by payment ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentResponse>> getPaymentById(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId) {
        log.debug("Getting payment by ID: {}", paymentId);

        return paymentService.getPaymentById(paymentId)
                .map(payment -> success(payment, PaymentConstants.PAYMENT_RETRIEVED))
                .orElse(notFound(PaymentConstants.PAYMENT_NOT_FOUND + " with ID: " + paymentId));
    }

    @GetMapping(ApiPaths.NUMBER + ApiPaths.PAYMENT_NUMBER_PARAM)
    @Operation(summary = "Get payment by number", description = "Retrieve payment details by payment number")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentResponse>> getPaymentByNumber(
            @Parameter(description = "Payment number") @PathVariable String paymentNumber) {
        log.debug("Getting payment by number: {}", paymentNumber);

        return paymentService.getPaymentByNumber(paymentNumber)
                .map(payment -> success(payment, PaymentConstants.PAYMENT_RETRIEVED))
                .orElse(notFound(PaymentConstants.PAYMENT_NOT_FOUND + " with number: " + paymentNumber));
    }

    @GetMapping(ApiPaths.ORDER + ApiPaths.ORDER_ID_PARAM)
    @Operation(summary = "Get payments by order ID", description = "Retrieve all payments for an order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<PaymentResponse>>> getPaymentsByOrderId(
            @Parameter(description = "Order ID") @PathVariable Long orderId) {
        log.debug("Getting payments for order: {}", orderId);

        List<PaymentResponse> payments = paymentService.getPaymentsByOrderId(orderId);
        return success(payments, PaymentConstants.PAYMENT_RETRIEVED);
    }

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM)
    @Operation(summary = "Get payments by user ID", description = "Retrieve paginated payments for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PageResponse<PaymentResponse>>> getPaymentsByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.debug("Getting payments for user: {} with pagination: {}", userId, pageable);

        Page<PaymentResponse> payments = paymentService.getPaymentsByUserId(userId, pageable);
        PageResponse<PaymentResponse> pageResponse = PageResponse.of(payments);
        return success(pageResponse, PaymentConstants.PAYMENT_RETRIEVED);
    }

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.STATUS + ApiPaths.STATUS_PARAM)
    @Operation(summary = "Get payments by user and status", description = "Retrieve payments for a user with specific status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<PaymentResponse>>> getPaymentsByUserIdAndStatus(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Payment status") @PathVariable PaymentStatus status) {
        log.debug("Getting payments for user: {} with status: {}", userId, status);

        List<PaymentResponse> payments = paymentService.getPaymentsByUserIdAndStatus(userId, status);
        return success(payments, PaymentConstants.PAYMENT_RETRIEVED);
    }

    // ========== PAYMENT STATUS ==========

    @GetMapping(ApiPaths.PAYMENT_ID_PARAM + ApiPaths.STATUS)
    @Operation(summary = "Get payment status", description = "Get detailed payment status information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment status retrieved"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentStatusResponse>> getPaymentStatus(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId) {
        log.debug("Getting payment status for: {}", paymentId);

        PaymentStatusResponse status = paymentService.getPaymentStatus(paymentId);
        return success(status, PaymentConstants.PAYMENT_RETRIEVED);
    }

    @GetMapping(ApiPaths.NUMBER + ApiPaths.PAYMENT_NUMBER_PARAM + ApiPaths.STATUS)
    @Operation(summary = "Get payment status by number", description = "Get payment status by payment number")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment status retrieved"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentStatusResponse>> getPaymentStatusByNumber(
            @Parameter(description = "Payment number") @PathVariable String paymentNumber) {
        log.debug("Getting payment status for number: {}", paymentNumber);

        PaymentStatusResponse status = paymentService.getPaymentStatusByNumber(paymentNumber);
        return success(status, PaymentConstants.PAYMENT_RETRIEVED);
    }

    @PutMapping(ApiPaths.PAYMENT_ID_PARAM + ApiPaths.STATUS)
    @Operation(summary = "Update payment status", description = "Update payment status manually")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment status updated"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentResponse>> updatePaymentStatus(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId,
            @Parameter(description = "New payment status") @RequestParam PaymentStatus status,
            @Parameter(description = "Status change reason") @RequestParam(required = false) String reason) {
        log.info("Updating payment status: {} -> {} with reason: {}", paymentId, status, reason);

        PaymentResponse response = paymentService.updatePaymentStatus(paymentId, status, reason);
        return success(response, "Payment status updated successfully");
    }

    @PostMapping(ApiPaths.PAYMENT_ID_PARAM + ApiPaths.SYNC)
    @Operation(summary = "Sync payment with Stripe", description = "Synchronize payment status with Stripe")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment synced successfully"),
            @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentResponse>> syncPaymentStatusWithStripe(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId) {
        log.info("Syncing payment with Stripe: {}", paymentId);

        PaymentResponse response = paymentService.syncPaymentStatusWithStripe(paymentId);
        return success(response, "Payment synchronized successfully");
    }

    // ========== PAYMENT SEARCH ==========

    @GetMapping(ApiPaths.SEARCH)
    @Operation(summary = "Search payments", description = "Search payments with various criteria")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payments retrieved successfully")
    })
    public ResponseEntity<Page<PaymentResponse>> searchPayments(
            @Parameter(description = "Payment number") @RequestParam(required = false) String paymentNumber,
            @Parameter(description = "User ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "Order ID") @RequestParam(required = false) Long orderId,
            @Parameter(description = "Payment status") @RequestParam(required = false) PaymentStatus status,
            @Parameter(description = "Minimum amount") @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "Maximum amount") @RequestParam(required = false) BigDecimal maxAmount,
            @Parameter(description = "Start date") @RequestParam(required = false) LocalDateTime startDate,
            @Parameter(description = "End date") @RequestParam(required = false) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.debug("Searching payments with criteria - paymentNumber: {}, userId: {}, orderId: {}, status: {}",
                paymentNumber, userId, orderId, status);

        Page<PaymentResponse> payments = paymentService.searchPayments(
                paymentNumber, userId, orderId, status, minAmount, maxAmount, startDate, endDate, pageable);
        return ResponseEntity.ok(payments);
    }

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.SUCCESSFUL)
    @Operation(summary = "Get successful payments by user", description = "Get all successful payments for a user")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<PaymentResponse>>> getSuccessfulPaymentsByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        log.debug("Getting successful payments for user: {}", userId);

        List<PaymentResponse> payments = paymentService.getSuccessfulPaymentsByUserId(userId);
        return success(payments, PaymentConstants.PAYMENT_RETRIEVED);
    }

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.FAILED)
    @Operation(summary = "Get failed payments by user", description = "Get all failed payments for a user")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<PaymentResponse>>> getFailedPaymentsByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        log.debug("Getting failed payments for user: {}", userId);

        List<PaymentResponse> payments = paymentService.getFailedPaymentsByUserId(userId);
        return success(payments, PaymentConstants.PAYMENT_RETRIEVED);
    }

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.PENDING)
    @Operation(summary = "Get pending payments by user", description = "Get all pending payments for a user")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<PaymentResponse>>> getPendingPaymentsByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        log.debug("Getting pending payments for user: {}", userId);

        List<PaymentResponse> payments = paymentService.getPendingPaymentsByUserId(userId);
        return success(payments, PaymentConstants.PAYMENT_RETRIEVED);
    }

    // ========== PAYMENT STATISTICS ==========

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.STATISTICS)
    @Operation(summary = "Get payment statistics by user", description = "Get payment statistics for a user")
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentService.PaymentStatistics>> getPaymentStatisticsByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        log.debug("Getting payment statistics for user: {}", userId);

        PaymentService.PaymentStatistics statistics = paymentService.getPaymentStatisticsByUserId(userId);
        return success(statistics, PaymentConstants.PAYMENT_RETRIEVED);
    }

    @GetMapping(ApiPaths.STATISTICS)
    @Operation(summary = "Get payment statistics by date range", description = "Get payment statistics for a date range")
    public ResponseEntity<org.de013.common.dto.ApiResponse<PaymentService.PaymentStatistics>> getPaymentStatisticsByDateRange(
            @Parameter(description = "Start date") @RequestParam LocalDateTime startDate,
            @Parameter(description = "End date") @RequestParam LocalDateTime endDate) {
        log.debug("Getting payment statistics for date range: {} to {}", startDate, endDate);

        PaymentService.PaymentStatistics statistics = paymentService.getPaymentStatisticsByDateRange(startDate, endDate);
        return success(statistics, PaymentConstants.PAYMENT_RETRIEVED);
    }

    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.TOTAL_AMOUNT)
    @Operation(summary = "Get total payment amount by user", description = "Get total successful payment amount for a user")
    public ResponseEntity<org.de013.common.dto.ApiResponse<BigDecimal>> getTotalPaymentAmountByUserId(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        log.debug("Getting total payment amount for user: {}", userId);

        BigDecimal totalAmount = paymentService.getTotalPaymentAmountByUserId(userId);
        return success(totalAmount, PaymentConstants.PAYMENT_RETRIEVED);
    }

    @GetMapping(ApiPaths.COUNT + ApiPaths.STATUS + ApiPaths.STATUS_PARAM)
    @Operation(summary = "Get payment count by status", description = "Get count of payments with specific status")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Long>> getPaymentCountByStatus(
            @Parameter(description = "Payment status") @PathVariable PaymentStatus status) {
        log.debug("Getting payment count for status: {}", status);

        Long count = paymentService.getPaymentCountByStatus(status);
        return success(count, PaymentConstants.PAYMENT_RETRIEVED);
    }
}
