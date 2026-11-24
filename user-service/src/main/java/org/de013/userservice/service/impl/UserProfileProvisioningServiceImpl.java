package org.de013.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.de013.userservice.dto.KeycloakProfileProvisionRequest;
import org.de013.userservice.dto.ProvisionResult;
import org.de013.userservice.dto.UserResponse;
import org.de013.userservice.entity.User;
import org.de013.userservice.exception.ProfileConflictException;
import org.de013.userservice.repository.UserRepository;
import org.de013.userservice.service.UserProfileProvisioningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileProvisioningServiceImpl implements UserProfileProvisioningService {

    private final UserRepository userRepository;

    @Setter
    @Value("${app.security.profile-provisioner-secret:${USER_PROFILE_PROVISIONER_SECRET:}}")
    private String expectedSecret;

    @Override
    public void verifyProvisionerSecret(String providedSecret) {
        if (expectedSecret == null || expectedSecret.isBlank() || providedSecret == null || providedSecret.isBlank()) {
            log.warn("Profile provisioning rejected: missing provisioner secret");
            throw new BadCredentialsException("Invalid profile provisioner secret");
        }

        byte[] expectedBytes = expectedSecret.trim().getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = providedSecret.trim().getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            log.warn("Profile provisioning rejected: provisioner secret mismatch");
            throw new BadCredentialsException("Invalid profile provisioner secret");
        }
    }

    @Override
    @Transactional
    public ProvisionResult provisionUserProfile(KeycloakProfileProvisionRequest request) {
        // 1. Idempotency check: does profile already exist with this keycloakId?
        Optional<User> existingUserOpt = userRepository.findByKeycloakId(request.keycloakId());
        if (existingUserOpt.isPresent()) {
            User existing = existingUserOpt.get();
            log.info("User profile already provisioned for keycloakId: {}", request.keycloakId());
            // Safe idempotency: do not overwrite phone, address or other user-service managed fields
            return ProvisionResult.alreadyExists(UserResponse.fromEntity(existing));
        }

        // 2. Conflict check: username belongs to another keycloakId
        userRepository.findByUsername(request.username()).ifPresent(userWithUsername -> {
            if (!request.keycloakId().equals(userWithUsername.getKeycloakId())) {
                log.warn("Profile conflict: username '{}' belongs to another keycloakId '{}'",
                        request.username(), userWithUsername.getKeycloakId());
                throw new ProfileConflictException("Username '" + request.username() + "' is already in use");
            }
        });

        // 3. Conflict check: email belongs to another keycloakId
        userRepository.findByEmail(request.email()).ifPresent(userWithEmail -> {
            if (!request.keycloakId().equals(userWithEmail.getKeycloakId())) {
                log.warn("Profile conflict: email '{}' belongs to another keycloakId '{}'",
                        request.email(), userWithEmail.getKeycloakId());
                throw new ProfileConflictException("Email '" + request.email() + "' is already in use");
            }
        });

        // 4. Create new user profile
        User newUser = User.builder()
                .keycloakId(request.keycloakId())
                .username(request.username())
                .email(request.email())
                .firstName(request.firstName() != null ? request.firstName() : "")
                .lastName(request.lastName() != null ? request.lastName() : "")
                .build();

        try {
            User saved = userRepository.save(newUser);
            log.info("Successfully provisioned new profile: keycloakId={}, username={}",
                    saved.getKeycloakId(), saved.getUsername());
            return ProvisionResult.created(UserResponse.fromEntity(saved));
        } catch (DataIntegrityViolationException ex) {
            log.warn("Constraint violation or concurrent insert for keycloakId {}: {}",
                    request.keycloakId(), ex.getMessage());
            // Retry find by keycloakId in case of concurrent insert
            return userRepository.findByKeycloakId(request.keycloakId())
                    .map(user -> ProvisionResult.alreadyExists(UserResponse.fromEntity(user)))
                    .orElseThrow(() -> new ProfileConflictException(
                            "Constraint violation while provisioning user: " + ex.getMostSpecificCause().getMessage(), ex));
        }
    }
}
