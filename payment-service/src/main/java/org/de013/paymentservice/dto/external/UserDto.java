package org.de013.paymentservice.dto.external;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * DTO for User data from User Service
 */
@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Helper methods
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isBlocked() {
        return "BLOCKED".equals(status);
    }

    public boolean isDeleted() {
        return "DELETED".equals(status);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
