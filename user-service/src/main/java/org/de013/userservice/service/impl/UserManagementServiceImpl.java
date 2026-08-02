package org.de013.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.common.exception.ResourceNotFoundException;
import org.de013.userservice.exception.EmailAlreadyExistsException;
import org.de013.userservice.dto.UserProfileDto;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

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
        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmailAndIdNot(request.email(), user.getId())) {
                throw new EmailAlreadyExistsException(request.email());
            }
            user.setEmail(request.email());
        }

        // Update other fields
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.address() != null) {
            user.setAddress(request.address());
        }

        user.setUpdatedBy(username);
        user = userRepository.save(user);

        log.info("Profile updated successfully for user: {}", username);

        return userMapper.convertToUserResponse(user);
    }

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
    public void deleteUser(Long userId) {
        log.info("Deleting user: {}", userId);
        User user = findUserById(userId);
        userRepository.delete(user);
        log.info("User deleted successfully: {}", userId);
    }

}
