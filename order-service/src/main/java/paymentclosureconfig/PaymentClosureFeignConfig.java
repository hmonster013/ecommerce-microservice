package paymentclosureconfig;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.openfeign.clientconfig.FeignClientConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

public class PaymentClosureFeignConfig {
    @Bean FeignClientConfigurer paymentClosureConfigurer() {
        return new FeignClientConfigurer() {
            @Override public boolean inheritParentConfiguration() { return false; }
        };
    }

    @Bean Logger.Level paymentClosureLogger() { return Logger.Level.NONE; }
    @Bean Request.Options paymentClosureTimeouts() { return new Request.Options(5, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true); }

    @Bean RequestInterceptor paymentClosureAuthorization(ObjectProvider<ClientRegistrationRepository> registrations,
                                                       ObjectProvider<OAuth2AuthorizedClientService> clients) {
        return template -> {
            var repository = registrations.getObject();
            var registration = repository.findByRegistrationId("payment-checkout-close");
            if (registration == null || !AuthorizationGrantType.CLIENT_CREDENTIALS.equals(registration.getAuthorizationGrantType())
                    || !"order-service".equals(registration.getClientId())) {
                throw new IllegalStateException("payment-checkout-close must use client_credentials with client ID order-service");
            }
            var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(repository, clients.getObject());
            var client = manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId("payment-checkout-close").principal("order-service").build());
            if (client == null) throw new IllegalStateException("Payment closure authorization failed");
            template.header("Authorization");
            template.header("Authorization", "Bearer " + client.getAccessToken().getTokenValue());
        };
    }
}
