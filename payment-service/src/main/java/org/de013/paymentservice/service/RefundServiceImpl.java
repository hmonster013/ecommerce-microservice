package org.de013.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.dto.refund.RefundRequest;
import org.de013.paymentservice.dto.refund.RefundResponse;
import org.de013.paymentservice.dto.stripe.StripeRefundRequest;
import org.de013.paymentservice.dto.stripe.StripeRefundResponse;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.Refund;
import org.de013.paymentservice.entity.enums.RefundStatus;
import org.de013.paymentservice.exception.PaymentNotFoundException;
import org.de013.paymentservice.exception.PaymentProcessingException;
import org.de013.paymentservice.exception.RefundNotFoundException;
import org.de013.paymentservice.gateway.PaymentGatewayFactory;
import org.de013.paymentservice.gateway.stripe.StripePaymentGateway;
import org.de013.paymentservice.mapper.RefundMapper;
import org.de013.paymentservice.repository.PaymentRepository;
import org.de013.paymentservice.repository.RefundRepository;
import org.de013.paymentservice.util.PaymentNumberGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of RefundService for refund processing operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final RefundMapper refundMapper;

    // ========== REFUND PROCESSING ==========

    @Override
    public RefundResponse createRefund(RefundRequest request) {
        log.info("Creating refund for payment: {}, amount: {}", request.getPaymentId(), request.getAmount());

        try {
            // Validate refund request
            validateRefundRequest(request);

            // Get payment
            Payment payment = paymentRepository.findById(request.getPaymentId())
                    .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + request.getPaymentId()));

            // Validate refund amount
            if (!isValidRefundAmount(request.getPaymentId(), request.getAmount())) {
                throw new PaymentProcessingException("Invalid refund amount");
            }

            // Create refund entity
            Refund refund = createRefundEntity(request, payment);
            Refund savedRefund = refundRepository.save(refund);

            // Process refund with Stripe
            StripePaymentGateway stripeGateway = gatewayFactory.getStripeGateway();
            StripeRefundRequest stripeRequest = mapToStripeRefundRequest(request, payment);
            StripeRefundResponse stripeResponse = stripeGateway.createRefund(stripeRequest);

            // Update refund with Stripe response
            updateRefundFromStripeResponse(savedRefund, stripeResponse);
            Refund finalRefund = refundRepository.save(savedRefund);

            // Update payment refund amounts
            updatePaymentRefundAmounts(payment);

            log.info("Refund created successfully: {}", finalRefund.getRefundNumber());
            return refundMapper.toRefundResponse(finalRefund);

        } catch (Exception e) {
            log.error("Failed to create refund for payment: {}", request.getPaymentId(), e);
            throw new PaymentProcessingException("Failed to create refund: " + e.getMessage(), e);
        }
    }

    @Override
    public RefundResponse processRefund(Long refundId) {
        log.info("Processing refund: {}", refundId);

        Refund refund = getRefundEntityById(refundId)
                .orElseThrow(() -> new RefundNotFoundException("Refund not found: " + refundId));

        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new PaymentProcessingException("Refund is not in pending status: " + refund.getStatus());
        }

        try {
            // Process with Stripe if not already processed
            if (refund.getStripeRefundId() == null) {
                StripePaymentGateway stripeGateway = gatewayFactory.getStripeGateway();
                Payment payment = paymentRepository.findById(refund.getPaymentId())
                        .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + refund.getPaymentId()));

                StripeRefundRequest stripeRequest = StripeRefundRequest.builder()
                        .paymentIntentId(payment.getStripePaymentIntentId())
                        .amount(refund.getAmount())
                        .reason(refund.getReason())
                        .build();

                StripeRefundResponse stripeResponse = stripeGateway.createRefund(stripeRequest);
                updateRefundFromStripeResponse(refund, stripeResponse);
            }

            refund.setStatus(RefundStatus.PROCESSING);
            Refund processedRefund = refundRepository.save(refund);

            log.info("Refund processed successfully: {}", processedRefund.getRefundNumber());
            return refundMapper.toRefundResponse(processedRefund);

        } catch (Exception e) {
            log.error("Failed to process refund: {}", refundId, e);
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason(e.getMessage());
            refundRepository.save(refund);
            throw new PaymentProcessingException("Failed to process refund: " + e.getMessage(), e);
        }
    }

    @Override
    public RefundResponse cancelRefund(Long refundId, String reason) {
        log.info("Canceling refund: {} with reason: {}", refundId, reason);

        Refund refund = getRefundEntityById(refundId)
                .orElseThrow(() -> new RefundNotFoundException("Refund not found: " + refundId));

        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new PaymentProcessingException("Only pending refunds can be canceled");
        }

        refund.setStatus(RefundStatus.CANCELED);
        refund.setFailureReason(reason);
        Refund canceledRefund = refundRepository.save(refund);

        log.info("Refund canceled successfully: {}", canceledRefund.getRefundNumber());
        return refundMapper.toRefundResponse(canceledRefund);
    }

    @Override
    public RefundResponse approveRefund(Long refundId, String approvedBy) {
        log.info("Approving refund: {} by: {}", refundId, approvedBy);

        Refund refund = getRefundEntityById(refundId)
                .orElseThrow(() -> new RefundNotFoundException("Refund not found: " + refundId));

        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new PaymentProcessingException("Only pending refunds can be approved");
        }

        refund.approve(approvedBy);
        refundRepository.save(refund);

        // Process the approved refund
        return processRefund(refundId);
    }

    @Override
    public RefundResponse rejectRefund(Long refundId, String rejectedBy, String reason) {
        log.info("Rejecting refund: {} by: {} with reason: {}", refundId, rejectedBy, reason);

        Refund refund = getRefundEntityById(refundId)
                .orElseThrow(() -> new RefundNotFoundException("Refund not found: " + refundId));

        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new PaymentProcessingException("Only pending refunds can be rejected");
        }

        refund.setStatus(RefundStatus.REJECTED);
        refund.setFailureReason(reason);
        Refund rejectedRefund = refundRepository.save(refund);

        log.info("Refund rejected successfully: {}", rejectedRefund.getRefundNumber());
        return refundMapper.toRefundResponse(rejectedRefund);
    }

    // ========== REFUND RETRIEVAL ==========

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundResponse> getRefundById(Long refundId) {
        return refundRepository.findById(refundId)
                .map(refundMapper::toRefundResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundResponse> getRefundByNumber(String refundNumber) {
        return refundRepository.findByRefundNumber(refundNumber)
                .map(refundMapper::toRefundResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefundResponse> getRefundByStripeRefundId(String stripeRefundId) {
        return refundRepository.findByStripeRefundId(stripeRefundId)
                .map(refundMapper::toRefundResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByPaymentId(Long paymentId) {
        return refundRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId).stream()
                .map(refundMapper::toRefundResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByOrderId(Long orderId) {
        return refundRepository.findByOrderId(orderId).stream()
                .map(refundMapper::toRefundResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RefundResponse> getRefundsByPaymentId(Long paymentId, Pageable pageable) {
        return refundRepository.findByPaymentId(paymentId, pageable)
                .map(refundMapper::toRefundResponse);
    }

    // ========== REFUND STATUS ==========

    @Override
    public RefundResponse updateRefundStatus(Long refundId, RefundStatus status, String reason) {
        Refund refund = getRefundEntityById(refundId)
                .orElseThrow(() -> new RefundNotFoundException("Refund not found: " + refundId));

        refund.setStatus(status);
        if (reason != null) {
            refund.setFailureReason(reason);
        }
        Refund updatedRefund = refundRepository.save(refund);

        log.info("Refund status updated: {} -> {}", refundId, status);
        return refundMapper.toRefundResponse(updatedRefund);
    }

    @Override
    public RefundResponse syncRefundStatusWithStripe(Long refundId) {
        Refund refund = getRefundEntityById(refundId)
                .orElseThrow(() -> new RefundNotFoundException("Refund not found: " + refundId));

        if (refund.getStripeRefundId() == null) {
            throw new PaymentProcessingException("Refund does not have Stripe refund ID");
        }

        try {
            StripePaymentGateway stripeGateway = gatewayFactory.getStripeGateway();
            StripeRefundResponse stripeResponse = stripeGateway.getRefundResponse(refund.getStripeRefundId());

            // Update refund with latest Stripe status
            updateRefundFromStripeResponse(refund, stripeResponse);
            Refund syncedRefund = refundRepository.save(refund);

            log.info("Refund status synced with Stripe: {}", syncedRefund.getRefundNumber());
            return refundMapper.toRefundResponse(syncedRefund);

        } catch (Exception e) {
            log.error("Failed to sync refund status with Stripe: {}", refundId, e);
            throw new PaymentProcessingException("Failed to sync refund status: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByStatus(RefundStatus status) {
        return refundRepository.findByStatus(status).stream()
                .map(refundMapper::toRefundResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getPendingRefunds() {
        return refundRepository.findPendingRefunds().stream()
                .map(refundMapper::toRefundResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsRequiringApproval() {
        return refundRepository.findRefundsPendingApproval().stream()
                .map(refundMapper::toRefundResponse)
                .toList();
    }

    // ========== REFUND VALIDATION ==========

    @Override
    public void validateRefundRequest(RefundRequest request) {
        if (request == null) {
            throw new PaymentProcessingException("Refund request cannot be null");
        }

        if (request.getPaymentId() == null) {
            throw new PaymentProcessingException("Payment ID is required");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException("Refund amount must be greater than zero");
        }

        if (!canRefundPayment(request.getPaymentId())) {
            throw new PaymentProcessingException("Payment cannot be refunded");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRefundPayment(Long paymentId) {
        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            return false;
        }

        Payment payment = paymentOpt.get();
        return payment.getStatus() == org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED &&
               !payment.isFullyRefunded();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isValidRefundAmount(Long paymentId, BigDecimal refundAmount) {
        BigDecimal maxRefundable = getMaxRefundableAmount(paymentId);
        return refundAmount.compareTo(maxRefundable) <= 0;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getMaxRefundableAmount(Long paymentId) {
        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Payment payment = paymentOpt.get();
        BigDecimal totalRefunded = getTotalRefundedAmount(paymentId);
        return payment.getAmount().subtract(totalRefunded);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalRefundedAmount(Long paymentId) {
        return refundRepository.sumSuccessfulRefundsByPaymentId(paymentId);
    }

    // ========== REFUND SEARCH ==========

    @Override
    @Transactional(readOnly = true)
    public Page<RefundResponse> searchRefunds(
            String refundNumber, Long paymentId, Long orderId, RefundStatus status,
            String refundType, BigDecimal minAmount, BigDecimal maxAmount,
            LocalDateTime startDate, LocalDateTime endDate, String initiatedBy, Pageable pageable) {

        return refundRepository.searchRefunds(
                refundNumber, paymentId, orderId, status, refundType, minAmount, maxAmount,
                startDate, endDate, initiatedBy, pageable)
                .map(refundMapper::toRefundResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getSuccessfulRefundsByPaymentId(Long paymentId) {
        return refundRepository.findSuccessfulRefundsByPaymentId(paymentId).stream()
                .map(refundMapper::toRefundResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getFailedRefundsByPaymentId(Long paymentId) {
        return refundRepository.findFailedRefundsByPaymentId(paymentId).stream()
                .map(refundMapper::toRefundResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return refundRepository.findByCreatedAtBetween(startDate, endDate).stream()
                .map(refundMapper::toRefundResponse)
                .toList();
    }

    // ========== REFUND STATISTICS ==========

    @Override
    @Transactional(readOnly = true)
    public RefundStatistics getRefundStatisticsByPaymentId(Long paymentId) {
        Object[] stats = refundRepository.getRefundStatisticsByPaymentId(paymentId);

        Long totalRefunds = (Long) stats[0];
        Long successfulRefunds = (Long) stats[1];
        Long failedRefunds = (Long) stats[2];
        Long pendingRefunds = (Long) stats[3];
        Long fullRefunds = (Long) stats[4];
        Long partialRefunds = (Long) stats[5];
        BigDecimal totalRefundedAmount = (BigDecimal) stats[6];

        BigDecimal averageRefundAmount = successfulRefunds > 0 ?
                totalRefundedAmount.divide(BigDecimal.valueOf(successfulRefunds)) : BigDecimal.ZERO;

        // Get first and last refund dates
        List<Refund> paymentRefunds = refundRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);
        LocalDateTime firstRefundDate = paymentRefunds.stream()
                .map(Refund::getCreatedAt)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        LocalDateTime lastRefundDate = paymentRefunds.stream()
                .map(Refund::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new RefundService.RefundStatistics(
                totalRefunds,
                successfulRefunds,
                failedRefunds,
                pendingRefunds,
                fullRefunds,
                partialRefunds,
                totalRefundedAmount,
                averageRefundAmount,
                firstRefundDate,
                lastRefundDate
        );
    }

    @Override
    @Transactional(readOnly = true)
    public RefundStatistics getRefundStatisticsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        List<Refund> refunds = refundRepository.findByCreatedAtBetween(startDate, endDate);

        Long totalRefunds = (long) refunds.size();
        Long successfulRefunds = refunds.stream()
                .mapToLong(r -> r.getStatus() == RefundStatus.SUCCEEDED ? 1 : 0)
                .sum();
        Long failedRefunds = refunds.stream()
                .mapToLong(r -> r.getStatus() == RefundStatus.FAILED ? 1 : 0)
                .sum();
        Long pendingRefunds = refunds.stream()
                .mapToLong(r -> r.getStatus() == RefundStatus.PENDING ? 1 : 0)
                .sum();
        Long fullRefunds = refunds.stream()
                .mapToLong(r -> "FULL".equals(r.getRefundType()) ? 1 : 0)
                .sum();
        Long partialRefunds = refunds.stream()
                .mapToLong(r -> "PARTIAL".equals(r.getRefundType()) ? 1 : 0)
                .sum();

        BigDecimal totalRefundedAmount = refunds.stream()
                .filter(r -> r.getStatus() == RefundStatus.SUCCEEDED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageRefundAmount = successfulRefunds > 0 ?
                totalRefundedAmount.divide(BigDecimal.valueOf(successfulRefunds)) : BigDecimal.ZERO;

        return new RefundService.RefundStatistics(
                totalRefunds,
                successfulRefunds,
                failedRefunds,
                pendingRefunds,
                fullRefunds,
                partialRefunds,
                totalRefundedAmount,
                averageRefundAmount,
                startDate,
                endDate
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Long getRefundCountByStatus(RefundStatus status) {
        return refundRepository.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalRefundedAmountByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return refundRepository.sumSuccessfulRefundsByDateRange(startDate, endDate);
    }

    // ========== UTILITY METHODS ==========

    @Override
    public String generateRefundNumber() {
        String refundNumber;
        do {
            refundNumber = PaymentNumberGenerator.generateRefundNumber();
        } while (refundNumberExists(refundNumber));

        return refundNumber;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean refundNumberExists(String refundNumber) {
        return refundRepository.existsByRefundNumber(refundNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Refund> getRefundEntityById(Long refundId) {
        return refundRepository.findById(refundId);
    }

    @Override
    public Refund saveRefundEntity(Refund refund) {
        return refundRepository.save(refund);
    }

    // ========== CLEANUP OPERATIONS ==========

    @Override
    public void cleanupOldFailedRefunds(LocalDateTime cutoffDate) {
        List<Refund> oldFailedRefunds = refundRepository.findOldFailedRefunds(cutoffDate);

        for (Refund refund : oldFailedRefunds) {
            try {
                refundRepository.delete(refund);
                log.info("Cleaned up old failed refund: {}", refund.getRefundNumber());
            } catch (Exception e) {
                log.warn("Failed to cleanup failed refund: {}", refund.getId(), e);
            }
        }
    }

    @Override
    public void cleanupOldCanceledRefunds(LocalDateTime cutoffDate) {
        List<Refund> oldCanceledRefunds = refundRepository.findOldCanceledRefunds(cutoffDate);

        for (Refund refund : oldCanceledRefunds) {
            try {
                refundRepository.delete(refund);
                log.info("Cleaned up old canceled refund: {}", refund.getRefundNumber());
            } catch (Exception e) {
                log.warn("Failed to cleanup canceled refund: {}", refund.getId(), e);
            }
        }
    }

    // ========== HELPER METHODS ==========

    private Refund createRefundEntity(RefundRequest request, Payment payment) {
        String refundType = request.getAmount().compareTo(payment.getAmount()) == 0 ? "FULL" : "PARTIAL";

        return Refund.builder()
                .refundNumber(generateRefundNumber())
                .payment(payment)
                .orderId(payment.getOrderId())
                .amount(request.getAmount())
                .currency(payment.getCurrency().name())
                .status(RefundStatus.PENDING)
                .refundType(refundType)
                .reason(request.getReason())
                .description(request.getDescription())
                .stripePaymentIntentId(payment.getStripePaymentIntentId())
                .initiatedBy(request.getInitiatedBy() != null ? request.getInitiatedBy() : "SYSTEM")
                .createdBy("SYSTEM")
                .updatedBy("SYSTEM")
                .build();
    }

    private StripeRefundRequest mapToStripeRefundRequest(RefundRequest request, Payment payment) {
        return StripeRefundRequest.builder()
                .paymentIntentId(payment.getStripePaymentIntentId())
                .amount(request.getAmount())
                .reason(request.getReason())
                .description(request.getDescription())
                .build();
    }

    private void updateRefundFromStripeResponse(Refund refund, StripeRefundResponse stripeResponse) {
        refund.setStripeRefundId(stripeResponse.getRefundId());
        refund.setStripeChargeId(stripeResponse.getChargeId());

        // Map Stripe status to internal status
        switch (stripeResponse.getStatus().toLowerCase()) {
            case "succeeded" -> refund.setStatus(RefundStatus.SUCCEEDED);
            case "pending" -> refund.setStatus(RefundStatus.PROCESSING);
            case "failed" -> {
                refund.setStatus(RefundStatus.FAILED);
                refund.setFailureReason(stripeResponse.getFailureReason());
            }
            case "canceled" -> refund.setStatus(RefundStatus.CANCELED);
            default -> refund.setStatus(RefundStatus.PROCESSING);
        }

        if (stripeResponse.getProcessedAt() != null) {
            refund.setProcessedAt(stripeResponse.getProcessedAt());
        }

        if (stripeResponse.getExpectedArrivalDate() != null) {
            refund.setExpectedArrivalDate(stripeResponse.getExpectedArrivalDate());
        }
    }

    private void updatePaymentRefundAmounts(Payment payment) {
        // Payment entity has computed properties for refund amounts
        // No need to set them manually - they are calculated from refunds list
        // Just save the payment to trigger any necessary updates
        paymentRepository.save(payment);
    }
}
