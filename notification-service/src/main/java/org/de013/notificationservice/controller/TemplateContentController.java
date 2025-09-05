package org.de013.notificationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.TemplateContent;
import org.de013.notificationservice.entity.enums.ContentStatus;
import org.de013.notificationservice.service.ContentManagementService;
import org.de013.notificationservice.service.LocalizationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Template Content Management
 */
@RestController
@RequestMapping("/api/v1/template-contents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Template Content", description = "Manage template content with rich text, media, and localization")
public class TemplateContentController {

    private final ContentManagementService contentManagementService;
    private final LocalizationService localizationService;

    /**
     * Create new template content
     */
    @PostMapping
    @Operation(summary = "Create template content", description = "Create new template content with rich text and media")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Template content created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplateContent>> createContent(
            @RequestBody TemplateContent content) {
        
        log.info("Creating template content: templateId={}, language={}", 
                content.getTemplateId(), content.getLanguageCode());

        try {
            TemplateContent createdContent = contentManagementService.createContent(content);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.success(createdContent);
            
            return ResponseEntity.status(201).body(response);
            
        } catch (Exception e) {
            log.error("Error creating template content: {}", e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to create template content: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get template content by ID
     */
    @GetMapping("/{contentId}")
    @Operation(summary = "Get template content", description = "Get template content by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Template content retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Template content not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplateContent>> getContent(
            @Parameter(description = "Content ID") @PathVariable Long contentId) {
        
        log.info("Getting template content: id={}", contentId);

        try {
            TemplateContent content = contentManagementService.getContentById(contentId);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.success(content);
            
            return ResponseEntity.ok(response);
            
        } catch (ContentManagementService.ContentManagementException e) {
            log.error("Template content not found: id={}", contentId);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error("Template content not found");
            return ResponseEntity.status(404).body(response);
        } catch (Exception e) {
            log.error("Error getting template content: id={}, error={}", contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get template content: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Update template content
     */
    @PutMapping("/{contentId}")
    @Operation(summary = "Update template content", description = "Update existing template content")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Template content updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Template content not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplateContent>> updateContent(
            @Parameter(description = "Content ID") @PathVariable Long contentId,
            @RequestBody TemplateContent content) {
        
        log.info("Updating template content: id={}", contentId);

        try {
            TemplateContent updatedContent = contentManagementService.updateContent(contentId, content);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.success(updatedContent);
            
            return ResponseEntity.ok(response);
            
        } catch (ContentManagementService.ContentManagementException e) {
            log.error("Error updating template content: id={}, error={}", contentId, e.getMessage());
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error updating template content: id={}, error={}", contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to update template content: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get all template contents with pagination
     */
    @GetMapping
    @Operation(summary = "Get all template contents", description = "Get all template contents with pagination")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Template contents retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Page<TemplateContent>>> getAllContents(
            Pageable pageable) {
        
        log.info("Getting all template contents: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        try {
            Page<TemplateContent> contents = contentManagementService.getAllContents(pageable);
            org.de013.common.dto.ApiResponse<Page<TemplateContent>> response = 
                    org.de013.common.dto.ApiResponse.success(contents);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting all template contents: {}", e.getMessage(), e);
            org.de013.common.dto.ApiResponse<Page<TemplateContent>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get template contents: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get template contents by template ID
     */
    @GetMapping("/template/{templateId}")
    @Operation(summary = "Get contents by template ID", description = "Get all contents for a specific template")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Template contents retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<TemplateContent>>> getContentsByTemplateId(
            @Parameter(description = "Template ID") @PathVariable Long templateId) {
        
        log.info("Getting template contents by template ID: templateId={}", templateId);

        try {
            List<TemplateContent> contents = contentManagementService.getContentsByTemplateId(templateId);
            org.de013.common.dto.ApiResponse<List<TemplateContent>> response = 
                    org.de013.common.dto.ApiResponse.success(contents);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting template contents by template ID: templateId={}, error={}", 
                    templateId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<List<TemplateContent>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get template contents: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get localized content
     */
    @GetMapping("/template/{templateId}/localized")
    @Operation(summary = "Get localized content", description = "Get localized content for template with fallback")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Localized content retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "No content found for template")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplateContent>> getLocalizedContent(
            @Parameter(description = "Template ID") @PathVariable Long templateId,
            @Parameter(description = "Language code") @RequestParam String language,
            @Parameter(description = "Country code") @RequestParam(required = false) String country) {
        
        log.info("Getting localized content: templateId={}, language={}, country={}", 
                templateId, language, country);

        try {
            TemplateContent content = localizationService.getLocalizedContent(templateId, language, country);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.success(content);
            
            return ResponseEntity.ok(response);
            
        } catch (LocalizationService.LocalizationException e) {
            log.error("No localized content found: templateId={}, language={}", templateId, language);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error("No localized content found");
            return ResponseEntity.status(404).body(response);
        } catch (Exception e) {
            log.error("Error getting localized content: templateId={}, language={}, error={}", 
                    templateId, language, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get localized content: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Submit content for review
     */
    @PostMapping("/{contentId}/submit-review")
    @Operation(summary = "Submit for review", description = "Submit template content for review")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Content submitted for review successfully"),
        @ApiResponse(responseCode = "400", description = "Content cannot be submitted for review")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplateContent>> submitForReview(
            @Parameter(description = "Content ID") @PathVariable Long contentId,
            @RequestBody Map<String, Long> request) {
        
        log.info("Submitting content for review: id={}", contentId);

        try {
            Long submittedBy = request.get("submittedBy");
            TemplateContent content = contentManagementService.submitForReview(contentId, submittedBy);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.success(content);
            
            return ResponseEntity.ok(response);
            
        } catch (ContentManagementService.ContentManagementException e) {
            log.error("Error submitting content for review: id={}, error={}", contentId, e.getMessage());
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error submitting content for review: id={}, error={}", contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to submit content for review: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Approve content
     */
    @PostMapping("/{contentId}/approve")
    @Operation(summary = "Approve content", description = "Approve template content")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Content approved successfully"),
        @ApiResponse(responseCode = "400", description = "Content cannot be approved")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplateContent>> approveContent(
            @Parameter(description = "Content ID") @PathVariable Long contentId,
            @RequestBody Map<String, Object> request) {
        
        log.info("Approving content: id={}", contentId);

        try {
            Long approvedBy = ((Number) request.get("approvedBy")).longValue();
            String comments = (String) request.get("comments");
            
            TemplateContent content = contentManagementService.approveContent(contentId, approvedBy, comments);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.success(content);
            
            return ResponseEntity.ok(response);
            
        } catch (ContentManagementService.ContentManagementException e) {
            log.error("Error approving content: id={}, error={}", contentId, e.getMessage());
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error approving content: id={}, error={}", contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to approve content: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Reject content
     */
    @PostMapping("/{contentId}/reject")
    @Operation(summary = "Reject content", description = "Reject template content")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Content rejected successfully"),
        @ApiResponse(responseCode = "400", description = "Content cannot be rejected")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplateContent>> rejectContent(
            @Parameter(description = "Content ID") @PathVariable Long contentId,
            @RequestBody Map<String, Object> request) {
        
        log.info("Rejecting content: id={}", contentId);

        try {
            Long rejectedBy = ((Number) request.get("rejectedBy")).longValue();
            String reason = (String) request.get("reason");
            
            TemplateContent content = contentManagementService.rejectContent(contentId, rejectedBy, reason);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.success(content);
            
            return ResponseEntity.ok(response);
            
        } catch (ContentManagementService.ContentManagementException e) {
            log.error("Error rejecting content: id={}, error={}", contentId, e.getMessage());
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error rejecting content: id={}, error={}", contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to reject content: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Publish content
     */
    @PostMapping("/{contentId}/publish")
    @Operation(summary = "Publish content", description = "Publish approved template content")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Content published successfully"),
        @ApiResponse(responseCode = "400", description = "Content cannot be published")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplateContent>> publishContent(
            @Parameter(description = "Content ID") @PathVariable Long contentId) {
        
        log.info("Publishing content: id={}", contentId);

        try {
            TemplateContent content = contentManagementService.publishContent(contentId);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.success(content);
            
            return ResponseEntity.ok(response);
            
        } catch (ContentManagementService.ContentManagementException e) {
            log.error("Error publishing content: id={}, error={}", contentId, e.getMessage());
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error publishing content: id={}, error={}", contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to publish content: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Upload media attachment
     */
    @PostMapping("/{contentId}/media")
    @Operation(summary = "Upload media", description = "Upload media attachment for template content")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Media uploaded successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid file or content")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplateContent.MediaAttachment>> uploadMedia(
            @Parameter(description = "Content ID") @PathVariable Long contentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "altText", required = false) String altText) {
        
        log.info("Uploading media for content: id={}, filename={}", contentId, file.getOriginalFilename());

        try {
            TemplateContent.MediaAttachment attachment = contentManagementService.uploadMedia(contentId, file, altText);
            org.de013.common.dto.ApiResponse<TemplateContent.MediaAttachment> response = 
                    org.de013.common.dto.ApiResponse.success(attachment);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error uploading media: contentId={}, error={}", contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplateContent.MediaAttachment> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to upload media: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Create translation
     */
    @PostMapping("/{contentId}/translate")
    @Operation(summary = "Create translation", description = "Create translation for template content")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Translation created successfully"),
        @ApiResponse(responseCode = "400", description = "Translation already exists or invalid request")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<TemplateContent>> createTranslation(
            @Parameter(description = "Content ID") @PathVariable Long contentId,
            @RequestBody Map<String, String> request) {
        
        log.info("Creating translation for content: id={}", contentId);

        try {
            String targetLanguage = request.get("targetLanguage");
            String targetCountry = request.get("targetCountry");
            
            TemplateContent translation = localizationService.createTranslation(contentId, targetLanguage, targetCountry);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.success(translation);
            
            return ResponseEntity.status(201).body(response);
            
        } catch (LocalizationService.LocalizationException e) {
            log.error("Error creating translation: contentId={}, error={}", contentId, e.getMessage());
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error(e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error creating translation: contentId={}, error={}", contentId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<TemplateContent> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to create translation: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get available languages for template
     */
    @GetMapping("/template/{templateId}/languages")
    @Operation(summary = "Get available languages", description = "Get available languages for template")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Available languages retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<String>>> getAvailableLanguages(
            @Parameter(description = "Template ID") @PathVariable Long templateId) {
        
        log.info("Getting available languages for template: templateId={}", templateId);

        try {
            List<String> languages = localizationService.getAvailableLanguages(templateId);
            org.de013.common.dto.ApiResponse<List<String>> response = 
                    org.de013.common.dto.ApiResponse.success(languages);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting available languages: templateId={}, error={}", templateId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<List<String>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get available languages: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get localization statistics
     */
    @GetMapping("/template/{templateId}/localization-stats")
    @Operation(summary = "Get localization statistics", description = "Get localization statistics for template")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Localization statistics retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Map<String, Object>>> getLocalizationStatistics(
            @Parameter(description = "Template ID") @PathVariable Long templateId) {
        
        log.info("Getting localization statistics for template: templateId={}", templateId);

        try {
            Map<String, Object> stats = localizationService.getLocalizationStatistics(templateId);
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.success(stats);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting localization statistics: templateId={}, error={}", templateId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<Map<String, Object>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get localization statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
