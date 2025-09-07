package org.de013.notificationservice.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * User context extracted from API Gateway headers
 * Used as principal in Spring Security Authentication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    
    private Long userId;
    private String username;
    private String email;
    private List<String> roles;
    
    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String role) {
        if (roles == null) {
            return false;
        }
        
        String roleToCheck = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return roles.contains(roleToCheck);
    }
    
    /**
     * Check if user has admin role
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
    
    /**
     * Check if user has user role
     */
    public boolean isUser() {
        return hasRole("USER");
    }
}
