package org.de013.paymentservice.mapper;

import org.de013.paymentservice.dto.payment.PaymentResponse;
import org.de013.paymentservice.dto.payment.PaymentStatusResponse;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.PaymentTransaction;
import org.de013.paymentservice.entity.Refund;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper for converting Payment entities to DTOs
 */
@Component
public class PaymentMapper {

    /**
     * Convert Payment entity to PaymentResponse DTO
     */
    public PaymentResponse toPaymentResponse(Payment payment) {
        if (payment == null) {
            return null;
        }

        PaymentResponse.PaymentResponseBuilder builder = PaymentResponse.builder()
                .id(payment.getId())
                .paymentNumber(payment.getPaymentNumber())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .method(payment.getMethod())
                .stripePaymentIntentId(payment.getStripePaymentIntentId())
                .stripeCustomerId(payment.getStripeCustomerId())
                .description(payment.getDescription())
                .failureReason(payment.getFailureReason())
                .receiptEmail(payment.getReceiptEmail())
                .totalRefundedAmount(payment.getTotalRefundedAmount())
                .isFullyRefunded(payment.isFullyRefunded())
                .isPartiallyRefunded(payment.isPartiallyRefunded())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt());

        // Map payment method info
        if (payment.getStripePaymentMethodId() != null) {
            // TODO: Get payment method details from PaymentMethodService
            builder.paymentMethodInfo(PaymentResponse.PaymentMethodInfo.builder()
                    .type(payment.getMethod().name())
                    .build());
        }

        // Map transactions
        if (payment.getTransactions() != null && !payment.getTransactions().isEmpty()) {
            List<PaymentResponse.PaymentTransactionResponse> transactions = payment.getTransactions().stream()
                    .map(this::toPaymentTransactionResponse)
                    .toList();
            builder.transactions(transactions);
        }

        // Map refunds
        if (payment.getRefunds() != null && !payment.getRefunds().isEmpty()) {
            List<PaymentResponse.RefundSummary> refunds = payment.getRefunds().stream()
                    .map(this::toRefundSummary)
                    .toList();
            builder.refunds(refunds);
        }

        return builder.build();
    }

    /**
     * Convert Payment entity to PaymentStatusResponse DTO
     */
    public PaymentStatusResponse toPaymentStatusResponse(Payment payment) {
        if (payment == null) {
            return null;
        }

        PaymentStatusResponse.PaymentStatusResponseBuilder builder = PaymentStatusResponse.builder()
                .paymentId(payment.getId())
                .paymentNumber(payment.getPaymentNumber())
                .status(payment.getStatus())
                .statusDisplayName(payment.getStatus().name())
                .amount(payment.getAmount())
                .currency(payment.getCurrency().name())
                .stripePaymentIntentId(payment.getStripePaymentIntentId())
                .failureReason(payment.getFailureReason())
                .statusUpdatedAt(payment.getUpdatedAt())
                .createdAt(payment.getCreatedAt());

        // Set status flags
        builder.requiresAction(payment.getStatus() == org.de013.paymentservice.entity.enums.PaymentStatus.REQUIRES_ACTION ||
                              payment.getStatus() == org.de013.paymentservice.entity.enums.PaymentStatus.REQUIRES_CONFIRMATION)
                .isProcessing(payment.getStatus() == org.de013.paymentservice.entity.enums.PaymentStatus.PENDING ||
                             payment.getStatus() == org.de013.paymentservice.entity.enums.PaymentStatus.PROCESSING)
                .isCompleted(payment.getStatus() == org.de013.paymentservice.entity.enums.PaymentStatus.SUCCEEDED)
                .isFailed(payment.getStatus() == org.de013.paymentservice.entity.enums.PaymentStatus.FAILED)
                .isCanceled(payment.getStatus() == org.de013.paymentservice.entity.enums.PaymentStatus.CANCELED);

        // Get latest transaction info
        if (payment.getTransactions() != null && !payment.getTransactions().isEmpty()) {
            PaymentTransaction latestTransaction = payment.getTransactions().stream()
                    .max((t1, t2) -> t1.getCreatedAt().compareTo(t2.getCreatedAt()))
                    .orElse(null);
            
            if (latestTransaction != null) {
                builder.lastTransactionType(latestTransaction.getType().name())
                        .lastTransactionStatus(latestTransaction.getStatus().name());
            }
        }

        return builder.build();
    }

    /**
     * Convert PaymentTransaction to PaymentTransactionResponse
     */
    private PaymentResponse.PaymentTransactionResponse toPaymentTransactionResponse(PaymentTransaction transaction) {
        return PaymentResponse.PaymentTransactionResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .status(transaction.getStatus().name())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    /**
     * Convert Refund to RefundSummary
     */
    private PaymentResponse.RefundSummary toRefundSummary(Refund refund) {
        return PaymentResponse.RefundSummary.builder()
                .id(refund.getId())
                .refundNumber(refund.getRefundNumber())
                .amount(refund.getAmount())
                .status(refund.getStatus().name())
                .reason(refund.getReason())
                .createdAt(refund.getCreatedAt())
                .build();
    }

    /**
     * Convert list of Payment entities to PaymentResponse DTOs
     */
    public List<PaymentResponse> toPaymentResponseList(List<Payment> payments) {
        if (payments == null || payments.isEmpty()) {
            return List.of();
        }

        return payments.stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    /**
     * Convert list of Payment entities to PaymentStatusResponse DTOs
     */
    public List<PaymentStatusResponse> toPaymentStatusResponseList(List<Payment> payments) {
        if (payments == null || payments.isEmpty()) {
            return List.of();
        }

        return payments.stream()
                .map(this::toPaymentStatusResponse)
                .toList();
    }
}
