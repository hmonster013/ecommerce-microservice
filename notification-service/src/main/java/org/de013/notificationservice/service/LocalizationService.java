package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.TemplateContent;
import org.de013.notificationservice.entity.enums.ContentStatus;
import org.de013.notificationservice.repository.TemplateContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing localization and multi-language support
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LocalizationService {

    private final TemplateContentRepository templateContentRepository;
    private final TranslationService translationService;

    // Supported languages with fallback hierarchy
    private static final Map<String, List<String>> LANGUAGE_FALLBACKS = createLanguageFallbacks();

    private static final String DEFAULT_LANGUAGE = "en";

    /**
     * Create language fallbacks map
     */
    private static Map<String, List<String>> createLanguageFallbacks() {
        Map<String, List<String>> fallbacks = new HashMap<>();
        fallbacks.put("en", List.of("en"));
        fallbacks.put("vi", List.of("vi", "en"));
        fallbacks.put("fr", List.of("fr", "en"));
        fallbacks.put("es", List.of("es", "en"));
        fallbacks.put("de", List.of("de", "en"));
        fallbacks.put("ja", List.of("ja", "en"));
        fallbacks.put("ko", List.of("ko", "en"));
        fallbacks.put("zh", List.of("zh", "en"));
        fallbacks.put("pt", List.of("pt", "en"));
        fallbacks.put("it", List.of("it", "en"));
        fallbacks.put("ru", List.of("ru", "en"));
        fallbacks.put("ar", List.of("ar", "en"));
        return fallbacks;
    }

    /**
     * Get localized content for template
     */
    public TemplateContent getLocalizedContent(Long templateId, String languageCode) {
        return getLocalizedContent(templateId, languageCode, null);
    }

    /**
     * Get localized content with country code
     */
    public TemplateContent getLocalizedContent(Long templateId, String languageCode, String countryCode) {
        log.debug("Getting localized content: templateId={}, language={}, country={}", 
                templateId, languageCode, countryCode);

        try {
            // Try exact match first (language + country)
            if (countryCode != null) {
                Optional<TemplateContent> exactMatch = templateContentRepository
                        .findByTemplateIdAndLanguageCodeAndCountryCodeAndStatusAndDeletedFalse(
                                templateId, languageCode, countryCode, ContentStatus.PUBLISHED);
                if (exactMatch.isPresent()) {
                    log.debug("Found exact match: templateId={}, language={}, country={}", 
                            templateId, languageCode, countryCode);
                    return exactMatch.get();
                }
            }

            // Try language only
            Optional<TemplateContent> languageMatch = templateContentRepository
                    .findByTemplateIdAndLanguageCodeAndStatusAndDeletedFalse(
                            templateId, languageCode, ContentStatus.PUBLISHED);
            if (languageMatch.isPresent()) {
                log.debug("Found language match: templateId={}, language={}", templateId, languageCode);
                return languageMatch.get();
            }

            // Try fallback languages
            List<String> fallbacks = LANGUAGE_FALLBACKS.getOrDefault(languageCode, List.of(DEFAULT_LANGUAGE));
            for (String fallbackLang : fallbacks) {
                if (!fallbackLang.equals(languageCode)) {
                    Optional<TemplateContent> fallbackMatch = templateContentRepository
                            .findByTemplateIdAndLanguageCodeAndStatusAndDeletedFalse(
                                    templateId, fallbackLang, ContentStatus.PUBLISHED);
                    if (fallbackMatch.isPresent()) {
                        log.debug("Found fallback match: templateId={}, requestedLanguage={}, fallbackLanguage={}", 
                                templateId, languageCode, fallbackLang);
                        return fallbackMatch.get();
                    }
                }
            }

            // Try default content
            Optional<TemplateContent> defaultContent = templateContentRepository
                    .findByTemplateIdAndIsDefaultTrueAndStatusAndDeletedFalse(templateId, ContentStatus.PUBLISHED);
            if (defaultContent.isPresent()) {
                log.debug("Found default content: templateId={}", templateId);
                return defaultContent.get();
            }

            throw new LocalizationException("No localized content found for template: " + templateId);

        } catch (Exception e) {
            log.error("Error getting localized content: templateId={}, language={}, error={}", 
                    templateId, languageCode, e.getMessage(), e);
            throw new LocalizationException("Failed to get localized content: " + e.getMessage(), e);
        }
    }

    /**
     * Detect language from user preferences or request
     */
    public String detectLanguage(String userLanguage, String acceptLanguage, String userAgent) {
        log.debug("Detecting language: userLanguage={}, acceptLanguage={}, userAgent={}", 
                userLanguage, acceptLanguage, userAgent);

        try {
            // Priority 1: User's explicit language preference
            if (userLanguage != null && isLanguageSupported(userLanguage)) {
                log.debug("Using user language preference: {}", userLanguage);
                return userLanguage;
            }

            // Priority 2: Accept-Language header
            if (acceptLanguage != null) {
                String detectedLanguage = parseAcceptLanguage(acceptLanguage);
                if (detectedLanguage != null && isLanguageSupported(detectedLanguage)) {
                    log.debug("Using Accept-Language header: {}", detectedLanguage);
                    return detectedLanguage;
                }
            }

            // Priority 3: User-Agent based detection (simplified)
            if (userAgent != null) {
                String detectedLanguage = detectLanguageFromUserAgent(userAgent);
                if (detectedLanguage != null && isLanguageSupported(detectedLanguage)) {
                    log.debug("Using User-Agent detection: {}", detectedLanguage);
                    return detectedLanguage;
                }
            }

            // Default to English
            log.debug("Using default language: {}", DEFAULT_LANGUAGE);
            return DEFAULT_LANGUAGE;

        } catch (Exception e) {
            log.error("Error detecting language: {}", e.getMessage(), e);
            return DEFAULT_LANGUAGE;
        }
    }

    /**
     * Get available languages for template
     */
    public List<String> getAvailableLanguages(Long templateId) {
        try {
            return templateContentRepository.findDistinctLanguageCodesByTemplateIdAndStatusAndDeletedFalse(
                    templateId, ContentStatus.PUBLISHED);
        } catch (Exception e) {
            log.error("Error getting available languages: templateId={}, error={}", templateId, e.getMessage(), e);
            return List.of(DEFAULT_LANGUAGE);
        }
    }

    /**
     * Get missing translations for template
     */
    public List<String> getMissingTranslations(Long templateId) {
        try {
            List<String> availableLanguages = getAvailableLanguages(templateId);
            List<String> supportedLanguages = getSupportedLanguages();
            
            return supportedLanguages.stream()
                    .filter(lang -> !availableLanguages.contains(lang))
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error getting missing translations: templateId={}, error={}", templateId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Create translation for template content
     */
    @Transactional
    public TemplateContent createTranslation(Long sourceContentId, String targetLanguage, String targetCountry) {
        log.info("Creating translation: sourceContentId={}, targetLanguage={}, targetCountry={}", 
                sourceContentId, targetLanguage, targetCountry);

        try {
            TemplateContent sourceContent = templateContentRepository.findByIdAndDeletedFalse(sourceContentId)
                    .orElseThrow(() -> new LocalizationException("Source content not found: " + sourceContentId));

            // Check if translation already exists
            Optional<TemplateContent> existingTranslation = templateContentRepository
                    .findByTemplateIdAndLanguageCodeAndCountryCodeAndDeletedFalse(
                            sourceContent.getTemplateId(), targetLanguage, targetCountry);
            
            if (existingTranslation.isPresent()) {
                throw new LocalizationException("Translation already exists for language: " + targetLanguage);
            }

            // Create new translation
            TemplateContent translation = TemplateContent.builder()
                    .templateId(sourceContent.getTemplateId())
                    .languageCode(targetLanguage)
                    .countryCode(targetCountry)
                    .contentVersion(1)
                    .title(sourceContent.getTitle())
                    .subject(sourceContent.getSubject())
                    .content(sourceContent.getContent())
                    .htmlContent(sourceContent.getHtmlContent())
                    .plainTextContent(sourceContent.getPlainTextContent())
                    .status(ContentStatus.DRAFT)
                    .isDefault(false)
                    .isFallback(false)
                    .mediaAttachments(new ArrayList<>(sourceContent.getMediaAttachments()))
                    .contentBlocks(new HashMap<>(sourceContent.getContentBlocks()))
                    .variables(new HashMap<>(sourceContent.getVariables()))
                    .metadata(new HashMap<>(sourceContent.getMetadata()))
                    .createdBy(sourceContent.getCreatedBy())
                    .altText(sourceContent.getAltText())
                    .metaDescription(sourceContent.getMetaDescription())
                    .keywords(sourceContent.getKeywords())
                    .build();

            // Auto-translate content if translation service is available
            if (translationService != null) {
                translation = autoTranslateContent(translation, sourceContent.getLanguageCode(), targetLanguage);
            }

            TemplateContent savedTranslation = templateContentRepository.save(translation);

            log.info("Translation created successfully: id={}, targetLanguage={}", 
                    savedTranslation.getId(), targetLanguage);
            return savedTranslation;

        } catch (Exception e) {
            log.error("Error creating translation: sourceContentId={}, targetLanguage={}, error={}", 
                    sourceContentId, targetLanguage, e.getMessage(), e);
            throw new LocalizationException("Failed to create translation: " + e.getMessage(), e);
        }
    }

    /**
     * Auto-translate content using translation service
     */
    private TemplateContent autoTranslateContent(TemplateContent content, String sourceLanguage, String targetLanguage) {
        try {
            if (content.getTitle() != null) {
                content.setTitle(translationService.translate(content.getTitle(), sourceLanguage, targetLanguage));
            }
            
            if (content.getSubject() != null) {
                content.setSubject(translationService.translate(content.getSubject(), sourceLanguage, targetLanguage));
            }
            
            if (content.getContent() != null) {
                content.setContent(translationService.translate(content.getContent(), sourceLanguage, targetLanguage));
            }
            
            if (content.getPlainTextContent() != null) {
                content.setPlainTextContent(translationService.translate(content.getPlainTextContent(), sourceLanguage, targetLanguage));
            }
            
            if (content.getAltText() != null) {
                content.setAltText(translationService.translate(content.getAltText(), sourceLanguage, targetLanguage));
            }
            
            if (content.getMetaDescription() != null) {
                content.setMetaDescription(translationService.translate(content.getMetaDescription(), sourceLanguage, targetLanguage));
            }

            // Mark as auto-translated
            content.setMetadata("auto_translated", true);
            content.setMetadata("source_language", sourceLanguage);
            content.setMetadata("translation_date", java.time.LocalDateTime.now().toString());

            log.debug("Content auto-translated: sourceLanguage={}, targetLanguage={}", sourceLanguage, targetLanguage);

        } catch (Exception e) {
            log.warn("Auto-translation failed: sourceLanguage={}, targetLanguage={}, error={}", 
                    sourceLanguage, targetLanguage, e.getMessage());
            // Continue without translation
        }

        return content;
    }

    /**
     * Parse Accept-Language header
     */
    private String parseAcceptLanguage(String acceptLanguage) {
        try {
            // Parse Accept-Language header (e.g., "en-US,en;q=0.9,vi;q=0.8")
            String[] languages = acceptLanguage.split(",");
            
            for (String lang : languages) {
                String cleanLang = lang.split(";")[0].trim();
                String langCode = cleanLang.split("-")[0].toLowerCase();
                
                if (isLanguageSupported(langCode)) {
                    return langCode;
                }
            }
            
        } catch (Exception e) {
            log.debug("Error parsing Accept-Language header: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Detect language from User-Agent (simplified)
     */
    private String detectLanguageFromUserAgent(String userAgent) {
        // This is a very simplified implementation
        // In practice, you'd use more sophisticated detection
        if (userAgent.toLowerCase().contains("zh")) return "zh";
        if (userAgent.toLowerCase().contains("ja")) return "ja";
        if (userAgent.toLowerCase().contains("ko")) return "ko";
        return null;
    }

    /**
     * Check if language is supported
     */
    public boolean isLanguageSupported(String languageCode) {
        return LANGUAGE_FALLBACKS.containsKey(languageCode.toLowerCase());
    }

    /**
     * Get supported languages
     */
    public List<String> getSupportedLanguages() {
        return new ArrayList<>(LANGUAGE_FALLBACKS.keySet());
    }

    /**
     * Get language fallback chain
     */
    public List<String> getLanguageFallbacks(String languageCode) {
        return LANGUAGE_FALLBACKS.getOrDefault(languageCode.toLowerCase(), List.of(DEFAULT_LANGUAGE));
    }

    /**
     * Get localization statistics
     */
    public Map<String, Object> getLocalizationStatistics(Long templateId) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            List<String> availableLanguages = getAvailableLanguages(templateId);
            List<String> missingLanguages = getMissingTranslations(templateId);
            List<String> supportedLanguages = getSupportedLanguages();
            
            stats.put("template_id", templateId);
            stats.put("available_languages", availableLanguages);
            stats.put("missing_languages", missingLanguages);
            stats.put("supported_languages", supportedLanguages);
            stats.put("coverage_percentage", (double) availableLanguages.size() / supportedLanguages.size() * 100);
            stats.put("total_translations", availableLanguages.size());
            stats.put("missing_translations", missingLanguages.size());
            
        } catch (Exception e) {
            log.error("Error getting localization statistics: templateId={}, error={}", templateId, e.getMessage(), e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    /**
     * Localization exception
     */
    public static class LocalizationException extends RuntimeException {
        public LocalizationException(String message) {
            super(message);
        }

        public LocalizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
