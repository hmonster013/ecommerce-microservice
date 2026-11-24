package org.de013.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.userservice.dto.UserIdentitySyncResponse;
import org.de013.userservice.exception.ProfileSyncException;
import org.de013.userservice.service.KeycloakProfileClient;
import org.de013.userservice.service.UserIdentitySyncService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserIdentitySyncServiceImpl implements UserIdentitySyncService {
    private final KeycloakProfileClient client;
    private final UserIdentityWriter writer;

    @Override
    public UserIdentitySyncResponse sync(String subject) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try { return writer.write(client.read(subject), null, true); }
            catch (OptimisticLockingFailureException ex) {
                // Retry the canonical read in a fresh database transaction, never a stale snapshot.
            } catch (DataIntegrityViolationException ex) {
                throw new ProfileSyncException(HttpStatus.CONFLICT, "PROFILE_IDENTITY_CONFLICT");
            }
        }
        throw new ProfileSyncException(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_SYNC_PENDING");
    }
}
