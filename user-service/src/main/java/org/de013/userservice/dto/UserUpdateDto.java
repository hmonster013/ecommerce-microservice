package org.de013.userservice.dto;

import jakarta.validation.constraints.Size;
import lombok.Builder;
import org.de013.userservice.validator.ValidPhone;

@Builder
public record UserUpdateDto(
        @Size(max = 50, message = "First name must not exceed 50 characters")
        String firstName,

        @Size(max = 50, message = "Last name must not exceed 50 characters")
        String lastName,

        @ValidPhone(countryCode = "VN", message = "Invalid phone number format")
        String phone,

        @Size(max = 255, message = "Address must not exceed 255 characters")
        String address
) {}
