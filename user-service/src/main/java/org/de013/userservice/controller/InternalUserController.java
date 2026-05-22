package org.de013.userservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.ApiResponse;
import org.de013.userservice.dto.SyncUserRequest;
import org.de013.userservice.dto.UserResponse;
import org.de013.userservice.entity.Role;
import org.de013.userservice.entity.User;
import org.de013.userservice.repository.RoleRepository;
import org.de013.userservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<UserResponse>> syncUserFromKeycloak(@Valid @RequestBody SyncUserRequest request) {
        log.info("Syncing user from Keycloak - keycloakId: {}, username: {}", request.getKeycloakId(), request.getUsername());

        User user = userRepository.findByKeycloakId(request.getKeycloakId())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .keycloakId(request.getKeycloakId())
                            .username(request.getUsername())
                            .email(request.getEmail())
                            .firstName(request.getFirstName() != null ? request.getFirstName() : "N/A")
                            .lastName(request.getLastName() != null ? request.getLastName() : "N/A")
                            .enabled(true)
                            .accountNonExpired(true)
                            .accountNonLocked(true)
                            .credentialsNonExpired(true)
                            .build();

                    log.info("Creating new user profile for Keycloak user: {}", request.getKeycloakId());
                    return userRepository.save(newUser);
                });

        UserResponse response = UserResponse.fromEntity(user);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(response, "User synced successfully"));
    }

    @GetMapping("/by-keycloak-id/{keycloakId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByKeycloakId(@PathVariable String keycloakId) {
        log.debug("Fetching user by keycloakId: {}", keycloakId);

        return userRepository.findByKeycloakId(keycloakId)
                .map(user -> ResponseEntity.ok(ApiResponse.success(UserResponse.fromEntity(user))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("User not found with keycloakId: " + keycloakId, "USER_NOT_FOUND")));
    }

    /**
     * Get or create user by Keycloak ID
     * This endpoint supports JIT (Just-In-Time) user synchronization
     * If user doesn't exist, creates a new user profile with default CUSTOMER role
     *
     * @param keycloakId Keycloak user UUID (sub claim from JWT)
     * @param username   Username from Keycloak
     * @param email      Email from Keycloak
     * @param firstName  First name (optional)
     * @param lastName   Last name (optional)
     * @return User response with user details
     */
    @GetMapping("/get-or-create")
    public ResponseEntity<ApiResponse<UserResponse>> getOrCreateUser(
            @RequestParam String keycloakId,
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName) {

        log.info("Get or create user - keycloakId: {}, username: {}, email: {}", keycloakId, username, email);

        // Try to find existing user
        User user = userRepository.findByKeycloakId(keycloakId).orElseGet(() -> {
            log.info("User not found with keycloakId: {}. Creating new user profile.", keycloakId);

            // Get default CUSTOMER role
            Role customerRole = roleRepository.findByName("CUSTOMER")
                    .orElseThrow(() -> new RuntimeException("Default CUSTOMER role not found in database"));

            // Create new user with default role
            User newUser = User.builder()
                    .keycloakId(keycloakId)
                    .username(username)
                    .email(email)
                    .firstName(firstName != null ? firstName : "N/A")
                    .lastName(lastName != null ? lastName : "N/A")
                    .enabled(true)
                    .accountNonExpired(true)
                    .accountNonLocked(true)
                    .credentialsNonExpired(true)
                    .build();

            // Add default CUSTOMER role
            newUser.getRoles().add(customerRole);

            // Save and return
            User savedUser = userRepository.save(newUser);
            log.info("New user profile created successfully: id={}, username={}", savedUser.getId(), savedUser.getUsername());

            return savedUser;
        });

        UserResponse response = UserResponse.fromEntity(user);
        return ResponseEntity.ok(ApiResponse.success(response, "User retrieved successfully"));
    }
}
