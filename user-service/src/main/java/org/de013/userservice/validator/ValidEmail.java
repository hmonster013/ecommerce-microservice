package org.de013.userservice.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for email validation
 *
 * <p>Validates email addresses according to RFC 5321 and RFC 1035 standards.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Email format validation (regex pattern)</li>
 *   <li>Length restrictions (RFC 5321)</li>
 *   <li>Local part and domain validation</li>
 *   <li>Disposable email domain detection (configurable)</li>
 *   <li>Blocked domain filtering</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * public class UserDto {
 *     @ValidEmail(allowDisposable = false)
 *     private String email;
 * }
 * }
 * </pre>
 *
 * @see EmailValidator
 */
@Documented
@Constraint(validatedBy = EmailValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {

    /**
     * The error message template
     * @return the message template
     */
    String message() default "{email.format}";

    /**
     * Validation groups
     * @return the groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload for clients
     * @return the payload
     */
    Class<? extends Payload>[] payload() default {};

    /**
     * Whether to allow disposable email domains (e.g., temporary email services)
     *
     * <p>When set to {@code false}, emails from disposable domains like
     * mailinator.com, tempmail.org, etc. will be rejected.</p>
     *
     * @return true to allow disposable domains, false to reject them
     */
    boolean allowDisposable() default true;
}
