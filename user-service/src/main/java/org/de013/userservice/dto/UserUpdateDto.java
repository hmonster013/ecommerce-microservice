package org.de013.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.de013.userservice.validator.ValidEmail;
import org.de013.userservice.validator.ValidPhone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDto {
    @Email(message = "Email should be valid")
    @ValidEmail(allowDisposable = true, message = "Invalid email format")
    private String email;

    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;

    @ValidPhone(countryCode = "VN", message = "Invalid phone number format")
    private String phone;

    private String address;
}
