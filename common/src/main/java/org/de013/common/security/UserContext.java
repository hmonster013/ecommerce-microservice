package org.de013.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    
    private Long userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;
    
    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String role) {
        if (roles == null) {
            return false;
        }
        return roles.contains("ROLE_" + role) || roles.contains(role);
    }
    
    /**
     * Check if user has any of the specified roles
     */
    public boolean hasAnyRole(String... roles) {
        if (this.roles == null) {
            return false;
        }
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if user is admin
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
    
    /**
     * Check if user is customer
     */
    public boolean isCustomer() {
        return hasRole("CUSTOMER") || hasRole("USER");
    }
    
    /**
     * Get display name
     */
    public String getDisplayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return username;
        }
    }
    
    /**
     * Check if user context is valid (has required fields)
     */
    public boolean isValid() {
        return userId != null && username != null && !username.trim().isEmpty();
    }
}
