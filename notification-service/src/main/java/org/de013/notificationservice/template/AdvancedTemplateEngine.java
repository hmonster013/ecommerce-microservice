package org.de013.notificationservice.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced Template Engine with dynamic rendering, conditional blocks, loops, and inheritance
 */
@Component
@Slf4j
public class AdvancedTemplateEngine {

    private final TemplateEngine thymeleafEngine;

    public AdvancedTemplateEngine(@Qualifier("customTemplateEngine") TemplateEngine thymeleafEngine) {
        this.thymeleafEngine = thymeleafEngine;
    }
    
    // Patterns for custom template syntax
    private static final Pattern IF_PATTERN = Pattern.compile("\\{\\{#if\\s+([^}]+)\\}\\}(.*?)\\{\\{/if\\}\\}", Pattern.DOTALL);
    private static final Pattern UNLESS_PATTERN = Pattern.compile("\\{\\{#unless\\s+([^}]+)\\}\\}(.*?)\\{\\{/unless\\}\\}", Pattern.DOTALL);
    private static final Pattern EACH_PATTERN = Pattern.compile("\\{\\{#each\\s+([^}]+)\\}\\}(.*?)\\{\\{/each\\}\\}", Pattern.DOTALL);
    private static final Pattern WITH_PATTERN = Pattern.compile("\\{\\{#with\\s+([^}]+)\\}\\}(.*?)\\{\\{/with\\}\\}", Pattern.DOTALL);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    private static final Pattern EXTENDS_PATTERN = Pattern.compile("\\{\\{extends\\s+['\"]([^'\"]+)['\"]\\}\\}");
    private static final Pattern BLOCK_PATTERN = Pattern.compile("\\{\\{#block\\s+([^}]+)\\}\\}(.*?)\\{\\{/block\\}\\}", Pattern.DOTALL);
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\{\\{include\\s+['\"]([^'\"]+)['\"]\\}\\}");

    /**
     * Process template with advanced features
     */
    public String processTemplate(String templateContent, Map<String, Object> variables) {
        return processTemplate(templateContent, variables, Locale.getDefault());
    }

    /**
     * Process template with locale support
     */
    public String processTemplate(String templateContent, Map<String, Object> variables, Locale locale) {
        log.debug("Processing template with {} variables, locale: {}", variables.size(), locale);

        try {
            // Step 1: Handle template inheritance
            templateContent = processInheritance(templateContent, variables);
            
            // Step 2: Handle includes
            templateContent = processIncludes(templateContent, variables);
            
            // Step 3: Process conditional blocks
            templateContent = processConditionals(templateContent, variables);
            
            // Step 4: Process loops
            templateContent = processLoops(templateContent, variables);
            
            // Step 5: Process with blocks
            templateContent = processWithBlocks(templateContent, variables);
            
            // Step 6: Process variables
            templateContent = processVariables(templateContent, variables);
            
            // Step 7: Final Thymeleaf processing for advanced features
            templateContent = processWithThymeleaf(templateContent, variables, locale);
            
            log.debug("Template processed successfully");
            return templateContent;
            
        } catch (Exception e) {
            log.error("Error processing template: {}", e.getMessage(), e);
            throw new TemplateProcessingException("Failed to process template: " + e.getMessage(), e);
        }
    }

    /**
     * Process template inheritance
     */
    private String processInheritance(String templateContent, Map<String, Object> variables) {
        Matcher extendsMatcher = EXTENDS_PATTERN.matcher(templateContent);
        if (extendsMatcher.find()) {
            String parentTemplateName = extendsMatcher.group(1);
            log.debug("Processing template inheritance: extending {}", parentTemplateName);
            
            // Extract blocks from child template
            Map<String, String> childBlocks = extractBlocks(templateContent);
            
            // Load parent template (this would typically load from database or file system)
            String parentTemplate = loadTemplate(parentTemplateName);
            
            // Replace blocks in parent template with child blocks
            String result = replaceBlocks(parentTemplate, childBlocks);
            
            // Remove extends directive
            result = extendsMatcher.replaceFirst("");
            
            return result;
        }
        return templateContent;
    }

    /**
     * Process includes
     */
    private String processIncludes(String templateContent, Map<String, Object> variables) {
        Matcher includeMatcher = INCLUDE_PATTERN.matcher(templateContent);
        StringBuffer result = new StringBuffer();
        
        while (includeMatcher.find()) {
            String includeTemplateName = includeMatcher.group(1);
            log.debug("Processing include: {}", includeTemplateName);
            
            String includeContent = loadTemplate(includeTemplateName);
            // Recursively process the included template
            includeContent = processTemplate(includeContent, variables);
            
            includeMatcher.appendReplacement(result, Matcher.quoteReplacement(includeContent));
        }
        includeMatcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * Process conditional blocks (if/unless)
     */
    private String processConditionals(String templateContent, Map<String, Object> variables) {
        // Process if blocks
        templateContent = processIfBlocks(templateContent, variables);
        
        // Process unless blocks
        templateContent = processUnlessBlocks(templateContent, variables);
        
        return templateContent;
    }

    /**
     * Process if blocks
     */
    private String processIfBlocks(String templateContent, Map<String, Object> variables) {
        Matcher ifMatcher = IF_PATTERN.matcher(templateContent);
        StringBuffer result = new StringBuffer();
        
        while (ifMatcher.find()) {
            String condition = ifMatcher.group(1).trim();
            String content = ifMatcher.group(2);
            
            log.debug("Processing if condition: {}", condition);
            
            if (evaluateCondition(condition, variables)) {
                // Recursively process the content inside the if block
                content = processTemplate(content, variables);
                ifMatcher.appendReplacement(result, Matcher.quoteReplacement(content));
            } else {
                ifMatcher.appendReplacement(result, "");
            }
        }
        ifMatcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * Process unless blocks
     */
    private String processUnlessBlocks(String templateContent, Map<String, Object> variables) {
        Matcher unlessMatcher = UNLESS_PATTERN.matcher(templateContent);
        StringBuffer result = new StringBuffer();
        
        while (unlessMatcher.find()) {
            String condition = unlessMatcher.group(1).trim();
            String content = unlessMatcher.group(2);
            
            log.debug("Processing unless condition: {}", condition);
            
            if (!evaluateCondition(condition, variables)) {
                // Recursively process the content inside the unless block
                content = processTemplate(content, variables);
                unlessMatcher.appendReplacement(result, Matcher.quoteReplacement(content));
            } else {
                unlessMatcher.appendReplacement(result, "");
            }
        }
        unlessMatcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * Process loops (each blocks)
     */
    private String processLoops(String templateContent, Map<String, Object> variables) {
        Matcher eachMatcher = EACH_PATTERN.matcher(templateContent);
        StringBuffer result = new StringBuffer();
        
        while (eachMatcher.find()) {
            String iterationExpression = eachMatcher.group(1).trim();
            String loopContent = eachMatcher.group(2);
            
            log.debug("Processing each loop: {}", iterationExpression);
            
            String loopResult = processLoop(iterationExpression, loopContent, variables);
            eachMatcher.appendReplacement(result, Matcher.quoteReplacement(loopResult));
        }
        eachMatcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * Process with blocks
     */
    private String processWithBlocks(String templateContent, Map<String, Object> variables) {
        Matcher withMatcher = WITH_PATTERN.matcher(templateContent);
        StringBuffer result = new StringBuffer();
        
        while (withMatcher.find()) {
            String contextExpression = withMatcher.group(1).trim();
            String content = withMatcher.group(2);
            
            log.debug("Processing with block: {}", contextExpression);
            
            Object contextValue = getVariableValue(contextExpression, variables);
            if (contextValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> contextMap = (Map<String, Object>) contextValue;
                Map<String, Object> newVariables = new HashMap<>(variables);
                newVariables.putAll(contextMap);
                
                content = processTemplate(content, newVariables);
            }
            
            withMatcher.appendReplacement(result, Matcher.quoteReplacement(content));
        }
        withMatcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * Process variables
     */
    private String processVariables(String templateContent, Map<String, Object> variables) {
        Matcher variableMatcher = VARIABLE_PATTERN.matcher(templateContent);
        StringBuffer result = new StringBuffer();
        
        while (variableMatcher.find()) {
            String variableExpression = variableMatcher.group(1).trim();
            
            // Skip if it's a block directive
            if (variableExpression.startsWith("#") || variableExpression.startsWith("/")) {
                continue;
            }
            
            Object value = getVariableValue(variableExpression, variables);
            String stringValue = value != null ? value.toString() : "";
            
            variableMatcher.appendReplacement(result, Matcher.quoteReplacement(stringValue));
        }
        variableMatcher.appendTail(result);
        
        return result.toString();
    }

    /**
     * Process with Thymeleaf for advanced features
     */
    private String processWithThymeleaf(String templateContent, Map<String, Object> variables, Locale locale) {
        try {
            Context context = new Context(locale);
            context.setVariables(variables);
            
            // Convert our custom syntax to Thymeleaf syntax for advanced features
            templateContent = convertToThymeleafSyntax(templateContent);
            
            return thymeleafEngine.process(templateContent, context);
            
        } catch (Exception e) {
            log.warn("Thymeleaf processing failed, returning processed template: {}", e.getMessage());
            return templateContent;
        }
    }

    /**
     * Convert custom syntax to Thymeleaf syntax
     */
    private String convertToThymeleafSyntax(String templateContent) {
        // Convert remaining custom syntax to Thymeleaf equivalents
        // This is a simplified conversion - in practice, you'd have more sophisticated conversion
        return templateContent;
    }

    /**
     * Evaluate condition
     */
    private boolean evaluateCondition(String condition, Map<String, Object> variables) {
        try {
            // Handle simple conditions
            if (condition.contains("==")) {
                String[] parts = condition.split("==");
                if (parts.length == 2) {
                    Object left = getVariableValue(parts[0].trim(), variables);
                    Object right = getVariableValue(parts[1].trim(), variables);
                    return Objects.equals(left, right);
                }
            }
            
            if (condition.contains("!=")) {
                String[] parts = condition.split("!=");
                if (parts.length == 2) {
                    Object left = getVariableValue(parts[0].trim(), variables);
                    Object right = getVariableValue(parts[1].trim(), variables);
                    return !Objects.equals(left, right);
                }
            }
            
            // Handle simple boolean conditions
            Object value = getVariableValue(condition, variables);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            
            // Truthy evaluation
            return value != null && !value.toString().isEmpty() && !"0".equals(value.toString());
            
        } catch (Exception e) {
            log.warn("Error evaluating condition '{}': {}", condition, e.getMessage());
            return false;
        }
    }

    /**
     * Process loop iteration
     */
    private String processLoop(String iterationExpression, String loopContent, Map<String, Object> variables) {
        try {
            // Parse iteration expression (e.g., "item in items" or "items")
            String[] parts = iterationExpression.split("\\s+in\\s+");
            String itemName = parts.length > 1 ? parts[0].trim() : "item";
            String collectionName = parts.length > 1 ? parts[1].trim() : iterationExpression.trim();
            
            Object collection = getVariableValue(collectionName, variables);
            if (collection == null) {
                return "";
            }
            
            StringBuilder result = new StringBuilder();
            
            if (collection instanceof Iterable) {
                int index = 0;
                for (Object item : (Iterable<?>) collection) {
                    Map<String, Object> loopVariables = new HashMap<>(variables);
                    loopVariables.put(itemName, item);
                    loopVariables.put("@index", index);
                    loopVariables.put("@first", index == 0);
                    
                    String processedContent = processTemplate(loopContent, loopVariables);
                    result.append(processedContent);
                    index++;
                }
                
                // Add @last variable
                if (index > 0) {
                    String lastProcessedContent = result.toString();
                    // This is simplified - in practice, you'd need to track the last iteration
                }
            }
            
            return result.toString();
            
        } catch (Exception e) {
            log.error("Error processing loop: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * Get variable value with dot notation support
     */
    private Object getVariableValue(String expression, Map<String, Object> variables) {
        try {
            // Remove quotes if present
            if ((expression.startsWith("'") && expression.endsWith("'")) ||
                (expression.startsWith("\"") && expression.endsWith("\""))) {
                return expression.substring(1, expression.length() - 1);
            }
            
            // Handle dot notation (e.g., user.name)
            String[] parts = expression.split("\\.");
            Object current = variables.get(parts[0]);
            
            for (int i = 1; i < parts.length && current != null; i++) {
                if (current instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) current;
                    current = map.get(parts[i]);
                } else {
                    // Use reflection to get property value
                    current = getPropertyValue(current, parts[i]);
                }
            }
            
            return current;
            
        } catch (Exception e) {
            log.warn("Error getting variable value for '{}': {}", expression, e.getMessage());
            return null;
        }
    }

    /**
     * Get property value using reflection
     */
    private Object getPropertyValue(Object object, String propertyName) {
        try {
            String getterName = "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
            return object.getClass().getMethod(getterName).invoke(object);
        } catch (Exception e) {
            log.debug("Could not get property '{}' from object: {}", propertyName, e.getMessage());
            return null;
        }
    }

    /**
     * Extract blocks from template
     */
    private Map<String, String> extractBlocks(String templateContent) {
        Map<String, String> blocks = new HashMap<>();
        Matcher blockMatcher = BLOCK_PATTERN.matcher(templateContent);
        
        while (blockMatcher.find()) {
            String blockName = blockMatcher.group(1).trim();
            String blockContent = blockMatcher.group(2);
            blocks.put(blockName, blockContent);
        }
        
        return blocks;
    }

    /**
     * Replace blocks in parent template
     */
    private String replaceBlocks(String parentTemplate, Map<String, String> childBlocks) {
        for (Map.Entry<String, String> entry : childBlocks.entrySet()) {
            String blockName = entry.getKey();
            String blockContent = entry.getValue();
            
            String blockPattern = "\\{\\{#block\\s+" + Pattern.quote(blockName) + "\\}\\}.*?\\{\\{/block\\}\\}";
            parentTemplate = parentTemplate.replaceAll(blockPattern, 
                    Matcher.quoteReplacement("{{#block " + blockName + "}}" + blockContent + "{{/block}}"));
        }
        
        return parentTemplate;
    }

    /**
     * Load template (placeholder implementation)
     */
    private String loadTemplate(String templateName) {
        // In a real implementation, this would load from database or file system
        log.debug("Loading template: {}", templateName);
        return "<!-- Template: " + templateName + " not found -->";
    }

    /**
     * Template processing exception
     */
    public static class TemplateProcessingException extends RuntimeException {
        public TemplateProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
