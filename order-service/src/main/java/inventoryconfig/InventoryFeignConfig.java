package inventoryconfig;

import feign.Logger;
import feign.RequestInterceptor;
import feign.Request;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.clientconfig.FeignClientConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@org.springframework.context.annotation.Import(org.de013.orderservice.config.FeignResilienceConfig.RethrowFallbackFactory.class)
public class InventoryFeignConfig {
    @Bean
    FeignClientConfigurer inventoryFeignClientConfigurer() {
        return new FeignClientConfigurer() {
            @Override public boolean inheritParentConfiguration() { return false; }
        };
    }

    @Bean
    Logger.Level inventoryLoggerLevel() { return Logger.Level.NONE; }

    @Bean
    Request.Options inventoryTimeouts() {
        return new Request.Options(5, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
    }

    @Bean
    RequestInterceptor inventoryAuthorization(ObjectProvider<ClientRegistrationRepository> registrations,
                                              ObjectProvider<OAuth2AuthorizedClientService> clients) {
        return template -> {
            ClientRegistrationRepository repository = registrations.getObject();
            var registration = repository.findByRegistrationId("catalog-inventory");
            if (registration == null || !AuthorizationGrantType.CLIENT_CREDENTIALS.equals(registration.getAuthorizationGrantType())
                    || !"order-service".equals(registration.getClientId())) {
                throw new IllegalStateException("catalog-inventory must use client_credentials with client ID order-service");
            }
            var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                    repository, clients.getObject());
            OAuth2AuthorizedClient client = manager.authorize(OAuth2AuthorizeRequest
                    .withClientRegistrationId("catalog-inventory").principal("order-service").build());
            if (client == null) throw new IllegalStateException("Catalog inventory authorization failed");
            template.header("Authorization");
            template.header("Authorization", "Bearer " + client.getAccessToken().getTokenValue());
        };
    }
}
