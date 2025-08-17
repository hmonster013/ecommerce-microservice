package org.de013.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User login request")
public class UserLoginDto {

    @NotBlank(message = "{username.required}")
    @Schema(description = "Username or email address",
            example = "john_doe",
            required = true)
    private String username;

    @NotBlank(message = "{password.required}")
    @Schema(description = "User password",
            example = "MySecure123!",
            required = true,
            format = "password")
    private String password;
}
