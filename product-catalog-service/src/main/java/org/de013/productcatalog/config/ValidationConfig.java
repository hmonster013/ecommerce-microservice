package org.de013.productcatalog.config;

import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * Configuration for validation framework.
 * Sets up custom validators, message sources, and validation processing.
 */
@Slf4j
@Configuration
public class ValidationConfig {

    /**
     * Configure message source for validation messages.
     */
    @Bean
    public MessageSource validationMessageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:ValidationMessages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(300); // Cache for 5 minutes
        messageSource.setFallbackToSystemLocale(false);
        
        log.info("Configured validation message source with custom messages");
        return messageSource;
    }

    /**
     * Configure validator factory with custom message source.
     */
    @Bean
    public LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validatorFactory = new LocalValidatorFactoryBean();
        validatorFactory.setValidationMessageSource(validationMessageSource());
        
        log.info("Configured custom validator factory");
        return validatorFactory;
    }

    /**
     * Configure method validation post processor for @Validated annotations.
     */
    @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.setValidator(validator());
        
        log.info("Configured method validation post processor");
        return processor;
    }

    /**
     * Primary validator bean.
     */
    @Bean
    public Validator primaryValidator() {
        return validator().getValidator();
    }
}
