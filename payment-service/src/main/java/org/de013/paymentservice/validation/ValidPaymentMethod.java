package org.de013.paymentservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for payment method validation.
 * Validates that a payment method ID exists, is active, and not expired.
 */
@Documented
@Constraint(validatedBy = PaymentMethodValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPaymentMethod {
    
    String message() default "Invalid payment method";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Whether to check if the payment method is active.
     */
    boolean checkActive() default true;
    
    /**
     * Whether to check if the payment method is not expired.
     */
    boolean checkExpired() default true;
    
    /**
     * Whether to allow null values.
     */
    boolean allowNull() default false;
}
