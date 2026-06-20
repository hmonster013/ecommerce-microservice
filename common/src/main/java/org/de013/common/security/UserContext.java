package org.de013.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {

    private String userId;
    private String username;
    private String email;

    /**
     * Check if user context is valid (has required fields)
     */
    public boolean isValid() {
        return userId != null && username != null && !username.trim().isEmpty();
    }
}
