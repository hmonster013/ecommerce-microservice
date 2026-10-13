package org.de013.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.userservice.dto.KeycloakProfile;
import org.de013.userservice.dto.UserIdentitySyncResponse;
import org.de013.userservice.dto.UserResponse;
import org.de013.userservice.dto.UserUpdateDto;
import org.de013.userservice.entity.User;
import org.de013.userservice.exception.ProfileSyncException;
import org.de013.userservice.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserIdentityWriter {
    private final UserRepository repository;

    @Transactional(readOnly = true)
    public void checkConflict(KeycloakProfile identity) {
        repository.findByUsername(identity.username()).ifPresent(u -> checkSubject(u, identity.id()));
        repository.findByEmail(identity.email()).ifPresent(u -> checkSubject(u, identity.id()));
    }

    private void checkSubject(User user, String subject) {
        if (!subject.equals(user.getKeycloakId())) throw new ProfileSyncException(HttpStatus.CONFLICT, "PROFILE_IDENTITY_CONFLICT");
    }

    @Transactional
    public UserIdentitySyncResponse write(KeycloakProfile identity, UserUpdateDto update, boolean allowCreate) {
        checkConflict(identity);
        User user = repository.findByKeycloakId(identity.id()).orElse(null);
        boolean created = user == null;
        if (created && !allowCreate) throw new ProfileSyncException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        if (created) user = User.builder().keycloakId(identity.id()).createdAt(LocalDateTime.now()).build();
        boolean changed = created || !Objects.equals(user.getUsername(), identity.username())
                || !Objects.equals(user.getEmail(), identity.email()) || !Objects.equals(user.getFirstName(), identity.firstName())
                || !Objects.equals(user.getLastName(), identity.lastName());
        user.setUsername(identity.username());
        user.setEmail(identity.email());
        user.setFirstName(identity.firstName());
        user.setLastName(identity.lastName());
        if (update != null) {
            if (update.phone() != null) { changed |= !Objects.equals(user.getPhone(), update.phone()); user.setPhone(update.phone()); }
            if (update.address() != null) { changed |= !Objects.equals(user.getAddress(), update.address()); user.setAddress(update.address()); }
        }
        if (changed) {
            user.setUpdatedAt(LocalDateTime.now());
            user.setUpdatedBy(identity.id());
            user = repository.saveAndFlush(user);
        }
        return new UserIdentitySyncResponse(created ? "created" : changed ? "updated" : "unchanged", UserResponse.fromEntity(user));
    }
}
