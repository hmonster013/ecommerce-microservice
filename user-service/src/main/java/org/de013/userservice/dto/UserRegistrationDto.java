package org.de013.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.de013.userservice.validator.ValidEmail;
import org.de013.userservice.validator.ValidPassword;
import org.de013.userservice.validator.ValidPhone;
import org.de013.userservice.validator.ValidName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User registration request")
public class UserRegistrationDto {

    @NotBlank(message = "{username.required}")
    @Size(min = 3, max = 50, message = "{username.invalid}")
    @Schema(description = "Unique username for the account",
            example = "john_doe",
            minLength = 3,
            maxLength = 50)
    private String username;

    @NotBlank(message = "{email.required}")
    @Email(message = "{email.invalid}")
    @ValidEmail(allowDisposable = false, message = "{email.disposable}")
    @Schema(description = "Valid email address (disposable emails not allowed)",
            example = "john.doe@company.com",
            format = "email")
    private String email;

    @NotBlank(message = "{password.required}")
    @ValidPassword
    @Schema(description = "Strong password (min 8 chars, uppercase, lowercase, digit, special char)",
            example = "MySecure123!",
            minLength = 8,
            maxLength = 128)
    private String password;

    @NotBlank(message = "{firstName.required}")
    @ValidName(message = "{firstName.invalid}")
    @Schema(description = "User's first name",
            example = "John",
            minLength = 2,
            maxLength = 50)
    private String firstName;

    @NotBlank(message = "{lastName.required}")
    @ValidName(message = "{lastName.invalid}")
    @Schema(description = "User's last name",
            example = "Doe",
            minLength = 2,
            maxLength = 50)
    private String lastName;

    @ValidPhone(countryCode = "VN", message = "{phone.invalidVietnamese}")
    @Schema(description = "Vietnamese phone number",
            example = "+84901234567",
            pattern = "^(\\+84|84|0)(3[2-9]|5[689]|7[06-9]|8[1-689]|9[0-46-9])\\d{7}$")
    private String phone;

    @Schema(description = "User's address (optional)",
            example = "123 Main Street, District 1, Ho Chi Minh City")
    private String address;
}
