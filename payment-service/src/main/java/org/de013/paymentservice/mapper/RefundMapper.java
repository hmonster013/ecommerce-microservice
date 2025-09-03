package org.de013.paymentservice.mapper;

import org.de013.paymentservice.dto.refund.RefundResponse;
import org.de013.paymentservice.entity.Refund;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapper for converting Refund entities to DTOs
 */
@Component
public class RefundMapper {

    /**
     * Convert Refund entity to RefundResponse DTO
     */
    public RefundResponse toRefundResponse(Refund refund) {
        if (refund == null) {
            return null;
        }

        RefundResponse.RefundResponseBuilder builder = RefundResponse.builder()
                .id(refund.getId())
                .refundNumber(refund.getRefundNumber())
                .paymentId(refund.getPaymentId())
                .orderId(refund.getOrderId())
                .amount(refund.getAmount())
                .currency(refund.getCurrency())
                .status(refund.getStatus())
                .refundType(refund.getRefundType())
                .reason(refund.getReason())
                .description(refund.getDescription())
                .stripeRefundId(refund.getStripeRefundId())
                .stripeChargeId(refund.getStripeChargeId())
                .stripePaymentIntentId(refund.getStripePaymentIntentId())
                .processingFeeRefunded(refund.getProcessingFeeRefunded())
                .netRefundAmount(refund.getNetRefundAmount())
                .receiptNumber(refund.getReceiptNumber())
                .initiatedBy(refund.getInitiatedBy())
                .approvedBy(refund.getApprovedBy())
                .approvedAt(refund.getApprovedAt())
                .processedAt(refund.getProcessedAt())
                .settledAt(refund.getSettledAt())
                .expectedArrivalDate(refund.getExpectedArrivalDate())
                .createdAt(refund.getCreatedAt())
                .updatedAt(refund.getUpdatedAt());

        // No status flags needed - RefundResponse doesn't have them

        return builder.build();
    }

    /**
     * Convert list of Refund entities to RefundResponse DTOs
     */
    public List<RefundResponse> toRefundResponseList(List<Refund> refunds) {
        if (refunds == null || refunds.isEmpty()) {
            return List.of();
        }

        return refunds.stream()
                .map(this::toRefundResponse)
                .toList();
    }
}
