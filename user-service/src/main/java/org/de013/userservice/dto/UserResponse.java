package org.de013.userservice.dto;

import lombok.Builder;
import org.de013.userservice.entity.User;

import java.time.LocalDateTime;

@Builder
public record UserResponse(
        Long id,
        String keycloakId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        String address,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getKeycloakId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getAddress(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
