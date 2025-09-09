package org.de013.userservice.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Field;

/**
 * Password confirmation validator implementation
 * Validates that password and confirmPassword fields match
 */
public class PasswordConfirmationValidator implements ConstraintValidator<ValidPasswordConfirmation, Object> {

    private String passwordField;
    private String confirmPasswordField;

    @Override
    public void initialize(ValidPasswordConfirmation constraintAnnotation) {
        this.passwordField = constraintAnnotation.passwordField();
        this.confirmPasswordField = constraintAnnotation.confirmPasswordField();
    }

    @Override
    public boolean isValid(Object obj, ConstraintValidatorContext context) {
        if (obj == null) {
            return true;
        }

        try {
            String password = getFieldValue(obj, passwordField);
            String confirmPassword = getFieldValue(obj, confirmPasswordField);

            // Both fields must be present for validation
            if (password == null && confirmPassword == null) {
                return true;
            }

            // If one is null and the other is not, they don't match
            if (password == null || confirmPassword == null) {
                addConstraintViolation(context);
                return false;
            }

            // Check if passwords match
            boolean isValid = password.equals(confirmPassword);
            
            if (!isValid) {
                addConstraintViolation(context);
            }

            return isValid;

        } catch (Exception e) {
            // If we can't access the fields, consider it invalid
            return false;
        }
    }

    private String getFieldValue(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(obj);
        return value != null ? value.toString() : null;
    }

    private void addConstraintViolation(ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(confirmPasswordField)
                .addConstraintViolation();
    }
}
