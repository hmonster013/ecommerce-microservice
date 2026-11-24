package org.de013.userservice.service;

import org.de013.userservice.dto.KeycloakProfile;
import org.de013.userservice.dto.UserUpdateDto;
import org.de013.userservice.exception.ProfileSyncException;
import org.de013.userservice.mapper.UserMapper;
import org.de013.userservice.repository.UserRepository;
import org.de013.userservice.service.impl.UserIdentityWriter;
import org.de013.userservice.service.impl.UserManagementServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileUpdateOrchestrationTest {
    @Test
    void failedKeycloakWriteNeverWritesBusinessData() {
        UserRepository repo = mock(UserRepository.class);
        KeycloakProfileClient client = mock(KeycloakProfileClient.class);
        UserIdentityWriter writer = mock(UserIdentityWriter.class);
        when(repo.existsByKeycloakId("subject")).thenReturn(true);
        when(client.read("subject")).thenReturn(new KeycloakProfile("subject", "alice", "alice@mail.net", "Old", "Name"));
        doThrow(new ProfileSyncException(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_UPDATE_UNCERTAIN"))
                .when(client).updateNames("subject", "New", null);
        var service = new UserManagementServiceImpl(repo, mock(UserMapper.class), client, writer);
        ProfileSyncException ex = assertThrows(ProfileSyncException.class,
                () -> service.updateUserProfile("subject", new UserUpdateDto("New", null, "0912345678", "")));
        assertEquals("PROFILE_UPDATE_UNCERTAIN", ex.getCode());
        verify(writer, never()).write(any(), any(), anyBoolean());
        verify(client, times(1)).updateNames(any(), any(), any());
    }

    @Test
    void databaseFailureAfterIdentityWriteReportsPending() {
        UserRepository repo = mock(UserRepository.class);
        KeycloakProfileClient client = mock(KeycloakProfileClient.class);
        UserIdentityWriter writer = mock(UserIdentityWriter.class);
        when(repo.existsByKeycloakId("subject")).thenReturn(true);
        when(client.read("subject")).thenReturn(new KeycloakProfile("subject", "alice", "alice@mail.net", "Old", "Name"));
        when(writer.write(any(), any(), eq(false))).thenThrow(new IllegalStateException("DB unavailable"));
        var service = new UserManagementServiceImpl(repo, mock(UserMapper.class), client, writer);
        ProfileSyncException ex = assertThrows(ProfileSyncException.class,
                () -> service.updateUserProfile("subject", new UserUpdateDto("New", null, null, null)));
        assertEquals("PROFILE_SYNC_PENDING", ex.getCode());
        verify(client, times(1)).updateNames("subject", "New", null);
    }
}
