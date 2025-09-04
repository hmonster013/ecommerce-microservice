package org.de013.paymentservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Validator implementation for @ValidAmount annotation.
 */
@Slf4j
@Component
public class AmountValidator implements ConstraintValidator<ValidAmount, BigDecimal> {
    
    private ValidAmount annotation;
    
    @Override
    public void initialize(ValidAmount constraintAnnotation) {
        this.annotation = constraintAnnotation;
    }
    
    @Override
    public boolean isValid(BigDecimal amount, ConstraintValidatorContext context) {
        // Allow null if specified
        if (amount == null) {
            return annotation.allowNull();
        }
        
        try {
            // Check if amount is negative
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                addConstraintViolation(context, "Amount cannot be negative");
                return false;
            }
            
            // Check if zero is allowed
            if (amount.compareTo(BigDecimal.ZERO) == 0 && !annotation.allowZero()) {
                addConstraintViolation(context, "Amount cannot be zero");
                return false;
            }
            
            // Check decimal places
            if (amount.scale() > annotation.maxDecimalPlaces()) {
                addConstraintViolation(context, 
                    String.format("Amount cannot have more than %d decimal places", annotation.maxDecimalPlaces()));
                return false;
            }
            
            // Convert to cents for min/max validation
            BigDecimal amountInCents = amount.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.HALF_UP);
            
            // Check minimum amount
            if (amountInCents.longValue() < annotation.min()) {
                BigDecimal minAmount = new BigDecimal(annotation.min()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                addConstraintViolation(context, 
                    String.format("Amount must be at least %s %s", minAmount, annotation.currency()));
                return false;
            }
            
            // Check maximum amount
            if (amountInCents.longValue() > annotation.max()) {
                BigDecimal maxAmount = new BigDecimal(annotation.max()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                addConstraintViolation(context, 
                    String.format("Amount cannot exceed %s %s", maxAmount, annotation.currency()));
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error validating amount: {}", amount, e);
            addConstraintViolation(context, "Error validating amount");
            return false;
        }
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
    }
}
