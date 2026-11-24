package org.de013.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.PageResponse;
import org.de013.common.exception.ResourceNotFoundException;
import org.de013.userservice.dto.KeycloakProfile;
import org.de013.userservice.dto.UserProfileDto;
import org.de013.userservice.dto.UserResponse;
import org.de013.userservice.dto.UserUpdateDto;
import org.de013.userservice.entity.User;
import org.de013.userservice.exception.ProfileSyncException;
import org.de013.userservice.mapper.UserMapper;
import org.de013.userservice.repository.UserRepository;
import org.de013.userservice.service.KeycloakProfileClient;
import org.de013.userservice.service.UserManagementService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final KeycloakProfileClient keycloakProfileClient;
    private final UserIdentityWriter identityWriter;

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
    public UserProfileDto getUserProfile(String subject) {
        User user = userRepository.findByKeycloakId(subject)
                .orElseThrow(() -> new ResourceNotFoundException("User", "keycloakId", subject));
        return userMapper.convertToUserProfileDto(user);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse updateUserProfile(String subject, UserUpdateDto request) {
        if (!userRepository.existsByKeycloakId(subject)) throw new ResourceNotFoundException("User", "keycloakId", subject);
        KeycloakProfile canonical = keycloakProfileClient.read(subject);
        identityWriter.checkConflict(canonical);
        boolean namesChanged = (request.firstName() != null && !Objects.equals(request.firstName(), canonical.firstName()))
                || (request.lastName() != null && !Objects.equals(request.lastName(), canonical.lastName()));
        if (namesChanged) keycloakProfileClient.updateNames(subject, request.firstName(), request.lastName());
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    canonical = keycloakProfileClient.read(subject);
                    return identityWriter.write(canonical, request, false).user();
                } catch (OptimisticLockingFailureException ex) {
                    // Read canonical identity again before retrying a short DB transaction.
                }
            }
            throw new ProfileSyncException(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_SYNC_PENDING");
        } catch (DataIntegrityViolationException ex) {
            throw new ProfileSyncException(HttpStatus.CONFLICT, "PROFILE_IDENTITY_CONFLICT");
        } catch (RuntimeException ex) {
            if (ex instanceof ProfileSyncException sync && sync.getStatus().value() == 409) throw ex;
            throw new ProfileSyncException(HttpStatus.SERVICE_UNAVAILABLE,
                    namesChanged ? "PROFILE_SYNC_PENDING" : "PROFILE_IDENTITY_UNAVAILABLE");
        }
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
