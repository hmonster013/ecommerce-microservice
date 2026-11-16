package org.de013.orderservice;

import org.de013.orderservice.config.SecurityConfig;
import org.de013.orderservice.security.HeaderAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = OrderSecurityOAuthRegressionTest.App.class, properties = {
        "spring.flyway.enabled=false",
        "spring.security.oauth2.client.registration.catalog-inventory.client-id=order-service",
        "spring.security.oauth2.client.registration.catalog-inventory.client-secret=fixture-only",
        "spring.security.oauth2.client.registration.catalog-inventory.authorization-grant-type=client_credentials",
        "spring.security.oauth2.client.provider.catalog-inventory.token-uri=http://127.0.0.1:1/token"})
@AutoConfigureMockMvc
class OrderSecurityOAuthRegressionTest {
    @Configuration @EnableAutoConfiguration
    @Import({SecurityConfig.class, HeaderAuthenticationFilter.class, Probe.class})
    static class App { }

    @RestController static class Probe {
        @GetMapping("/security-probe") String probe(Authentication auth) { return auth == null ? "anonymous" : auth.getName(); }
    }

    @Autowired MockMvc mvc;
    @Autowired FilterChainProxy security;

    @Test void registrationDoesNotChangeInboundSecurity() throws Exception {
        mvc.perform(get("/security-probe")).andExpect(status().isOk()).andExpect(content().string("anonymous"));
        mvc.perform(get("/security-probe").header("X-User-Id", "id").header("X-User-Username", "alice"))
                .andExpect(status().isOk()).andExpect(content().string("alice"));
        var filters = security.getFilters("/security-probe");
        assertTrue(filters.stream().anyMatch(f -> f instanceof HeaderAuthenticationFilter));
        assertTrue(filters.stream().noneMatch(f -> f.getClass().getName().contains("oauth2")));
    }
}
