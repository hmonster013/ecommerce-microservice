package org.de013.notificationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.service.TemplatePreviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Template Preview
 */
@RestController
@RequestMapping("/api/v1/template-preview")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Template Preview", description = "Preview and validate templates with sample data")
public class TemplatePreviewController {

    private final TemplatePreviewService templatePreviewService;

    /**
     * Preview template content
     */
    @PostMapping("/content/{contentId}")
    @Operation(summary = "Preview template content", description = "Preview template content with sample data for specific channel")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Template preview generated successfully"),
        @ApiResponse(responseCode = "404", description = "Template content not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplatePreviewResult>> previewTemplate(
            @Parameter(description = "Content ID") @PathVariable Long contentId,
            @Parameter(description = "Notification channel") @RequestParam NotificationChannel channel,
            @RequestBody(required = false) Map<String, Object> sampleData) {
        
        log.info("Previewing template content: contentId={}, channel={}", contentId, channel);

        try {
            TemplatePreviewService.TemplatePreviewResult result = 
                    templatePreviewService.previewTemplate(contentId, channel, sampleData);
            
            org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplatePreviewResult> response = 
                    org.de013.common.dto.ApiResponse.success(result);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error previewing template: contentId={}, channel={}, error={}", 
                    contentId, channel, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplatePreviewResult> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to preview template: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Preview localized template
     */
    @PostMapping("/template/{templateId}/localized")
    @Operation(summary = "Preview localized template", description = "Preview localized template with sample data")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Localized template preview generated successfully"),
        @ApiResponse(responseCode = "404", description = "Template or localization not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplatePreviewResult>> previewLocalizedTemplate(
            @Parameter(description = "Template ID") @PathVariable Long templateId,
            @Parameter(description = "Language code") @RequestParam String language,
            @Parameter(description = "Notification channel") @RequestParam NotificationChannel channel,
            @RequestBody(required = false) Map<String, Object> sampleData) {
        
        log.info("Previewing localized template: templateId={}, language={}, channel={}", 
                templateId, language, channel);

        try {
            TemplatePreviewService.TemplatePreviewResult result = 
                    templatePreviewService.previewLocalizedTemplate(templateId, language, channel, sampleData);
            
            org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplatePreviewResult> response = 
                    org.de013.common.dto.ApiResponse.success(result);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error previewing localized template: templateId={}, language={}, error={}", 
                    templateId, language, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplatePreviewResult> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to preview localized template: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Preview template for multiple channels
     */
    @PostMapping("/content/{contentId}/multi-channel")
    @Operation(summary = "Preview multi-channel template", description = "Preview template for all notification channels")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Multi-channel template preview generated successfully"),
        @ApiResponse(responseCode = "404", description = "Template content not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Map<NotificationChannel, TemplatePreviewService.TemplatePreviewResult>>> previewMultiChannel(
            @Parameter(description = "Content ID") @PathVariable Long contentId,
            @RequestBody(required = false) Map<String, Object> sampleData) {
        
        log.info("Previewing multi-channel template: contentId={}", contentId);

        try {
            Map<NotificationChannel, TemplatePreviewService.TemplatePreviewResult> results = 
                    templatePreviewService.previewMultiChannel(contentId, sampleData);
            
            org.de013.common.dto.ApiResponse<Map<NotificationChannel, TemplatePreviewService.TemplatePreviewResult>> response = 
                    org.de013.common.dto.ApiResponse.success(results);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error previewing multi-channel template: contentId={}, error={}", 
                    contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<Map<NotificationChannel, TemplatePreviewService.TemplatePreviewResult>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to preview multi-channel template: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Validate template syntax
     */
    @PostMapping("/content/{contentId}/validate")
    @Operation(summary = "Validate template", description = "Validate template syntax and structure")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Template validation completed"),
        @ApiResponse(responseCode = "404", description = "Template content not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplateValidationResult>> validateTemplate(
            @Parameter(description = "Content ID") @PathVariable Long contentId) {
        
        log.info("Validating template: contentId={}", contentId);

        try {
            TemplatePreviewService.TemplateValidationResult result = 
                    templatePreviewService.validateTemplate(contentId);
            
            org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplateValidationResult> response = 
                    org.de013.common.dto.ApiResponse.success(result);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error validating template: contentId={}, error={}", contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplateValidationResult> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to validate template: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Analyze template variables
     */
    @GetMapping("/content/{contentId}/variables")
    @Operation(summary = "Analyze template variables", description = "Analyze variables used in template content")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Template variables analyzed successfully"),
        @ApiResponse(responseCode = "404", description = "Template content not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplateVariablesAnalysis>> analyzeTemplateVariables(
            @Parameter(description = "Content ID") @PathVariable Long contentId) {
        
        log.info("Analyzing template variables: contentId={}", contentId);

        try {
            TemplatePreviewService.TemplateVariablesAnalysis result = 
                    templatePreviewService.analyzeTemplateVariables(contentId);
            
            org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplateVariablesAnalysis> response = 
                    org.de013.common.dto.ApiResponse.success(result);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error analyzing template variables: contentId={}, error={}", contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplatePreviewService.TemplateVariablesAnalysis> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to analyze template variables: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get sample data for preview
     */
    @GetMapping("/sample-data")
    @Operation(summary = "Get sample data", description = "Get default sample data for template preview")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Sample data retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Map<String, Object>>> getSampleData() {
        log.info("Getting sample data for template preview");

        try {
            // Create sample data similar to what's used in the service
            Map<String, Object> sampleData = new java.util.HashMap<>();
            
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
            
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.success(sampleData);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting sample data: {}", e.getMessage(), e);
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get sample data: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Test template with custom data
     */
    @PostMapping("/content/{contentId}/test")
    @Operation(summary = "Test template", description = "Test template with custom data and return detailed results")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Template test completed"),
        @ApiResponse(responseCode = "404", description = "Template content not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Map<String, Object>>> testTemplate(
            @Parameter(description = "Content ID") @PathVariable Long contentId,
            @RequestBody Map<String, Object> testData) {
        
        log.info("Testing template with custom data: contentId={}", contentId);

        try {
            Map<String, Object> results = new java.util.HashMap<>();
            
            // Validate template
            TemplatePreviewService.TemplateValidationResult validation = 
                    templatePreviewService.validateTemplate(contentId);
            results.put("validation", validation);
            
            // Analyze variables
            TemplatePreviewService.TemplateVariablesAnalysis variablesAnalysis = 
                    templatePreviewService.analyzeTemplateVariables(contentId);
            results.put("variables_analysis", variablesAnalysis);
            
            // Preview for all channels
            Map<NotificationChannel, TemplatePreviewService.TemplatePreviewResult> previews = 
                    templatePreviewService.previewMultiChannel(contentId, testData);
            results.put("channel_previews", previews);
            
            // Test summary
            Map<String, Object> summary = new java.util.HashMap<>();
            summary.put("is_valid", validation.isValid());
            summary.put("total_variables", variablesAnalysis.getTotalVariableCount());
            summary.put("successful_channels", previews.values().stream().mapToLong(p -> p.isSuccess() ? 1 : 0).sum());
            summary.put("failed_channels", previews.values().stream().mapToLong(p -> p.isSuccess() ? 0 : 1).sum());
            results.put("summary", summary);
            
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.success(results);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error testing template: contentId={}, error={}", contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to test template: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
