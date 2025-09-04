package org.de013.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Thymeleaf Configuration for template processing
 */
@Configuration
public class ThymeleafConfig {

    /**
     * Template engine for processing notification templates
     */
    @Bean
    public TemplateEngine templateEngine() {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addTemplateResolver(stringTemplateResolver());
        return templateEngine;
    }

    /**
     * String template resolver for processing templates from database
     */
    @Bean
    public StringTemplateResolver stringTemplateResolver() {
        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.TEXT);
        templateResolver.setCacheable(false); // Disable caching for dynamic templates
        return templateResolver;
    }
}
