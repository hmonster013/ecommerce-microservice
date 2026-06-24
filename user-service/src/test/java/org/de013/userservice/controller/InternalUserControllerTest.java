package org.de013.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.userservice.dto.SyncUserRequest;
import org.de013.userservice.entity.User;
import org.de013.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @Autowired
    private MockMvc mockMvc;

    @MockBean
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
        SyncUserRequest request = SyncUserRequest.builder()
                .keycloakId("user-uuid-new")
                .username("jane_doe")
                .email("jane@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .build();

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
}
