package org.de013.orderservice;

import feign.RequestTemplate;
import inventoryconfig.InventoryFeignConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import feign.RequestInterceptor;

import static org.junit.jupiter.api.Assertions.*;

class InventoryRegistrationValidationTest {
    @Test void rejectsWrongGrantEvenWithCachedToken() { reject(AuthorizationGrantType.PASSWORD, "order-service"); }
    @Test void rejectsWrongClientEvenWithCachedToken() { reject(AuthorizationGrantType.CLIENT_CREDENTIALS, "other-client"); }

    private void reject(AuthorizationGrantType grant, String clientId) {
        var registration = ClientRegistration.withRegistrationId("catalog-inventory")
                .clientId(clientId).clientSecret("fixture-only").authorizationGrantType(grant)
                .tokenUri("http://127.0.0.1:1/token").build();
        var repository = new InMemoryClientRegistrationRepository(registration);
        var service = new InMemoryOAuth2AuthorizedClientService(repository);
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "cached", Instant.now(), Instant.now().plusSeconds(3600));
        service.saveAuthorizedClient(new OAuth2AuthorizedClient(registration, "order-service", token),
                new UsernamePasswordAuthenticationToken("order-service", ""));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ClientRegistrationRepository.class, () -> repository);
            context.registerBean(OAuth2AuthorizedClientService.class, () -> service);
            context.register(InventoryFeignConfig.class);
            context.refresh();
            var template = new RequestTemplate();
            assertThrows(IllegalStateException.class, () -> context.getBean(RequestInterceptor.class).apply(template));
            assertTrue(template.headers().isEmpty());
        }
    }
}
