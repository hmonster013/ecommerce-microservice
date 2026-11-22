package org.de013.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@SuppressWarnings("removal")
public class HttpJsonConfig implements WebMvcConfigurer {
    private final ObjectMapper objectMapper;

    public HttpJsonConfig(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.withJsonConverter(new MappingJackson2HttpMessageConverter(objectMapper));
    }
}
