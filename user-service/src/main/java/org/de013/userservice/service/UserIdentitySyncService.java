package org.de013.userservice.service;

import org.de013.userservice.dto.UserIdentitySyncResponse;

public interface UserIdentitySyncService {
    UserIdentitySyncResponse sync(String subject);
}
