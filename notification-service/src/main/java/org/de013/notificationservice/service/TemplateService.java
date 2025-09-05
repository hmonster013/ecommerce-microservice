package org.de013.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.NotificationTemplate;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.repository.NotificationTemplateRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing notification templates
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class TemplateService {

    private final NotificationTemplateRepository templateRepository;
    private final TemplateEngine templateEngine;
    private final org.de013.notificationservice.template.AdvancedTemplateEngine advancedTemplateEngine;
    private final LocalizationService localizationService;

    public TemplateService(NotificationTemplateRepository templateRepository,
                          @Qualifier("customTemplateEngine") TemplateEngine templateEngine,
                          org.de013.notificationservice.template.AdvancedTemplateEngine advancedTemplateEngine,
                          LocalizationService localizationService) {
        this.templateRepository = templateRepository;
        this.templateEngine = templateEngine;
        this.advancedTemplateEngine = advancedTemplateEngine;
        this.localizationService = localizationService;
    }

    /**
     * Find active template by name, channel, and language
     */
    @Cacheable(value = "notification-templates", key = "#name + '_' + #channel + '_' + #language")
    public Optional<NotificationTemplate> findActiveTemplate(String name, NotificationChannel channel, String language) {
        log.debug("Finding active template: name={}, channel={}, language={}", name, channel, language);
        
        Optional<NotificationTemplate> template = templateRepository
                .findByNameAndChannelAndLanguageAndActiveTrueAndDeletedFalse(name, channel, language);
        
        if (template.isEmpty() && !"en".equals(language)) {
            // Fallback to English if specific language not found
            log.debug("Template not found for language {}, falling back to English", language);
            template = templateRepository
                    .findByNameAndChannelAndLanguageAndActiveTrueAndDeletedFalse(name, channel, "en");
        }
        
        return template;
    }

    /**
     * Find all active templates by type and channel
     */
    public List<NotificationTemplate> findActiveTemplatesByTypeAndChannel(NotificationType type, NotificationChannel channel) {
        log.debug("Finding active templates by type={} and channel={}", type, channel);
        return templateRepository.findByTypeAndChannelAndActiveTrueAndDeletedFalseOrderByCreatedAtDesc(type, channel);
    }

    /**
     * Find all active templates
     */
    public Page<NotificationTemplate> findActiveTemplates(Pageable pageable) {
        log.debug("Finding all active templates with pagination");
        return templateRepository.findByActiveTrueAndDeletedFalseOrderByCreatedAtDesc(pageable);
    }

    /**
     * Find template by ID
     */
    public Optional<NotificationTemplate> findById(Long id) {
        log.debug("Finding template by id={}", id);
        return templateRepository.findById(id);
    }

    /**
     * Create new template
     */
    @Transactional
    @CacheEvict(value = "notification-templates", allEntries = true)
    public NotificationTemplate createTemplate(NotificationTemplate template) {
        log.info("Creating new template: name={}, channel={}, type={}", 
                template.getName(), template.getChannel(), template.getType());
        
        // Validate template
        validateTemplate(template);
        
        // Set initial values
        template.setActive(false); // New templates start as inactive
        template.setTemplateVersion(1);
        
        NotificationTemplate savedTemplate = templateRepository.save(template);
        log.info("Template created successfully with id={}", savedTemplate.getId());
        
        return savedTemplate;
    }

    /**
     * Update existing template
     */
    @Transactional
    @CacheEvict(value = "notification-templates", allEntries = true)
    public NotificationTemplate updateTemplate(Long id, NotificationTemplate updatedTemplate) {
        log.info("Updating template with id={}", id);
        
        NotificationTemplate existingTemplate = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found with id: " + id));
        
        // Validate updated template
        validateTemplate(updatedTemplate);
        
        // Update fields
        existingTemplate.setDisplayName(updatedTemplate.getDisplayName());
        existingTemplate.setDescription(updatedTemplate.getDescription());
        existingTemplate.setSubjectTemplate(updatedTemplate.getSubjectTemplate());
        existingTemplate.setBodyTemplate(updatedTemplate.getBodyTemplate());
        existingTemplate.setHtmlTemplate(updatedTemplate.getHtmlTemplate());
        existingTemplate.setVariables(updatedTemplate.getVariables());
        existingTemplate.setDefaultValues(updatedTemplate.getDefaultValues());
        existingTemplate.setValidationRules(updatedTemplate.getValidationRules());
        existingTemplate.setSenderName(updatedTemplate.getSenderName());
        existingTemplate.setSenderEmail(updatedTemplate.getSenderEmail());
        existingTemplate.setReplyTo(updatedTemplate.getReplyTo());
        existingTemplate.setMetadata(updatedTemplate.getMetadata());
        existingTemplate.setTags(updatedTemplate.getTags());
        existingTemplate.setCategory(updatedTemplate.getCategory());
        
        NotificationTemplate savedTemplate = templateRepository.save(existingTemplate);
        log.info("Template updated successfully with id={}", savedTemplate.getId());
        
        return savedTemplate;
    }

    /**
     * Activate template and deactivate other versions
     */
    @Transactional
    @CacheEvict(value = "notification-templates", allEntries = true)
    public void activateTemplate(Long id) {
        log.info("Activating template with id={}", id);
        
        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found with id: " + id));
        
        // Deactivate other versions of the same template
        templateRepository.deactivateOtherVersions(
                template.getName(), 
                template.getChannel(), 
                id, 
                LocalDateTime.now()
        );
        
        // Activate this template
        template.activate();
        templateRepository.save(template);
        
        log.info("Template activated successfully with id={}", id);
    }

    /**
     * Deactivate template
     */
    @Transactional
    @CacheEvict(value = "notification-templates", allEntries = true)
    public void deactivateTemplate(Long id) {
        log.info("Deactivating template with id={}", id);
        
        NotificationTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found with id: " + id));
        
        template.deactivate();
        templateRepository.save(template);
        
        log.info("Template deactivated successfully with id={}", id);
    }

    /**
     * Render template with variables
     */
    public String renderTemplate(String templateContent, Map<String, Object> variables) {
        log.debug("Rendering template with {} variables", variables != null ? variables.size() : 0);
        
        try {
            Context context = new Context();
            if (variables != null) {
                context.setVariables(variables);
            }
            
            String rendered = templateEngine.process(templateContent, context);
            log.debug("Template rendered successfully");
            return rendered;
            
        } catch (Exception e) {
            log.error("Error rendering template: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to render template: " + e.getMessage(), e);
        }
    }

    /**
     * Render subject template
     */
    public String renderSubject(NotificationTemplate template, Map<String, Object> variables) {
        if (template.getSubjectTemplate() == null || template.getSubjectTemplate().trim().isEmpty()) {
            return null;
        }
        
        // Merge default values with provided variables
        Map<String, Object> mergedVariables = mergeVariables(template.getDefaultValues(), variables);
        
        return renderTemplate(template.getSubjectTemplate(), mergedVariables);
    }

    /**
     * Render body template
     */
    public String renderBody(NotificationTemplate template, Map<String, Object> variables) {
        if (template.getBodyTemplate() == null || template.getBodyTemplate().trim().isEmpty()) {
            throw new RuntimeException("Body template is required");
        }
        
        // Merge default values with provided variables
        Map<String, Object> mergedVariables = mergeVariables(template.getDefaultValues(), variables);
        
        return renderTemplate(template.getBodyTemplate(), mergedVariables);
    }

    /**
     * Render HTML template
     */
    public String renderHtml(NotificationTemplate template, Map<String, Object> variables) {
        if (template.getHtmlTemplate() == null || template.getHtmlTemplate().trim().isEmpty()) {
            return null;
        }
        
        // Merge default values with provided variables
        Map<String, Object> mergedVariables = mergeVariables(template.getDefaultValues(), variables);
        
        return renderTemplate(template.getHtmlTemplate(), mergedVariables);
    }

    /**
     * Search templates
     */
    public Page<NotificationTemplate> searchTemplates(String searchTerm, Pageable pageable) {
        log.debug("Searching templates with term: {}", searchTerm);
        return templateRepository.searchTemplates(searchTerm, pageable);
    }

    /**
     * Validate template
     */
    private void validateTemplate(NotificationTemplate template) {
        if (template.getName() == null || template.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Template name is required");
        }
        
        if (template.getChannel() == null) {
            throw new IllegalArgumentException("Template channel is required");
        }
        
        if (template.getType() == null) {
            throw new IllegalArgumentException("Template type is required");
        }
        
        if (template.getBodyTemplate() == null || template.getBodyTemplate().trim().isEmpty()) {
            throw new IllegalArgumentException("Body template is required");
        }
        
        // Validate template syntax by trying to render with empty variables
        try {
            renderTemplate(template.getBodyTemplate(), Map.of());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid body template syntax: " + e.getMessage());
        }
        
        // Validate subject template if provided
        if (template.getSubjectTemplate() != null && !template.getSubjectTemplate().trim().isEmpty()) {
            try {
                renderTemplate(template.getSubjectTemplate(), Map.of());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid subject template syntax: " + e.getMessage());
            }
        }
        
        // Validate HTML template if provided
        if (template.getHtmlTemplate() != null && !template.getHtmlTemplate().trim().isEmpty()) {
            try {
                renderTemplate(template.getHtmlTemplate(), Map.of());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid HTML template syntax: " + e.getMessage());
            }
        }
    }

    /**
     * Merge default values with provided variables
     */
    private Map<String, Object> mergeVariables(Map<String, Object> defaultValues, Map<String, Object> variables) {
        Map<String, Object> merged = new java.util.HashMap<>();

        if (defaultValues != null) {
            merged.putAll(defaultValues);
        }

        if (variables != null) {
            merged.putAll(variables); // Override defaults with provided values
        }

        return merged;
    }
}
