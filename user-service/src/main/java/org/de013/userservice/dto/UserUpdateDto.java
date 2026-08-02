package org.de013.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import org.de013.userservice.validator.ValidEmail;
import org.de013.userservice.validator.ValidPhone;

@Builder
public record UserUpdateDto(
        @Email(message = "Email should be valid")
        @ValidEmail(allowDisposable = true, message = "Invalid email format")
        String email,

        @Size(max = 50, message = "First name must not exceed 50 characters")
        String firstName,

        @Size(max = 50, message = "Last name must not exceed 50 characters")
        String lastName,

        @ValidPhone(countryCode = "VN", message = "Invalid phone number format")
        String phone,

        String address
) {}
