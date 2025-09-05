package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.TemplateContent;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.template.AdvancedTemplateEngine;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Service for template preview functionality
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemplatePreviewService {

    private final AdvancedTemplateEngine templateEngine;
    private final ContentManagementService contentManagementService;
    private final LocalizationService localizationService;

    /**
     * Preview template content with sample data
     */
    public TemplatePreviewResult previewTemplate(Long contentId, NotificationChannel channel, 
                                                Map<String, Object> sampleData) {
        log.info("Previewing template content: contentId={}, channel={}", contentId, channel);

        try {
            TemplateContent content = contentManagementService.getContentById(contentId);
            
            // Get content for specific channel
            String templateContent = content.getContentForChannel(channel);
            
            // Merge sample data with content variables
            Map<String, Object> variables = new HashMap<>();
            if (content.getVariables() != null) {
                variables.putAll(content.getVariables());
            }
            if (sampleData != null) {
                variables.putAll(sampleData);
            }
            
            // Add default sample data if not provided
            variables.putAll(getDefaultSampleData());
            
            // Process template
            String processedContent = templateEngine.processTemplate(templateContent, variables);
            
            // Create preview result
            return TemplatePreviewResult.builder()
                    .contentId(contentId)
                    .channel(channel)
                    .originalContent(templateContent)
                    .processedContent(processedContent)
                    .subject(processSubject(content.getSubject(), variables))
                    .variables(variables)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Error previewing template: contentId={}, channel={}, error={}", 
                    contentId, channel, e.getMessage(), e);
            
            return TemplatePreviewResult.builder()
                    .contentId(contentId)
                    .channel(channel)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Preview template with localization
     */
    public TemplatePreviewResult previewLocalizedTemplate(Long templateId, String languageCode, 
                                                         NotificationChannel channel, 
                                                         Map<String, Object> sampleData) {
        log.info("Previewing localized template: templateId={}, language={}, channel={}", 
                templateId, languageCode, channel);

        try {
            TemplateContent content = localizationService.getLocalizedContent(templateId, languageCode);
            return previewTemplate(content.getId(), channel, sampleData);

        } catch (Exception e) {
            log.error("Error previewing localized template: templateId={}, language={}, error={}", 
                    templateId, languageCode, e.getMessage(), e);
            
            return TemplatePreviewResult.builder()
                    .contentId(null)
                    .channel(channel)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Preview template for multiple channels
     */
    public Map<NotificationChannel, TemplatePreviewResult> previewMultiChannel(Long contentId, 
                                                                              Map<String, Object> sampleData) {
        log.info("Previewing template for multiple channels: contentId={}", contentId);

        Map<NotificationChannel, TemplatePreviewResult> results = new HashMap<>();
        
        for (NotificationChannel channel : NotificationChannel.values()) {
            try {
                TemplatePreviewResult result = previewTemplate(contentId, channel, sampleData);
                results.put(channel, result);
            } catch (Exception e) {
                log.error("Error previewing template for channel: contentId={}, channel={}, error={}", 
                        contentId, channel, e.getMessage(), e);
                
                results.put(channel, TemplatePreviewResult.builder()
                        .contentId(contentId)
                        .channel(channel)
                        .success(false)
                        .error(e.getMessage())
                        .build());
            }
        }
        
        return results;
    }

    /**
     * Validate template syntax
     */
    public TemplateValidationResult validateTemplate(Long contentId) {
        log.info("Validating template: contentId={}", contentId);

        try {
            TemplateContent content = contentManagementService.getContentById(contentId);
            
            TemplateValidationResult.TemplateValidationResultBuilder resultBuilder = 
                    TemplateValidationResult.builder()
                            .contentId(contentId)
                            .valid(true);

            // Validate content for each channel
            Map<NotificationChannel, String> channelErrors = new HashMap<>();
            
            for (NotificationChannel channel : NotificationChannel.values()) {
                try {
                    String templateContent = content.getContentForChannel(channel);
                    if (templateContent != null && !templateContent.isEmpty()) {
                        // Try to process with sample data
                        templateEngine.processTemplate(templateContent, getDefaultSampleData());
                    }
                } catch (Exception e) {
                    channelErrors.put(channel, e.getMessage());
                    resultBuilder.valid(false);
                }
            }

            // Validate subject
            try {
                if (content.getSubject() != null && !content.getSubject().isEmpty()) {
                    processSubject(content.getSubject(), getDefaultSampleData());
                }
            } catch (Exception e) {
                resultBuilder.subjectError(e.getMessage()).valid(false);
            }

            return resultBuilder
                    .channelErrors(channelErrors)
                    .build();

        } catch (Exception e) {
            log.error("Error validating template: contentId={}, error={}", contentId, e.getMessage(), e);
            
            return TemplateValidationResult.builder()
                    .contentId(contentId)
                    .valid(false)
                    .generalError(e.getMessage())
                    .build();
        }
    }

    /**
     * Get template variables analysis
     */
    public TemplateVariablesAnalysis analyzeTemplateVariables(Long contentId) {
        log.info("Analyzing template variables: contentId={}", contentId);

        try {
            TemplateContent content = contentManagementService.getContentById(contentId);
            
            // Extract variables from content
            Map<NotificationChannel, java.util.Set<String>> channelVariables = new HashMap<>();
            
            for (NotificationChannel channel : NotificationChannel.values()) {
                String templateContent = content.getContentForChannel(channel);
                if (templateContent != null) {
                    java.util.Set<String> variables = extractVariables(templateContent);
                    channelVariables.put(channel, variables);
                }
            }

            // Extract variables from subject
            java.util.Set<String> subjectVariables = content.getSubject() != null ? 
                    extractVariables(content.getSubject()) : new java.util.HashSet<>();

            // Get all unique variables
            java.util.Set<String> allVariables = new java.util.HashSet<>();
            channelVariables.values().forEach(allVariables::addAll);
            allVariables.addAll(subjectVariables);

            return TemplateVariablesAnalysis.builder()
                    .contentId(contentId)
                    .allVariables(allVariables)
                    .channelVariables(channelVariables)
                    .subjectVariables(subjectVariables)
                    .totalVariableCount(allVariables.size())
                    .build();

        } catch (Exception e) {
            log.error("Error analyzing template variables: contentId={}, error={}", contentId, e.getMessage(), e);
            
            return TemplateVariablesAnalysis.builder()
                    .contentId(contentId)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Process subject with variables
     */
    private String processSubject(String subject, Map<String, Object> variables) {
        if (subject == null) return null;
        return templateEngine.processTemplate(subject, variables);
    }

    /**
     * Get default sample data for preview
     */
    private Map<String, Object> getDefaultSampleData() {
        Map<String, Object> sampleData = new HashMap<>();
        
        // User data
        sampleData.put("userName", "John Doe");
        sampleData.put("userEmail", "john.doe@example.com");
        sampleData.put("firstName", "John");
        sampleData.put("lastName", "Doe");
        sampleData.put("fullName", "John Doe");
        
        // Order data
        sampleData.put("orderId", "ORD-12345");
        sampleData.put("orderNumber", "12345");
        sampleData.put("totalAmount", "99.99");
        sampleData.put("currency", "USD");
        sampleData.put("orderDate", "2024-01-15");
        sampleData.put("itemCount", 3);
        
        // Payment data
        sampleData.put("paymentId", "PAY-67890");
        sampleData.put("paymentMethod", "Credit Card");
        sampleData.put("amount", "99.99");
        
        // System data
        sampleData.put("companyName", "Your Company");
        sampleData.put("supportEmail", "support@yourcompany.com");
        sampleData.put("websiteUrl", "https://yourcompany.com");
        sampleData.put("currentDate", java.time.LocalDate.now().toString());
        sampleData.put("currentYear", String.valueOf(java.time.Year.now().getValue()));
        
        // Localization data
        sampleData.put("greeting", "Hello");
        sampleData.put("userLanguage", "en");
        sampleData.put("userTimezone", "UTC");
        
        return sampleData;
    }

    /**
     * Extract variables from template content
     */
    private java.util.Set<String> extractVariables(String content) {
        java.util.Set<String> variables = new java.util.HashSet<>();
        
        if (content == null) return variables;
        
        // Extract {{variable}} patterns
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{([^}]+)\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String variable = matcher.group(1).trim();
            // Skip block directives
            if (!variable.startsWith("#") && !variable.startsWith("/")) {
                variables.add(variable);
            }
        }
        
        return variables;
    }

    /**
     * Template preview result
     */
    @lombok.Data
    @lombok.Builder
    public static class TemplatePreviewResult {
        private Long contentId;
        private NotificationChannel channel;
        private String originalContent;
        private String processedContent;
        private String subject;
        private Map<String, Object> variables;
        private boolean success;
        private String error;
    }

    /**
     * Template validation result
     */
    @lombok.Data
    @lombok.Builder
    public static class TemplateValidationResult {
        private Long contentId;
        private boolean valid;
        private String generalError;
        private String subjectError;
        private Map<NotificationChannel, String> channelErrors;
    }

    /**
     * Template variables analysis
     */
    @lombok.Data
    @lombok.Builder
    public static class TemplateVariablesAnalysis {
        private Long contentId;
        private java.util.Set<String> allVariables;
        private Map<NotificationChannel, java.util.Set<String>> channelVariables;
        private java.util.Set<String> subjectVariables;
        private int totalVariableCount;
        private String error;
    }
}
