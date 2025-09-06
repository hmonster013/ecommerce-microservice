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
import org.de013.paymentservice.dto.refund.RefundRequest;
import org.de013.paymentservice.dto.refund.RefundResponse;
import org.de013.paymentservice.entity.enums.RefundStatus;
import org.de013.paymentservice.service.RefundService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST Controller for refund operations
 */
@RestController
@RequestMapping(ApiPaths.REFUNDS)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Refund", description = "Refund management APIs")
public class RefundController extends BaseController {

    private final RefundService refundService;

    // ========== REFUND PROCESSING ==========

    @PostMapping
    @Operation(summary = "Create refund", description = "Create a new refund for a payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Refund created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid refund request"),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "500", description = "Refund creation failed")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<RefundResponse>> createRefund(
            @Valid @RequestBody RefundRequest request) {
        log.info("Creating refund for payment: {}, amount: {}", request.getPaymentId(), request.getAmount());

        RefundResponse response = refundService.createRefund(request);
        return created(response, PaymentConstants.REFUND_CREATED);
    }

    @PostMapping(ApiPaths.REFUND_ID_PARAM + ApiPaths.PROCESS)
    @Operation(summary = "Process refund", description = "Process a pending refund")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund processed successfully"),
            @ApiResponse(responseCode = "404", description = "Refund not found"),
            @ApiResponse(responseCode = "400", description = "Refund cannot be processed")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<RefundResponse>> processRefund(
            @Parameter(description = "Refund ID") @PathVariable Long refundId) {
        log.info("Processing refund: {}", refundId);

        RefundResponse response = refundService.processRefund(refundId);
        return success(response, PaymentConstants.REFUND_PROCESSED);
    }

    @PutMapping(ApiPaths.REFUND_ID_PARAM + ApiPaths.CANCEL)
    @Operation(summary = "Cancel refund", description = "Cancel a pending refund")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund canceled successfully"),
            @ApiResponse(responseCode = "404", description = "Refund not found"),
            @ApiResponse(responseCode = "400", description = "Refund cannot be canceled")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<RefundResponse>> cancelRefund(
            @Parameter(description = "Refund ID") @PathVariable Long refundId,
            @Parameter(description = "Cancellation reason") @RequestParam(required = false) String reason) {
        log.info("Canceling refund: {} with reason: {}", refundId, reason);

        RefundResponse response = refundService.cancelRefund(refundId, reason);
        return success(response, PaymentConstants.REFUND_CANCELED);
    }

    // ========== REFUND APPROVAL WORKFLOW ==========

    @PostMapping(ApiPaths.REFUND_ID_PARAM + ApiPaths.APPROVE)
    @Operation(summary = "Approve refund", description = "Approve a pending refund")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund approved successfully"),
            @ApiResponse(responseCode = "404", description = "Refund not found"),
            @ApiResponse(responseCode = "400", description = "Refund cannot be approved")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<RefundResponse>> approveRefund(
            @Parameter(description = "Refund ID") @PathVariable Long refundId,
            @Parameter(description = "Approver") @RequestParam String approvedBy) {
        log.info("Approving refund: {} by: {}", refundId, approvedBy);

        RefundResponse response = refundService.approveRefund(refundId, approvedBy);
        return success(response, PaymentConstants.REFUND_APPROVED);
    }

    @PostMapping(ApiPaths.REFUND_ID_PARAM + ApiPaths.REJECT)
    @Operation(summary = "Reject refund", description = "Reject a pending refund")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund rejected successfully"),
            @ApiResponse(responseCode = "404", description = "Refund not found"),
            @ApiResponse(responseCode = "400", description = "Refund cannot be rejected")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<RefundResponse>> rejectRefund(
            @Parameter(description = "Refund ID") @PathVariable Long refundId,
            @Parameter(description = "Rejector") @RequestParam String rejectedBy,
            @Parameter(description = "Rejection reason") @RequestParam String reason) {
        log.info("Rejecting refund: {} by: {} with reason: {}", refundId, rejectedBy, reason);

        RefundResponse response = refundService.rejectRefund(refundId, rejectedBy, reason);
        return success(response, PaymentConstants.REFUND_REJECTED);
    }

    @GetMapping(ApiPaths.REQUIRING_APPROVAL)
    @Operation(summary = "Get refunds requiring approval", description = "Get all refunds that require manual approval")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refunds retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Page<RefundResponse>>> getRefundsRequiringApproval(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.debug("Getting refunds requiring approval");

        List<RefundResponse> refundsList = refundService.getRefundsRequiringApproval();
        Page<RefundResponse> refunds = org.springframework.data.support.PageableExecutionUtils.getPage(
                refundsList, pageable, refundsList::size);
        return success(refunds, PaymentConstants.REFUND_RETRIEVED);
    }

    // ========== REFUND RETRIEVAL ==========

    @GetMapping(ApiPaths.REFUND_ID_PARAM)
    @Operation(summary = "Get refund by ID", description = "Retrieve refund details by refund ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund found"),
            @ApiResponse(responseCode = "404", description = "Refund not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<RefundResponse>> getRefundById(
            @Parameter(description = "Refund ID") @PathVariable Long refundId) {
        log.debug("Getting refund by ID: {}", refundId);

        return refundService.getRefundById(refundId)
                .map(refund -> success(refund, PaymentConstants.REFUND_RETRIEVED))
                .orElse(notFound(PaymentConstants.REFUND_NOT_FOUND + " with ID: " + refundId));
    }

    @GetMapping(ApiPaths.NUMBER + ApiPaths.REFUND_NUMBER_PARAM)
    @Operation(summary = "Get refund by number", description = "Retrieve refund details by refund number")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund found"),
            @ApiResponse(responseCode = "404", description = "Refund not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<RefundResponse>> getRefundByNumber(
            @Parameter(description = "Refund number") @PathVariable String refundNumber) {
        log.debug("Getting refund by number: {}", refundNumber);

        return refundService.getRefundByNumber(refundNumber)
                .map(refund -> success(refund, PaymentConstants.REFUND_RETRIEVED))
                .orElse(notFound(PaymentConstants.REFUND_NOT_FOUND + " with number: " + refundNumber));
    }

    @GetMapping(ApiPaths.PAYMENT + ApiPaths.PAYMENT_ID_PARAM)
    @Operation(summary = "Get refunds by payment ID", description = "Retrieve all refunds for a payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refunds retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<RefundResponse>>> getRefundsByPaymentId(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId) {
        log.debug("Getting refunds for payment: {}", paymentId);

        List<RefundResponse> refunds = refundService.getRefundsByPaymentId(paymentId);
        return success(refunds, PaymentConstants.REFUND_RETRIEVED);
    }

    @GetMapping(ApiPaths.ORDER + ApiPaths.ORDER_ID_PARAM)
    @Operation(summary = "Get refunds by order ID", description = "Retrieve all refunds for an order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refunds retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<RefundResponse>>> getRefundsByOrderId(
            @Parameter(description = "Order ID") @PathVariable Long orderId) {
        log.debug("Getting refunds for order: {}", orderId);

        List<RefundResponse> refunds = refundService.getRefundsByOrderId(orderId);
        return success(refunds, PaymentConstants.REFUND_RETRIEVED);
    }

    // ========== REFUND STATUS ==========

    @PutMapping(ApiPaths.REFUND_ID_PARAM + ApiPaths.STATUS)
    @Operation(summary = "Update refund status", description = "Update refund status manually")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund status updated"),
            @ApiResponse(responseCode = "404", description = "Refund not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<RefundResponse>> updateRefundStatus(
            @Parameter(description = "Refund ID") @PathVariable Long refundId,
            @Parameter(description = "New refund status") @RequestParam RefundStatus status,
            @Parameter(description = "Status change reason") @RequestParam(required = false) String reason) {
        log.info("Updating refund status: {} -> {} with reason: {}", refundId, status, reason);

        RefundResponse response = refundService.updateRefundStatus(refundId, status, reason);
        return success(response, "Refund status updated successfully");
    }

    @PostMapping(ApiPaths.REFUND_ID_PARAM + ApiPaths.SYNC)
    @Operation(summary = "Sync refund with Stripe", description = "Synchronize refund status with Stripe")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund synced successfully"),
            @ApiResponse(responseCode = "404", description = "Refund not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<RefundResponse>> syncRefundStatusWithStripe(
            @Parameter(description = "Refund ID") @PathVariable Long refundId) {
        log.info("Syncing refund with Stripe: {}", refundId);

        RefundResponse response = refundService.syncRefundStatusWithStripe(refundId);
        return success(response, "Refund synchronized successfully");
    }

    // ========== REFUND SEARCH ==========

    @GetMapping(ApiPaths.SEARCH)
    @Operation(summary = "Search refunds", description = "Search refunds with various criteria")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refunds retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Page<RefundResponse>>> searchRefunds(
            @Parameter(description = "Refund number") @RequestParam(required = false) String refundNumber,
            @Parameter(description = "Payment ID") @RequestParam(required = false) Long paymentId,
            @Parameter(description = "Order ID") @RequestParam(required = false) Long orderId,
            @Parameter(description = "Refund status") @RequestParam(required = false) RefundStatus status,
            @Parameter(description = "Refund type") @RequestParam(required = false) String refundType,
            @Parameter(description = "Minimum amount") @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "Maximum amount") @RequestParam(required = false) BigDecimal maxAmount,
            @Parameter(description = "Start date") @RequestParam(required = false) LocalDateTime startDate,
            @Parameter(description = "End date") @RequestParam(required = false) LocalDateTime endDate,
            @Parameter(description = "Initiated by") @RequestParam(required = false) String initiatedBy,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.debug("Searching refunds with criteria - refundNumber: {}, paymentId: {}, status: {}",
                refundNumber, paymentId, status);

        Page<RefundResponse> refunds = refundService.searchRefunds(
                refundNumber, paymentId, orderId, status, refundType, minAmount, maxAmount, startDate, endDate, initiatedBy, pageable);
        return success(refunds, PaymentConstants.REFUND_RETRIEVED);
    }

    @GetMapping(ApiPaths.PAYMENT + ApiPaths.PAYMENT_ID_PARAM + ApiPaths.SUCCESSFUL)
    @Operation(summary = "Get successful refunds by payment", description = "Get all successful refunds for a payment")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<RefundResponse>>> getSuccessfulRefundsByPaymentId(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId) {
        log.debug("Getting successful refunds for payment: {}", paymentId);

        List<RefundResponse> refunds = refundService.getSuccessfulRefundsByPaymentId(paymentId);
        return success(refunds, PaymentConstants.REFUND_RETRIEVED);
    }



    @GetMapping(ApiPaths.PAYMENT + ApiPaths.PAYMENT_ID_PARAM + ApiPaths.FAILED)
    @Operation(summary = "Get failed refunds by payment", description = "Get all failed refunds for a payment")
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<RefundResponse>>> getFailedRefundsByPaymentId(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId) {
        log.debug("Getting failed refunds for payment: {}", paymentId);

        List<RefundResponse> refunds = refundService.getFailedRefundsByPaymentId(paymentId);
        return success(refunds, PaymentConstants.REFUND_RETRIEVED);
    }

    // ========== REFUND STATISTICS ==========

    @GetMapping(ApiPaths.PAYMENT + ApiPaths.PAYMENT_ID_PARAM + ApiPaths.STATISTICS)
    @Operation(summary = "Get refund statistics by payment", description = "Get refund statistics for a payment")
    public ResponseEntity<org.de013.common.dto.ApiResponse<RefundService.RefundStatistics>> getRefundStatisticsByPaymentId(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId) {
        log.debug("Getting refund statistics for payment: {}", paymentId);

        RefundService.RefundStatistics statistics = refundService.getRefundStatisticsByPaymentId(paymentId);
        return success(statistics, PaymentConstants.REFUND_RETRIEVED);
    }

    @GetMapping(ApiPaths.STATISTICS)
    @Operation(summary = "Get refund statistics by date range", description = "Get refund statistics for a date range")
    public ResponseEntity<org.de013.common.dto.ApiResponse<RefundService.RefundStatistics>> getRefundStatisticsByDateRange(
            @Parameter(description = "Start date") @RequestParam LocalDateTime startDate,
            @Parameter(description = "End date") @RequestParam LocalDateTime endDate) {
        log.debug("Getting refund statistics for date range: {} to {}", startDate, endDate);

        RefundService.RefundStatistics statistics = refundService.getRefundStatisticsByDateRange(startDate, endDate);
        return success(statistics, PaymentConstants.REFUND_RETRIEVED);
    }

    @GetMapping(ApiPaths.COUNT + ApiPaths.STATUS + ApiPaths.STATUS_PARAM)
    @Operation(summary = "Get refund count by status", description = "Get count of refunds with specific status")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Long>> getRefundCountByStatus(
            @Parameter(description = "Refund status") @PathVariable RefundStatus status) {
        log.debug("Getting refund count for status: {}", status);

        Long count = refundService.getRefundCountByStatus(status);
        return success(count, PaymentConstants.REFUND_RETRIEVED);
    }

    @GetMapping(ApiPaths.PAYMENT + ApiPaths.PAYMENT_ID_PARAM + ApiPaths.TOTAL_AMOUNT)
    @Operation(summary = "Get total refunded amount", description = "Get total refunded amount for a payment")
    public ResponseEntity<org.de013.common.dto.ApiResponse<BigDecimal>> getTotalRefundedAmount(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId) {
        log.debug("Getting total refunded amount for payment: {}", paymentId);

        BigDecimal totalAmount = refundService.getTotalRefundedAmount(paymentId);
        return success(totalAmount, PaymentConstants.REFUND_RETRIEVED);
    }

    // ========== REFUND VALIDATION ==========

    @GetMapping(ApiPaths.PAYMENT + ApiPaths.PAYMENT_ID_PARAM + ApiPaths.CAN_REFUND)
    @Operation(summary = "Check if payment can be refunded", description = "Check if a payment can be refunded")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> canRefundPayment(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId) {
        log.debug("Checking if payment can be refunded: {}", paymentId);

        boolean canRefund = refundService.canRefundPayment(paymentId);
        return success(canRefund, "Refund eligibility checked successfully");
    }

    @GetMapping(ApiPaths.PAYMENT + ApiPaths.PAYMENT_ID_PARAM + ApiPaths.VALID_AMOUNT)
    @Operation(summary = "Check if refund amount is valid", description = "Check if refund amount is valid for a payment")
    public ResponseEntity<org.de013.common.dto.ApiResponse<Boolean>> isValidRefundAmount(
            @Parameter(description = "Payment ID") @PathVariable Long paymentId,
            @Parameter(description = "Refund amount") @RequestParam BigDecimal amount) {
        log.debug("Checking if refund amount {} is valid for payment: {}", amount, paymentId);

        boolean isValid = refundService.isValidRefundAmount(paymentId, amount);
        return success(isValid, "Refund amount validation completed");
    }
}
