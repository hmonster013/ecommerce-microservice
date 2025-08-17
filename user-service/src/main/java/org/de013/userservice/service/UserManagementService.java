package org.de013.userservice.service;

import org.de013.userservice.dto.*;
import org.de013.userservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Management Service Interface
 * Handles user CRUD operations, profile management, and user administration
 */
public interface UserManagementService {

    // ========== User Registration & Creation ==========

    /**
     * Register a new user
     * @param request User registration details
     * @return User response DTO
     */
    UserResponse registerUser(UserRegistrationDto request);

    /**
     * Create a new user (admin function)
     * @param request User creation details
     * @return User response DTO
     */
    UserResponse createUser(UserRegistrationDto request);

    // ========== User Retrieval ==========

    /**
     * Get user by ID
     * @param id User ID
     * @return User response DTO
     */
    UserResponse getUserById(Long id);

    /**
     * Get user by username
     * @param username Username
     * @return User response DTO
     */
    UserResponse getUserByUsername(String username);

    /**
     * Get user by email
     * @param email Email address
     * @return User response DTO
     */
    UserResponse getUserByEmail(String email);

    /**
     * Find user entity by username (internal use)
     * @param username Username
     * @return User entity
     */
    User findUserByUsername(String username);

    /**
     * Find user entity by ID (internal use)
     * @param id User ID
     * @return User entity
     */
    User findUserById(Long id);

    // ========== User Profile Management ==========

    /**
     * Get user profile
     * @param username Username
     * @return User profile DTO
     */
    UserProfileDto getUserProfile(String username);

    /**
     * Update user profile
     * @param username Username
     * @param request Profile update details
     * @return Updated user response DTO
     */
    UserResponse updateUserProfile(String username, UserUpdateDto request);

    /**
     * Update user password
     * @param username Username
     * @param newPassword New password (plain text)
     */
    void updatePassword(String username, String newPassword);

    // ========== User Administration ==========

    /**
     * Get all users with pagination
     * @param pageable Pagination parameters
     * @return Page of user responses
     */
    Page<UserResponse> getAllUsers(Pageable pageable);

    /**
     * Search users by keyword
     * @param keyword Search keyword
     * @param pageable Pagination parameters
     * @return Page of user responses
     */
    Page<UserResponse> searchUsers(String keyword, Pageable pageable);

    /**
     * Get users by role
     * @param roleName Role name
     * @return List of user responses
     */
    List<UserResponse> getUsersByRole(String roleName);

    /**
     * Get active users
     * @return List of active user responses
     */
    List<UserResponse> getActiveUsers();

    /**
     * Get users created in date range
     * @param startDate Start date
     * @param endDate End date
     * @param pageable Pagination parameters
     * @return Page of user responses
     */
    Page<UserResponse> getUsersCreatedBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // ========== User Status Management ==========

    /**
     * Enable user account
     * @param userId User ID
     */
    void enableUser(Long userId);

    /**
     * Disable user account
     * @param userId User ID
     */
    void disableUser(Long userId);

    /**
     * Lock user account
     * @param userId User ID
     */
    void lockUser(Long userId);

    /**
     * Unlock user account
     * @param userId User ID
     */
    void unlockUser(Long userId);

    // ========== User Role Management ==========

    /**
     * Assign role to user
     * @param userId User ID
     * @param roleName Role name
     */
    void assignRole(Long userId, String roleName);

    /**
     * Remove role from user
     * @param userId User ID
     * @param roleName Role name
     */
    void removeRole(Long userId, String roleName);

    /**
     * Update user roles
     * @param userId User ID
     * @param roleNames List of role names
     */
    void updateUserRoles(Long userId, List<String> roleNames);

    // ========== User Deletion ==========

    /**
     * Delete user by ID
     * @param userId User ID
     */
    void deleteUser(Long userId);

    /**
     * Soft delete user (disable instead of actual deletion)
     * @param userId User ID
     */
    void softDeleteUser(Long userId);

    // ========== Validation Methods ==========

    /**
     * Check if username exists
     * @param username Username
     * @return true if exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists
     * @param email Email address
     * @return true if exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Validate username availability for update
     * @param username Username
     * @param userId Current user ID
     * @return true if available, false otherwise
     */
    boolean isUsernameAvailableForUpdate(String username, Long userId);

    /**
     * Validate email availability for update
     * @param email Email address
     * @param userId Current user ID
     * @return true if available, false otherwise
     */
    boolean isEmailAvailableForUpdate(String email, Long userId);
}
