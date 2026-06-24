package org.de013.userservice.integration;

import org.de013.userservice.entity.User;
import org.de013.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void testSaveAndFindByKeycloakId() {
        User user = User.builder()
                .keycloakId("keycloak-user-uuid-1234")
                .username("test_username")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();

        User saved = userRepository.save(user);
        assertNotNull(saved.getId());

        Optional<User> fetched = userRepository.findByKeycloakId("keycloak-user-uuid-1234");
        assertTrue(fetched.isPresent());
        assertEquals("test_username", fetched.get().getUsername());
        assertEquals("test@example.com", fetched.get().getEmail());
    }
}
