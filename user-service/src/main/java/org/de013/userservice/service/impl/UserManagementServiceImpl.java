package org.de013.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.common.exception.BusinessException;
import org.de013.common.exception.ResourceNotFoundException;
import org.de013.userservice.dto.UserProfileDto;
import org.de013.userservice.dto.UserRegistrationDto;
import org.de013.userservice.dto.UserResponse;
import org.de013.userservice.dto.UserUpdateDto;
import org.de013.userservice.entity.User;
import org.de013.userservice.mapper.UserMapper;
import org.de013.userservice.repository.UserRepository;
import org.de013.userservice.service.UserManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    // ========== User Registration & Creation ==========

    @Override
    public UserResponse registerUser(UserRegistrationDto request) {
        log.info("Registering new user: {}", request.getUsername());

        validateUserRegistration(request);

        // Get default CUSTOMER role
        // Note: User registration should be done through Keycloak
        // This method creates profile only - password managed by Keycloak

        User user = User.builder()
                .keycloakId(request.getKeycloakId()) // Required: Keycloak user UUID
                .username(request.getUsername())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .address(request.getAddress())
                .createdBy("SYSTEM")
                .build();

        user = userRepository.save(user);

        log.info("User registered successfully: {}", user.getUsername());

        return userMapper.convertToUserResponse(user);
    }

    @Override
    public UserResponse createUser(UserRegistrationDto request) {
        log.info("Creating new user: {}", request.getUsername());

        // Note: User creation should be done through Keycloak
        // This method creates profile only - password managed by Keycloak

        validateUserRegistration(request);

        User user = User.builder()
                .keycloakId(request.getKeycloakId()) // Required: Keycloak user UUID
                .username(request.getUsername())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .address(request.getAddress())
                .createdBy("ADMIN")
                .build();

        user = userRepository.save(user);

        log.info("User created successfully: {}", user.getUsername());

        return userMapper.convertToUserResponse(user);
    }

    // ========== User Retrieval ==========

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = findUserById(id);
        return userMapper.convertToUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        User user = findUserByUsername(username);
        return userMapper.convertToUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return userMapper.convertToUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    // ========== User Profile Management ==========

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(String username) {
        User user = findUserByUsername(username);
        return userMapper.convertToUserProfileDto(user);
    }

    @Override
    public UserResponse updateUserProfile(String username, UserUpdateDto request) {
        log.info("Updating profile for user: {}", username);

        User user = findUserByUsername(username);

        // Validate email uniqueness if changed
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (!isEmailAvailableForUpdate(request.getEmail(), user.getId())) {
                throw new BusinessException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        // Update other fields
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }

        user.setUpdatedBy(username);
        user = userRepository.save(user);

        log.info("Profile updated successfully for user: {}", username);

        return userMapper.convertToUserResponse(user);
    }

    // Password management is now handled by Keycloak
    // Users should change password through Keycloak UI or API

    // ========== User Administration ==========

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        return PageResponse.of(users.map(userMapper::convertToUserResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> searchUsers(String keyword, Pageable pageable) {
        log.info("Searching users with keyword: {}", keyword);

        Page<User> users = userRepository.searchUsers(keyword, pageable);

        log.info("Found {} users matching keyword: {}", users.getTotalElements(), keyword);

        return PageResponse.of(users.map(userMapper::convertToUserResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsersCreatedBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Page<User> users = userRepository.findUsersCreatedBetween(startDate, endDate, pageable);
        return PageResponse.of(users.map(userMapper::convertToUserResponse));
    }

    // ========== User Deletion ==========

    @Override
    public void deleteUser(Long userId) {
        log.info("Deleting user: {}", userId);
        User user = findUserById(userId);
        userRepository.delete(user);
        log.info("User deleted successfully: {}", userId);
    }

    // ========== Validation Methods ==========

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsernameAvailableForUpdate(String username, Long userId) {
        return !userRepository.existsByUsernameAndIdNot(username, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmailAvailableForUpdate(String email, Long userId) {
        return !userRepository.existsByEmailAndIdNot(email, userId);
    }

    // ========== Helper Methods ==========

    private void validateUserRegistration(UserRegistrationDto request) {
        if (existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already exists: " + request.getUsername());
        }

        if (existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists: " + request.getEmail());
        }
    }
}
