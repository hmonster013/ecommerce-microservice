package org.de013.notificationservice.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User context extracted from API Gateway headers
 * Used as principal in Spring Security Authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {

    private String userId;
    private String username;
    private String email;
}

