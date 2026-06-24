package org.de013.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.de013.common.controller.BaseController;
import org.de013.common.dto.ApiResponse;
import org.de013.common.dto.PageResponse;
import org.de013.userservice.dto.UserProfileDto;
import org.de013.userservice.dto.UserResponse;
import org.de013.userservice.dto.UserUpdateDto;
import org.de013.userservice.service.UserManagementService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "User Management", description = "User profile and administration endpoints")
public class UserManagementController extends BaseController {

    private final UserManagementService userManagementService;

    // ========== Profile Management ==========

    @GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Get current user's profile information")
    public ResponseEntity<ApiResponse<UserProfileDto>> getUserProfile(Authentication authentication) {
        String username = authentication.getName();
        UserProfileDto profile = userManagementService.getUserProfile(username);
        return ok(profile);
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile", description = "Update current user's profile information")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserProfile(
            Authentication authentication,
            @Valid @RequestBody UserUpdateDto updateRequest) {

        String username = authentication.getName();
        UserResponse updatedUser = userManagementService.updateUserProfile(username, updateRequest);
        return ok(updatedUser);
    }

    // Note: Password change is now handled by Keycloak directly
    // Users should change password through Keycloak UI or API

    // ========== Admin Only Endpoints ==========

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID (Admin only)", description = "Retrieve user information by user ID. Authorization handled by API Gateway.")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @Parameter(description = "User ID", required = true)
            @PathVariable @Positive(message = "User ID must be positive") Long id) {

        UserResponse user = userManagementService.getUserById(id);
        return ok(user);
    }

    @GetMapping("/username/{username}")
    @Operation(summary = "Get user by username (Admin only)", description = "Retrieve user information by username. Authorization handled by API Gateway.")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByUsername(
            @Parameter(description = "Username", required = true)
            @PathVariable String username) {

        UserResponse user = userManagementService.getUserByUsername(username);
        return ok(user);
    }

    @GetMapping
    @Operation(summary = "Get all users (Admin only)", description = "Retrieve all users with pagination. Authorization handled by API Gateway.")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        PageResponse<UserResponse> users = userManagementService.getAllUsers(pageable);
        return ok(users);
    }

    @GetMapping("/search")
    @Operation(summary = "Search users (Admin only)", description = "Search users by keyword. Authorization handled by API Gateway.")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> searchUsers(
            @Parameter(description = "Search keyword", required = true)
            @RequestParam String keyword,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        PageResponse<UserResponse> users = userManagementService.searchUsers(keyword, pageable);
        return ok(users);
    }

    // ========== User Status Management (Admin only) ==========

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user (Admin only)", description = "Delete user account. Authorization handled by API Gateway.")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable @Positive(message = "User ID must be positive") Long id) {
        userManagementService.deleteUser(id);
        return ok("User deleted successfully");
    }
}
