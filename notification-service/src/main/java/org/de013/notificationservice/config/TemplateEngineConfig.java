package org.de013.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Configuration for Thymeleaf Template Engine
 */
@Configuration
public class TemplateEngineConfig {

    /**
     * Configure Thymeleaf Template Engine for string templates
     */
    @Bean("customTemplateEngine")
    public TemplateEngine templateEngine() {
        TemplateEngine templateEngine = new TemplateEngine();
        
        // String template resolver for processing templates from database
        StringTemplateResolver stringTemplateResolver = new StringTemplateResolver();
        stringTemplateResolver.setTemplateMode(TemplateMode.TEXT);
        stringTemplateResolver.setCacheable(false); // Disable caching for dynamic templates
        stringTemplateResolver.setOrder(1);
        
        // HTML template resolver for HTML content
        StringTemplateResolver htmlTemplateResolver = new StringTemplateResolver();
        htmlTemplateResolver.setTemplateMode(TemplateMode.HTML);
        htmlTemplateResolver.setCacheable(false);
        htmlTemplateResolver.setOrder(2);
        
        templateEngine.addTemplateResolver(stringTemplateResolver);
        templateEngine.addTemplateResolver(htmlTemplateResolver);
        
        return templateEngine;
    }
}
