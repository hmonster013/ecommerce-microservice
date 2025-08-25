package org.de013.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.common.exception.BusinessException;
import org.de013.common.exception.ResourceNotFoundException;
import org.de013.userservice.dto.*;
import org.de013.userservice.entity.Role;
import org.de013.userservice.entity.User;
import org.de013.userservice.mapper.UserMapper;
import org.de013.userservice.repository.RoleRepository;
import org.de013.userservice.repository.UserRepository;
import org.de013.userservice.service.UserManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    // ========== User Registration & Creation ==========

    @Override
    public UserResponse registerUser(UserRegistrationDto request) {
        log.info("Registering new user: {}", request.getUsername());
        
        validateUserRegistration(request);
        
        // Get default CUSTOMER role
        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new BusinessException("Default CUSTOMER role not found"));
        
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .address(request.getAddress())
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .createdBy("SYSTEM")
                .build();
        
        // Add default role
        user.getRoles().add(customerRole);
        
        user = userRepository.save(user);
        
        log.info("User registered successfully: {}", user.getUsername());
        
        return userMapper.convertToUserResponse(user);
    }

    @Override
    public UserResponse createUser(UserRegistrationDto request) {
        log.info("Creating new user: {}", request.getUsername());
        
        validateUserRegistration(request);
        
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .address(request.getAddress())
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
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

    @Override
    public void updatePassword(String username, String newPassword) {
        log.info("Updating password for user: {}", username);
        
        User user = findUserByUsername(username);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedBy(username);
        
        userRepository.save(user);
        
        log.info("Password updated successfully for user: {}", username);
    }

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
        // Note: userRepository.searchUsers needs to be implemented with Specification for dynamic search
        Page<User> users = userRepository.findAll(pageable); // Placeholder
        return PageResponse.of(users.map(userMapper::convertToUserResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(String roleName) {
        List<User> users = userRepository.findByRoleName(roleName);
        return users.stream()
                .map(userMapper::convertToUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getActiveUsers() {
        List<User> users = userRepository.findAllActiveUsers();
        return users.stream()
                .map(userMapper::convertToUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsersCreatedBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Page<User> users = userRepository.findUsersCreatedBetween(startDate, endDate, pageable);
        return PageResponse.of(users.map(userMapper::convertToUserResponse));
    }

    // ========== User Status Management ==========

    @Override
    public void enableUser(Long userId) {
        log.info("Enabling user: {}", userId);
        User user = findUserById(userId);
        user.setEnabled(true);
        userRepository.save(user);
        log.info("User enabled successfully: {}", userId);
    }

    @Override
    public void disableUser(Long userId) {
        log.info("Disabling user: {}", userId);
        User user = findUserById(userId);
        user.setEnabled(false);
        userRepository.save(user);
        log.info("User disabled successfully: {}", userId);
    }

    @Override
    public void lockUser(Long userId) {
        log.info("Locking user: {}", userId);
        User user = findUserById(userId);
        user.setAccountNonLocked(false);
        userRepository.save(user);
        log.info("User locked successfully: {}", userId);
    }

    @Override
    public void unlockUser(Long userId) {
        log.info("Unlocking user: {}", userId);
        User user = findUserById(userId);
        user.setAccountNonLocked(true);
        userRepository.save(user);
        log.info("User unlocked successfully: {}", userId);
    }

    // ========== User Role Management ==========

    @Override
    public void assignRole(Long userId, String roleName) {
        log.info("Assigning role {} to user: {}", roleName, userId);
        
        User user = findUserById(userId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));
        
        user.getRoles().add(role);
        userRepository.save(user);
        
        log.info("Role {} assigned successfully to user: {}", roleName, userId);
    }

    @Override
    public void removeRole(Long userId, String roleName) {
        log.info("Removing role {} from user: {}", roleName, userId);
        
        User user = findUserById(userId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName));
        
        user.getRoles().remove(role);
        userRepository.save(user);
        
        log.info("Role {} removed successfully from user: {}", roleName, userId);
    }

    @Override
    public void updateUserRoles(Long userId, List<String> roleNames) {
        log.info("Updating roles for user: {} with roles: {}", userId, roleNames);
        
        User user = findUserById(userId);
        Set<Role> roles = roleNames.stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("Role", "name", roleName)))
                .collect(Collectors.toSet());
        
        user.setRoles(roles);
        userRepository.save(user);
        
        log.info("Roles updated successfully for user: {}", userId);
    }

    // ========== User Deletion ==========

    @Override
    public void deleteUser(Long userId) {
        log.info("Deleting user: {}", userId);
        User user = findUserById(userId);
        userRepository.delete(user);
        log.info("User deleted successfully: {}", userId);
    }

    @Override
    public void softDeleteUser(Long userId) {
        log.info("Soft deleting user: {}", userId);
        disableUser(userId);
        log.info("User soft deleted successfully: {}", userId);
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
