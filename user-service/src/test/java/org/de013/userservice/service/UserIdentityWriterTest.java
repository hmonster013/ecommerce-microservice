package org.de013.userservice.service;

import org.de013.userservice.dto.KeycloakProfile;
import org.de013.userservice.entity.User;
import org.de013.userservice.repository.UserRepository;
import org.de013.userservice.service.impl.UserIdentityWriter;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class UserIdentityWriterTest {
    @Test
    void identityProjectionPreservesBusinessFieldsAndIsIdempotent() {
        UserRepository repository = mock(UserRepository.class);
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);
        User user = User.builder().id(1L).keycloakId("subject").username("old").email("old@mail.net")
                .firstName("Old").lastName("Name").phone("0912345678").address("Home")
                .createdAt(created).updatedAt(created).build();
        when(repository.findByKeycloakId("subject")).thenReturn(Optional.of(user));
        when(repository.saveAndFlush(user)).thenReturn(user);
        UserIdentityWriter writer = new UserIdentityWriter(repository);
        KeycloakProfile identity = new KeycloakProfile("subject", "new", "new@mail.net", "", "New");
        assertEquals("updated", writer.write(identity, null, true).outcome());
        assertEquals("", user.getFirstName());
        assertEquals("0912345678", user.getPhone());
        assertEquals("Home", user.getAddress());
        assertEquals(created, user.getCreatedAt());
        assertTrue(user.getUpdatedAt().isAfter(created));
        assertEquals("unchanged", writer.write(identity, null, true).outcome());
        verify(repository, times(1)).saveAndFlush(user);
    }
}
