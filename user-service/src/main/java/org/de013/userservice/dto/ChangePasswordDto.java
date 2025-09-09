package org.de013.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.de013.userservice.validator.ValidPassword;
import org.de013.userservice.validator.ValidPasswordConfirmation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidPasswordConfirmation
@Schema(description = "Change password request")
public class ChangePasswordDto {

    @NotBlank(message = "Current password is required")
    @Schema(description = "Current user password",
            example = "OldPassword123!",
            required = true,
            format = "password")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @ValidPassword
    @Schema(description = "New password (min 8 chars, uppercase, lowercase, digit, special char)",
            example = "NewPassword123!",
            required = true,
            format = "password",
            minLength = 8,
            maxLength = 128)
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    @Schema(description = "Confirmation of the new password (must match newPassword)",
            example = "NewPassword123!",
            required = true,
            format = "password")
    private String confirmPassword;
}
