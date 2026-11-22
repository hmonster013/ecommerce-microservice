package org.de013.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.discovery.DiscoveryLocatorProperties;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CentralGatewayPropertiesTest {
    @Test
    void bindsGatewaySettingsFromCentralConfig() throws Exception {
        String prefix = "spring.cloud.gateway.server.webflux";
        assertThat(GatewayProperties.PREFIX).isEqualTo(prefix);
        var sources = new YamlPropertySourceLoader().load("central-gateway",
                new FileSystemResource("../config-server/src/main/resources/config/api-gateway.yml"));
        Map<String, Object> properties = new LinkedHashMap<>();
        for (var source : sources) {
            var enumerable = (EnumerablePropertySource<?>) source;
            for (String name : enumerable.getPropertyNames()) {
                if (name.startsWith(prefix + ".")) {
                    properties.put(name, enumerable.getProperty(name));
                }
            }
        }
        var binder = new Binder(new MapConfigurationPropertySource(properties));
        var client = binder.bind(prefix + ".httpclient", HttpClientProperties.class).get();
        var discovery = binder.bind(prefix + ".discovery.locator", DiscoveryLocatorProperties.class).get();
        assertThat(binder.bindOrCreate(prefix, GatewayProperties.class)).isNotNull();

        assertThat(client.getConnectTimeout()).isEqualTo(5000);
        assertThat(client.getResponseTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(client.getPool().getMaxConnections()).isEqualTo(500);
        assertThat(client.getPool().getMaxIdleTime()).isEqualTo(Duration.ofSeconds(30));
        assertThat(binder.bind(prefix + ".forwarded.enabled", Boolean.class).get()).isFalse();
        assertThat(discovery.isEnabled()).isFalse();
    }
}
