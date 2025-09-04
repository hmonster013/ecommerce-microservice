package org.de013.paymentservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.entity.PaymentMethod;
import org.de013.paymentservice.repository.PaymentMethodRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Validator implementation for @ValidPaymentMethod annotation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentMethodValidator implements ConstraintValidator<ValidPaymentMethod, Long> {
    
    private final PaymentMethodRepository paymentMethodRepository;
    
    private ValidPaymentMethod annotation;
    
    @Override
    public void initialize(ValidPaymentMethod constraintAnnotation) {
        this.annotation = constraintAnnotation;
    }
    
    @Override
    public boolean isValid(Long paymentMethodId, ConstraintValidatorContext context) {
        // Allow null if specified
        if (paymentMethodId == null) {
            return annotation.allowNull();
        }
        
        try {
            Optional<PaymentMethod> paymentMethodOpt = paymentMethodRepository.findById(paymentMethodId);
            
            if (paymentMethodOpt.isEmpty()) {
                addConstraintViolation(context, "Payment method not found with ID: " + paymentMethodId);
                return false;
            }
            
            PaymentMethod paymentMethod = paymentMethodOpt.get();
            
            // Check if payment method is active
            if (annotation.checkActive() && !paymentMethod.getIsActive()) {
                addConstraintViolation(context, "Payment method is inactive");
                return false;
            }
            
            // Check if payment method is not expired
            if (annotation.checkExpired() && paymentMethod.isExpired()) {
                addConstraintViolation(context, "Payment method has expired");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error validating payment method ID: {}", paymentMethodId, e);
            addConstraintViolation(context, "Error validating payment method");
            return false;
        }
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
    }
}
