package org.de013.userservice.service;

import org.de013.common.dto.PageResponse;
import org.de013.userservice.dto.UserProfileDto;
import org.de013.userservice.dto.UserResponse;
import org.de013.userservice.dto.UserUpdateDto;
import org.de013.userservice.entity.User;
import org.springframework.data.domain.Pageable;

/**
 * User Management Service Interface
 * Handles user CRUD operations, profile management, and user administration
 */
public interface UserManagementService {

    // ========== User Retrieval ==========

    /**
     * Get user by ID
     *
     * @param id User ID
     * @return User response DTO
     */
    UserResponse getUserById(Long id);

    /**
     * Get user by username
     *
     * @param username Username
     * @return User response DTO
     */
    UserResponse getUserByUsername(String username);

    /**
     * Find user entity by username (internal use)
     *
     * @param username Username
     * @return User entity
     */
    User findUserByUsername(String username);

    /**
     * Find user entity by ID (internal use)
     *
     * @param id User ID
     * @return User entity
     */
    User findUserById(Long id);

    // ========== User Profile Management ==========

    /**
     * Get user profile
     *
     * @param username Username
     * @return User profile DTO
     */
    UserProfileDto getUserProfile(String username);

    /**
     * Update user profile
     *
     * @param username Username
     * @param request  Profile update details
     * @return Updated user response DTO
     */
    UserResponse updateUserProfile(String username, UserUpdateDto request);

    /**
     * Get all users with pagination
     *
     * @param pageable Pagination parameters
     * @return Page of user responses
     */
    PageResponse<UserResponse> getAllUsers(Pageable pageable);

    /**
     * Search users by keyword
     *
     * @param keyword  Search keyword
     * @param pageable Pagination parameters
     * @return Page of user responses
     */
    PageResponse<UserResponse> searchUsers(String keyword, Pageable pageable);

    /**
     * Delete user by ID
     *
     * @param userId User ID
     */
    void deleteUser(Long userId);

}
