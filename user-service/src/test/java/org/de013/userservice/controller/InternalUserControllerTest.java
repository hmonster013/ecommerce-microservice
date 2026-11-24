package org.de013.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.userservice.dto.KeycloakProfileProvisionRequest;
import org.de013.userservice.dto.SyncUserRequest;
import org.de013.userservice.entity.User;
import org.de013.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for this controller test
@ActiveProfiles("test")
class InternalUserControllerTest {

    private static final String PROVISION_SECRET_HEADER = "X-Profile-Provisioner-Secret";
    private static final String VALID_SECRET = "test-provisioner-secret-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void validateUserForPayment_WhenUserExists_ShouldReturnValid() throws Exception {
        String keycloakId = "user-uuid-111";

        User user = User.builder()
                .keycloakId(keycloakId)
                .username("john_doe")
                .email("john@example.com")
                .build();

        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/users/internal/{keycloakId}/validate-payment", keycloakId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.canMakePayments").value(true))
                .andExpect(jsonPath("$.userId").value(keycloakId));
    }

    @Test
    void validateUserForPayment_WhenUserDoesNotExist_ShouldReturnUserNotFound() throws Exception {
        String keycloakId = "user-uuid-absent";

        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/users/internal/{keycloakId}/validate-payment", keycloakId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.userExists").value(false))
                .andExpect(jsonPath("$.canMakePayments").value(false));
    }

    @Test
    void syncUserFromKeycloak_WhenNewUser_ShouldCreateAndReturn() throws Exception {
        SyncUserRequest request = new SyncUserRequest(
                "user-uuid-new",
                "jane_doe",
                "jane@example.com",
                "Jane",
                "Doe"
        );

        when(userRepository.findByKeycloakId("user-uuid-new")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/users/internal/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("jane_doe"))
                .andExpect(jsonPath("$.data.email").value("jane@example.com"));
    }

    @Test
    void provisionUserProfile_WhenNewUserAndValidSecret_ShouldReturn201Created() throws Exception {
        KeycloakProfileProvisionRequest request = KeycloakProfileProvisionRequest.builder()
                .keycloakId("kc-new-123")
                .username("alice_smith")
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .build();

        when(userRepository.findByKeycloakId("kc-new-123")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice_smith")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/internal/keycloak/users")
                        .header(PROVISION_SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keycloakId").value("kc-new-123"))
                .andExpect(jsonPath("$.data.username").value("alice_smith"))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"));
    }

    @Test
    void provisionUserProfile_WhenExistingUserAndValidSecret_ShouldReturn200OkIdempotent() throws Exception {
        KeycloakProfileProvisionRequest request = KeycloakProfileProvisionRequest.builder()
                .keycloakId("kc-existing-456")
                .username("bob_jones")
                .email("bob@example.com")
                .firstName("Bob")
                .lastName("Jones")
                .build();

        User existingUser = User.builder()
                .keycloakId("kc-existing-456")
                .username("bob_jones")
                .email("bob@example.com")
                .firstName("Bob")
                .lastName("Jones")
                .phone("+84901234567")
                .address("123 Saigon")
                .build();

        when(userRepository.findByKeycloakId("kc-existing-456")).thenReturn(Optional.of(existingUser));

        mockMvc.perform(post("/internal/keycloak/users")
                        .header(PROVISION_SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keycloakId").value("kc-existing-456"))
                .andExpect(jsonPath("$.data.phone").value("+84901234567"))
                .andExpect(jsonPath("$.data.address").value("123 Saigon"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void provisionUserProfile_WhenInvalidSecret_ShouldReturn401Unauthorized() throws Exception {
        KeycloakProfileProvisionRequest request = KeycloakProfileProvisionRequest.builder()
                .keycloakId("kc-new-123")
                .username("alice_smith")
                .email("alice@example.com")
                .build();

        mockMvc.perform(post("/internal/keycloak/users")
                        .header(PROVISION_SECRET_HEADER, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void provisionUserProfile_WhenMissingSecret_ShouldReturn401Unauthorized() throws Exception {
        KeycloakProfileProvisionRequest request = KeycloakProfileProvisionRequest.builder()
                .keycloakId("kc-new-123")
                .username("alice_smith")
                .email("alice@example.com")
                .build();

        mockMvc.perform(post("/internal/keycloak/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void provisionUserProfile_WhenUsernameConflict_ShouldReturn409Conflict() throws Exception {
        KeycloakProfileProvisionRequest request = KeycloakProfileProvisionRequest.builder()
                .keycloakId("kc-new-123")
                .username("existing_username")
                .email("new@example.com")
                .build();

        User userWithSameUsername = User.builder()
                .keycloakId("kc-different-user")
                .username("existing_username")
                .email("other@example.com")
                .build();

        when(userRepository.findByKeycloakId("kc-new-123")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("existing_username")).thenReturn(Optional.of(userWithSameUsername));

        mockMvc.perform(post("/internal/keycloak/users")
                        .header(PROVISION_SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_CONFLICT"));
    }

    @Test
    void provisionUserProfile_WhenValidationFails_ShouldReturn400BadRequest() throws Exception {
        // Missing keycloakId and invalid email
        KeycloakProfileProvisionRequest request = KeycloakProfileProvisionRequest.builder()
                .keycloakId("")
                .username("")
                .email("invalid-email")
                .build();

        mockMvc.perform(post("/internal/keycloak/users")
                        .header(PROVISION_SECRET_HEADER, VALID_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
