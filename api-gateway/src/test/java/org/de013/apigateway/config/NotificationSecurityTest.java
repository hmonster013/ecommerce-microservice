package org.de013.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.context.ApplicationContext;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;

@SpringJUnitConfig(NotificationSecurityTest.TestConfig.class)
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://example.invalid/jwks")
class NotificationSecurityTest {
    @Autowired ApplicationContext context;

    @Test
    void sendRoutesRequireAdminOrSupport() {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).build();
        for (String route : new String[]{"send-email", "send-sms", "send-both"}) {
            String path = "/api/v1/notification-service/notifications/" + route;
            client.post().uri(path).exchange().expectStatus().isUnauthorized();
            client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                    .post().uri(path).header("X-User-Roles", "ADMIN,SUPPORT")
                    .header("X-User-Id", "forged").header("X-User-Email", "forged@example.com")
                    .exchange().expectStatus().isForbidden();
            for (String role : new String[]{"ADMIN", "SUPPORT"}) {
                client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                        .post().uri(path).exchange().expectStatus().isOk();
            }
        }
    }

    @Configuration
    @EnableWebFlux
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean
        RouterFunction<ServerResponse> routes() {
            return RouterFunctions.route(POST("/api/v1/notification-service/notifications/{route}"),
                    request -> ServerResponse.ok().build());
        }
    }
}
