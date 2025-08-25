package org.de013.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.de013.common.constant.ApiPaths;
import org.de013.common.controller.BaseController;
import org.de013.common.dto.ApiResponse;
import org.de013.userservice.dto.*;
import org.de013.userservice.service.AuthService;
import org.de013.userservice.service.UserManagementService;
import org.de013.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.API + ApiPaths.V1 + ApiPaths.USERS)
@RequiredArgsConstructor
@Tag(name = "User Management", description = "User profile and administration endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class UserManagementController extends BaseController {

    private final UserManagementService userManagementService;
    private final AuthService authService;

    // ========== Profile Management ==========

    @GetMapping(ApiPaths.PROFILE)
    @Operation(summary = "Get user profile", description = "Get current user's profile information")
    public ResponseEntity<ApiResponse<UserProfileDto>> getUserProfile(Authentication authentication) {
        String username = authentication.getName();
        UserProfileDto profile = userManagementService.getUserProfile(username);
        return ok(profile);
    }

    @PutMapping(ApiPaths.PROFILE)
    @Operation(summary = "Update user profile", description = "Update current user's profile information")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserProfile(
            Authentication authentication,
            @Valid @RequestBody UserUpdateDto request) {
        
        String username = authentication.getName();
        UserResponse updatedUser = userManagementService.updateUserProfile(username, request);
        return ok(updatedUser);
    }

    @PutMapping(ApiPaths.CHANGE_PASSWORD)
    @Operation(summary = "Change password", description = "Change current user's password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordDto request) {
        
        String username = authentication.getName();
        authService.changePassword(request, username);
        return ok("Password changed successfully");
    }

    // ========== Admin Only Endpoints ==========

    @GetMapping(ApiPaths.ID_PARAM)
    @Operation(summary = "Get user by ID (Admin only)", description = "Retrieve user information by user ID")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id) {
        
        UserResponse user = userManagementService.getUserById(id);
        return ok(user);
    }

    @GetMapping
    @Operation(summary = "Get all users (Admin only)", description = "Retrieve all users with pagination")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        PageResponse<UserResponse> users = userManagementService.getAllUsers(pageable);
        return ok(users);
    }

    @GetMapping(ApiPaths.SEARCH)
    @Operation(summary = "Search users (Admin only)", description = "Search users by keyword")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> searchUsers(
            @Parameter(description = "Search keyword", required = true)
            @RequestParam String keyword,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        PageResponse<UserResponse> users = userManagementService.searchUsers(keyword, pageable);
        return ok(users);
    }

    // ========== User Status Management (Admin only) ==========

    @PutMapping(ApiPaths.ID_PARAM + ApiPaths.ENABLE)
    @Operation(summary = "Enable user (Admin only)", description = "Enable user account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> enableUser(@PathVariable Long id) {
        userManagementService.enableUser(id);
        return ok("User enabled successfully");
    }

    @PutMapping(ApiPaths.ID_PARAM + ApiPaths.DISABLE)
    @Operation(summary = "Disable user (Admin only)", description = "Disable user account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> disableUser(@PathVariable Long id) {
        userManagementService.disableUser(id);
        return ok("User disabled successfully");
    }

    @DeleteMapping(ApiPaths.ID_PARAM)
    @Operation(summary = "Delete user (Admin only)", description = "Delete user account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable Long id) {
        userManagementService.deleteUser(id);
        return ok("User deleted successfully");
    }
}
