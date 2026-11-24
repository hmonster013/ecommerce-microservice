package org.de013.paymentservice;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;

import static org.junit.jupiter.api.Assertions.*;

class PaymentJacksonCompatibilityTest {
    record Fixture(Date date, Duration duration, BigDecimal amount, String optional) {}

    @Test void centralJackson2ConfigurationPreservesWireFormat() throws Exception {
        var path = Path.of(System.getProperty("basedir"), "..", "config-server", "src", "main", "resources", "config", "payment-service.yml");
        var sources = new YamlPropertySourceLoader().load("central", new FileSystemResource(path));
        var json = new LinkedHashMap<String, Object>();
        for (var source : sources) {
            var enumerable = (EnumerablePropertySource<?>) source;
            for (String key : enumerable.getPropertyNames()) {
                if (key.startsWith("spring.jackson2.")) json.put(key, source.getProperty(key));
            }
        }
        assertEquals(5, json.size());
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(Jackson2AutoConfiguration.class))
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("central-json", json)))
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertFalse(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
                    assertFalse(mapper.isEnabled(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS));
                    assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
                    assertEquals("UTC", mapper.getSerializationConfig().getTimeZone().getID());
                    assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", ((SimpleDateFormat) mapper.getDateFormat()).toPattern());
                    var fixture = new Fixture(Date.from(Instant.parse("2025-01-02T03:04:05.123Z")), Duration.ofSeconds(90), new BigDecimal("12.30"), null);
                    assertEquals("{\"date\":\"2025-01-02T03:04:05.123Z\",\"duration\":\"PT1M30S\",\"amount\":12.30,\"optional\":null}", mapper.writeValueAsString(fixture));
                    assertEquals(fixture, mapper.readValue("{\"date\":\"2025-01-02T03:04:05.123Z\",\"duration\":\"PT1M30S\",\"amount\":12.30,\"optional\":null,\"unknown\":true}", Fixture.class));
                });
    }
}
