package org.de013.paymentservice.exception;

import lombok.extern.slf4j.Slf4j;
// Local payment service exceptions
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.PaymentMethod;
import org.de013.paymentservice.entity.Refund;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.entity.enums.RefundStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Utility class for creating payment-related exceptions with consistent messaging.
 */
@Slf4j
@Component
public class PaymentExceptionUtils {

    // Payment-related exception factories
    
    public PaymentNotFoundException paymentNotFound(Long paymentId) {
        return PaymentNotFoundException.forId(paymentId);
    }
    
    public PaymentNotFoundException paymentNotFound(String paymentNumber) {
        return PaymentNotFoundException.forNumber(paymentNumber);
    }
    
    public PaymentProcessingException paymentProcessingFailed(String paymentId, String operation, String reason) {
        return new PaymentProcessingException(
            String.format("Payment %s failed for payment %s: %s", operation, paymentId, reason),
            paymentId,
            operation
        );
    }
    
    public PaymentProcessingException paymentProcessingFailed(String paymentId, String operation, String reason, Throwable cause) {
        return new PaymentProcessingException(
            String.format("Payment %s failed for payment %s: %s", operation, paymentId, reason),
            paymentId,
            operation,
            cause
        );
    }
    
    public PaymentProcessingException invalidPaymentStatus(Payment payment, PaymentStatus requiredStatus) {
        return new PaymentProcessingException(
            String.format("Invalid payment status. Current: %s, Required: %s",
                payment.getStatus(), requiredStatus),
            payment.getStripePaymentIntentId(),
            "STATUS_CHECK"
        );
    }
    
    // Payment Method-related exception factories
    
    public InvalidPaymentMethodException paymentMethodNotFound(Long paymentMethodId) {
        return InvalidPaymentMethodException.notFound(paymentMethodId.toString());
    }
    
    public InvalidPaymentMethodException paymentMethodExpired(PaymentMethod paymentMethod) {
        return InvalidPaymentMethodException.expired(paymentMethod.getId().toString());
    }
    
    public InvalidPaymentMethodException paymentMethodInactive(PaymentMethod paymentMethod) {
        return InvalidPaymentMethodException.inactive(paymentMethod.getId().toString());
    }
    
    public InvalidPaymentMethodException paymentMethodValidationFailed(Long paymentMethodId, String details) {
        return InvalidPaymentMethodException.validationFailed(paymentMethodId.toString(), details);
    }
    
    // Refund-related exception factories
    
    public RefundException refundNotFound(Long refundId) {
        return RefundException.notFound(refundId.toString());
    }
    
    public RefundException refundProcessingFailed(Refund refund, String reason) {
        return RefundException.processingFailed(
            refund.getId().toString(),
            refund.getPayment().getStripePaymentIntentId(),
            reason
        );
    }
    
    public RefundException refundInvalidStatus(Refund refund, RefundStatus requiredStatus) {
        return RefundException.invalidStatus(
            refund.getId().toString(),
            refund.getStatus().toString(),
            requiredStatus.toString()
        );
    }
    
    public RefundException refundAmountExceeded(String refundId, String paymentId, 
                                              BigDecimal requestedAmount, BigDecimal maxAmount, String currency) {
        return RefundException.amountExceeded(refundId, paymentId, requestedAmount, maxAmount, currency);
    }
    
    public RefundException paymentNotRefundable(Payment payment) {
        return RefundException.paymentNotRefundable(
            payment.getStripePaymentIntentId(),
            payment.getStatus().toString()
        );
    }
    
    // Insufficient Funds exception factories
    
    public InsufficientFundsException insufficientFundsForPayment(String paymentId, 
                                                                BigDecimal requestedAmount, 
                                                                BigDecimal availableAmount, 
                                                                String currency) {
        return InsufficientFundsException.forPayment(paymentId, requestedAmount, availableAmount, currency);
    }
    
    public InsufficientFundsException insufficientFundsForRefund(String paymentId, 
                                                               BigDecimal requestedAmount, 
                                                               BigDecimal availableAmount, 
                                                               String currency) {
        return InsufficientFundsException.forRefund(paymentId, requestedAmount, availableAmount, currency);
    }
    
    // Validation helpers
    
    public void validatePaymentExists(Payment payment, Long paymentId) {
        if (payment == null) {
            throw paymentNotFound(paymentId);
        }
    }
    
    public void validatePaymentMethodExists(PaymentMethod paymentMethod, Long paymentMethodId) {
        if (paymentMethod == null) {
            throw paymentMethodNotFound(paymentMethodId);
        }
    }
    
    public void validateRefundExists(Refund refund, Long refundId) {
        if (refund == null) {
            throw refundNotFound(refundId);
        }
    }
    
    public void validatePaymentStatus(Payment payment, PaymentStatus requiredStatus) {
        if (payment.getStatus() != requiredStatus) {
            throw invalidPaymentStatus(payment, requiredStatus);
        }
    }
    
    public void validateRefundStatus(Refund refund, RefundStatus requiredStatus) {
        if (refund.getStatus() != requiredStatus) {
            throw refundInvalidStatus(refund, requiredStatus);
        }
    }
    
    public void validatePaymentMethodActive(PaymentMethod paymentMethod) {
        if (!paymentMethod.getIsActive()) {
            throw paymentMethodInactive(paymentMethod);
        }
    }
    
    public void validatePaymentMethodNotExpired(PaymentMethod paymentMethod) {
        if (paymentMethod.isExpired()) {
            throw paymentMethodExpired(paymentMethod);
        }
    }
    
    public void validateRefundAmount(BigDecimal requestedAmount, BigDecimal maxAmount, 
                                   String refundId, String paymentId, String currency) {
        if (requestedAmount.compareTo(maxAmount) > 0) {
            throw refundAmountExceeded(refundId, paymentId, requestedAmount, maxAmount, currency);
        }
    }
    
    public void validatePaymentRefundable(Payment payment) {
        if (!isPaymentRefundable(payment)) {
            throw paymentNotRefundable(payment);
        }
    }
    
    // Helper methods
    
    private boolean isPaymentRefundable(Payment payment) {
        return payment.getStatus() == PaymentStatus.SUCCEEDED;
    }
    
    /**
     * Logs exception details for debugging purposes.
     */
    public void logException(Exception ex, String context) {
        if (ex instanceof PaymentProcessingException) {
            PaymentProcessingException ppe = (PaymentProcessingException) ex;
            log.error("Payment processing exception in {}: PaymentId={}, Operation={}, Message={}", 
                    context, ppe.getPaymentId(), ppe.getOperation(), ppe.getMessage(), ex);
        } else if (ex instanceof RefundException) {
            RefundException re = (RefundException) ex;
            log.error("Refund exception in {}: RefundId={}, PaymentId={}, Operation={}, Message={}", 
                    context, re.getRefundId(), re.getPaymentId(), re.getOperation(), re.getMessage(), ex);
        } else if (ex instanceof StripeException) {
            StripeException se = (StripeException) ex;
            log.error("Stripe exception in {}: ErrorCode={}, ErrorType={}, Message={}", 
                    context, se.getStripeErrorCode(), se.getStripeErrorType(), se.getMessage(), ex);
        } else {
            log.error("Exception in {}: {}", context, ex.getMessage(), ex);
        }
    }
}
