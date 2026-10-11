package org.de013.userservice.service;

import org.de013.userservice.dto.KeycloakProfileProvisionRequest;
import org.de013.userservice.dto.ProvisionResult;
import org.de013.userservice.entity.User;
import org.de013.userservice.exception.ProfileConflictException;
import org.de013.userservice.repository.UserRepository;
import org.de013.userservice.service.impl.UserProfileProvisioningServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileProvisioningServiceTest {

    private static final String TEST_SECRET = "my-secret-key-123";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserProfileProvisioningServiceImpl provisioningService;

    @BeforeEach
    void setUp() {
        provisioningService.setExpectedSecret(TEST_SECRET);
    }

    @Test
    void verifyProvisionerSecret_WithValidSecret_ShouldSucceed() {
        assertDoesNotThrow(() -> provisioningService.verifyProvisionerSecret(TEST_SECRET));
    }

    @Test
    void verifyProvisionerSecret_WithInvalidSecret_ShouldThrowBadCredentialsException() {
        assertThrows(BadCredentialsException.class,
                () -> provisioningService.verifyProvisionerSecret("invalid-secret"));
    }

    @Test
    void verifyProvisionerSecret_WithNullOrBlankSecret_ShouldThrowBadCredentialsException() {
        assertThrows(BadCredentialsException.class,
                () -> provisioningService.verifyProvisionerSecret(null));
        assertThrows(BadCredentialsException.class,
                () -> provisioningService.verifyProvisionerSecret(""));
        assertThrows(BadCredentialsException.class,
                () -> provisioningService.verifyProvisionerSecret("   "));
    }

    @Test
    void provisionUserProfile_WhenUserDoesNotExist_ShouldCreateProfile() {
        KeycloakProfileProvisionRequest request = KeycloakProfileProvisionRequest.builder()
                .keycloakId("kc-uuid-1")
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();

        when(userRepository.findByKeycloakId("kc-uuid-1")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        User savedUser = User.builder()
                .id(1L)
                .keycloakId("kc-uuid-1")
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        ProvisionResult result = provisioningService.provisionUserProfile(request);

        assertTrue(result.isCreated());
        assertEquals("kc-uuid-1", result.getUser().keycloakId());
        assertEquals("testuser", result.getUser().username());
        assertEquals("test@example.com", result.getUser().email());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void provisionUserProfile_WhenUserAlreadyExists_ShouldReturnExistingProfileWithoutOverwrite() {
        KeycloakProfileProvisionRequest request = KeycloakProfileProvisionRequest.builder()
                .keycloakId("kc-uuid-1")
                .username("testuser_new")
                .email("new@example.com")
                .firstName("NewFirst")
                .lastName("NewLast")
                .build();

        User existingUser = User.builder()
                .id(1L)
                .keycloakId("kc-uuid-1")
                .username("testuser")
                .email("test@example.com")
                .firstName("OldFirst")
                .lastName("OldLast")
                .phone("+84999999999")
                .address("Hanoi")
                .build();

        when(userRepository.findByKeycloakId("kc-uuid-1")).thenReturn(Optional.of(existingUser));

        ProvisionResult result = provisioningService.provisionUserProfile(request);

        assertFalse(result.isCreated());
        assertEquals("kc-uuid-1", result.getUser().keycloakId());
        assertEquals("testuser", result.getUser().username());
        assertEquals("+84999999999", result.getUser().phone());
        assertEquals("Hanoi", result.getUser().address());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void provisionUserProfile_WhenUsernameInUseByOtherUser_ShouldThrowConflictException() {
        KeycloakProfileProvisionRequest request = KeycloakProfileProvisionRequest.builder()
                .keycloakId("kc-uuid-new")
                .username("conflict_user")
                .email("new@example.com")
                .build();

        User conflictUser = User.builder()
                .id(99L)
                .keycloakId("kc-uuid-different")
                .username("conflict_user")
                .email("conflict@example.com")
                .build();

        when(userRepository.findByKeycloakId("kc-uuid-new")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("conflict_user")).thenReturn(Optional.of(conflictUser));

        assertThrows(ProfileConflictException.class,
                () -> provisioningService.provisionUserProfile(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void provisionUserProfile_WhenEmailInUseByOtherUser_ShouldThrowConflictException() {
        KeycloakProfileProvisionRequest request = KeycloakProfileProvisionRequest.builder()
                .keycloakId("kc-uuid-new")
                .username("new_user")
                .email("conflict@example.com")
                .build();

        User conflictUser = User.builder()
                .id(99L)
                .keycloakId("kc-uuid-different")
                .username("other_user")
                .email("conflict@example.com")
                .build();

        when(userRepository.findByKeycloakId("kc-uuid-new")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("new_user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("conflict@example.com")).thenReturn(Optional.of(conflictUser));

        assertThrows(ProfileConflictException.class,
                () -> provisioningService.provisionUserProfile(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void provisionUserProfile_WhenConcurrentInsert_ShouldCatchConstraintViolationAndRetry() {
        KeycloakProfileProvisionRequest request = KeycloakProfileProvisionRequest.builder()
                .keycloakId("kc-uuid-concurrent")
                .username("concurrent_user")
                .email("concurrent@example.com")
                .build();

        User concurrentCreated = User.builder()
                .id(101L)
                .keycloakId("kc-uuid-concurrent")
                .username("concurrent_user")
                .email("concurrent@example.com")
                .build();

        // 1st check: empty
        when(userRepository.findByKeycloakId("kc-uuid-concurrent"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrentCreated)); // 2nd check after violation: present
        when(userRepository.findByUsername("concurrent_user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("concurrent@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key on keycloak_id"));

        ProvisionResult result = provisioningService.provisionUserProfile(request);

        assertFalse(result.isCreated());
        assertEquals("kc-uuid-concurrent", result.getUser().keycloakId());
    }
}
