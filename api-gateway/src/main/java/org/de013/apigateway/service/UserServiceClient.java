package org.de013.apigateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.apigateway.dto.SyncUserRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final WebClient.Builder webClientBuilder;

    private static final String USER_SERVICE_URL = "lb://user-service";
    private static final String SYNC_ENDPOINT = "/users/internal/sync";

    /**
     * Sync user from Keycloak to User Service database
     *
     * @param request User sync request with Keycloak user data
     * @return Mono that completes when sync is done
     */
    public Mono<Void> syncUser(SyncUserRequest request) {
        log.info("Syncing user to User Service - username: {}, keycloakId: {}",
                request.getUsername(), request.getKeycloakId());

        return webClientBuilder.build()
                .post()
                .uri(USER_SERVICE_URL + SYNC_ENDPOINT)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("User synced successfully: {}", request.getUsername()))
                .doOnError(e -> log.error("Failed to sync user {}: {}", request.getUsername(), e.getMessage()))
                .onErrorResume(e -> {
                    log.warn("User sync failed but continuing with registration: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
