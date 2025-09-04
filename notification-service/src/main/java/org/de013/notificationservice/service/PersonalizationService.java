package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.NotificationPreference;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Service for personalizing notification content and delivery
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalizationService {

    private final NotificationPreferenceService preferenceService;
    private final Random random = new Random();

    /**
     * Personalize notification content based on user preferences
     */
    public Notification personalizeNotification(Notification notification) {
        log.debug("Personalizing notification: id={}, userId={}", notification.getId(), notification.getUserId());

        try {
            NotificationPreference preference = preferenceService.getPreference(
                    notification.getUserId(), 
                    notification.getChannel(), 
                    notification.getType()
            );

            if (preference == null || !Boolean.TRUE.equals(preference.getPersonalizationEnabled())) {
                log.debug("Personalization disabled for user: userId={}", notification.getUserId());
                return notification;
            }

            // Apply timezone-based personalization
            personalizeTimezone(notification, preference);

            // Apply language-based personalization
            personalizeLanguage(notification, preference);

            // Apply A/B testing personalization
            personalizeAbTesting(notification, preference);

            // Apply custom personalization
            personalizeCustomContent(notification, preference);

            log.debug("Notification personalized successfully: id={}", notification.getId());
            return notification;

        } catch (Exception e) {
            log.error("Error personalizing notification: id={}, error={}", notification.getId(), e.getMessage(), e);
            return notification; // Return original notification on error
        }
    }

    /**
     * Check if notification should be sent based on user preferences and timing
     */
    public boolean shouldSendNotification(Notification notification) {
        try {
            NotificationPreference preference = preferenceService.getPreference(
                    notification.getUserId(), 
                    notification.getChannel(), 
                    notification.getType()
            );

            if (preference == null) {
                return true; // Default to send if no preference found
            }

            // Check global opt-out
            if (Boolean.TRUE.equals(preference.getGlobalOptOut())) {
                log.info("User has global opt-out: userId={}", notification.getUserId());
                return false;
            }

            // Check marketing opt-out for promotional notifications
            if (isMarketingNotification(notification.getType()) && Boolean.TRUE.equals(preference.getMarketingOptOut())) {
                log.info("User has marketing opt-out: userId={}, type={}", notification.getUserId(), notification.getType());
                return false;
            }

            // Check snooze status
            if (preference.getSnoozeUntil() != null && preference.getSnoozeUntil().isAfter(LocalDateTime.now())) {
                log.info("User has snoozed notifications: userId={}, snoozeUntil={}", 
                        notification.getUserId(), preference.getSnoozeUntil());
                return false;
            }

            // Check quiet hours
            if (isInQuietHours(preference)) {
                log.info("Notification blocked by quiet hours: userId={}", notification.getUserId());
                return false;
            }

            // Check frequency limits
            if (exceedsFrequencyLimit(notification.getUserId(), preference)) {
                log.info("Notification blocked by frequency limit: userId={}", notification.getUserId());
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("Error checking if notification should be sent: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            return true; // Default to send on error
        }
    }

    /**
     * Get optimal send time for notification based on user timezone and preferences
     */
    public LocalDateTime getOptimalSendTime(Notification notification) {
        try {
            NotificationPreference preference = preferenceService.getPreference(
                    notification.getUserId(), 
                    notification.getChannel(), 
                    notification.getType()
            );

            if (preference == null || preference.getTimezone() == null) {
                return LocalDateTime.now(); // Send immediately if no preference
            }

            ZoneId userTimezone = ZoneId.of(preference.getTimezone());
            ZonedDateTime userTime = ZonedDateTime.now(userTimezone);

            // If in quiet hours, schedule for after quiet hours
            if (Boolean.TRUE.equals(preference.getQuietHoursEnabled()) && 
                preference.getQuietHoursStart() != null && preference.getQuietHoursEnd() != null) {
                
                if (isTimeInQuietHours(userTime.toLocalTime(), preference.getQuietHoursStart(), preference.getQuietHoursEnd())) {
                    ZonedDateTime afterQuietHours = userTime.with(preference.getQuietHoursEnd()).plusMinutes(1);
                    return afterQuietHours.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                }
            }

            return LocalDateTime.now(); // Send immediately if not in quiet hours

        } catch (Exception e) {
            log.error("Error calculating optimal send time: notificationId={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            return LocalDateTime.now();
        }
    }

    /**
     * Get A/B test group for user
     */
    public String getAbTestGroup(Long userId, String testName) {
        try {
            NotificationPreference preference = preferenceService.getPreference(userId, null, null);
            
            if (preference != null && preference.getAbTestGroup() != null) {
                return preference.getAbTestGroup();
            }

            // Assign A/B test group based on user ID hash
            String group = (userId % 2 == 0) ? "A" : "B";
            
            // Update preference with A/B test group
            if (preference != null) {
                preference.setAbTestGroup(group);
                preferenceService.updatePreference(preference);
            }

            return group;

        } catch (Exception e) {
            log.error("Error getting A/B test group: userId={}, testName={}, error={}", 
                    userId, testName, e.getMessage(), e);
            return "A"; // Default to group A
        }
    }

    /**
     * Apply timezone-based personalization
     */
    private void personalizeTimezone(Notification notification, NotificationPreference preference) {
        if (preference.getTimezone() == null) {
            return;
        }

        try {
            ZoneId userTimezone = ZoneId.of(preference.getTimezone());
            ZonedDateTime userTime = ZonedDateTime.now(userTimezone);
            
            // Add timezone-specific variables to template variables
            Map<String, Object> templateVars = notification.getTemplateVariables();
            if (templateVars == null) {
                templateVars = new HashMap<>();
                notification.setTemplateVariables(templateVars);
            }
            
            templateVars.put("userTimezone", preference.getTimezone());
            templateVars.put("userLocalTime", userTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            templateVars.put("userLocalDate", userTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            templateVars.put("userLocalTimeFormatted", userTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a")));

        } catch (Exception e) {
            log.error("Error applying timezone personalization: notificationId={}, timezone={}, error={}", 
                    notification.getId(), preference.getTimezone(), e.getMessage(), e);
        }
    }

    /**
     * Apply language-based personalization
     */
    private void personalizeLanguage(Notification notification, NotificationPreference preference) {
        if (preference.getLanguageCode() == null) {
            return;
        }

        try {
            // Add language-specific variables to template variables
            Map<String, Object> templateVars = notification.getTemplateVariables();
            if (templateVars == null) {
                templateVars = new HashMap<>();
                notification.setTemplateVariables(templateVars);
            }
            
            templateVars.put("userLanguage", preference.getLanguageCode());
            templateVars.put("isEnglish", "en".equals(preference.getLanguageCode()));
            templateVars.put("isVietnamese", "vi".equals(preference.getLanguageCode()));
            
            // Set language-specific greeting
            String greeting = switch (preference.getLanguageCode()) {
                case "vi" -> "Xin chào";
                case "fr" -> "Bonjour";
                case "es" -> "Hola";
                case "de" -> "Hallo";
                default -> "Hello";
            };
            templateVars.put("greeting", greeting);

        } catch (Exception e) {
            log.error("Error applying language personalization: notificationId={}, language={}, error={}", 
                    notification.getId(), preference.getLanguageCode(), e.getMessage(), e);
        }
    }

    /**
     * Apply A/B testing personalization
     */
    private void personalizeAbTesting(Notification notification, NotificationPreference preference) {
        try {
            String abTestGroup = getAbTestGroup(notification.getUserId(), "default");
            
            // Add A/B test variables to template variables
            Map<String, Object> templateVars = notification.getTemplateVariables();
            if (templateVars == null) {
                templateVars = new HashMap<>();
                notification.setTemplateVariables(templateVars);
            }
            
            templateVars.put("abTestGroup", abTestGroup);
            templateVars.put("isGroupA", "A".equals(abTestGroup));
            templateVars.put("isGroupB", "B".equals(abTestGroup));
            
            // Apply group-specific content variations
            if ("A".equals(abTestGroup)) {
                templateVars.put("ctaText", "Click Here");
                templateVars.put("urgencyLevel", "normal");
            } else {
                templateVars.put("ctaText", "Get Started Now");
                templateVars.put("urgencyLevel", "high");
            }

        } catch (Exception e) {
            log.error("Error applying A/B testing personalization: notificationId={}, error={}", 
                    notification.getId(), e.getMessage(), e);
        }
    }

    /**
     * Apply custom personalization based on user preferences
     */
    private void personalizeCustomContent(Notification notification, NotificationPreference preference) {
        if (preference.getCustomPreferences() == null || preference.getCustomPreferences().isEmpty()) {
            return;
        }

        try {
            // Add custom preferences to template variables
            Map<String, Object> templateVars = notification.getTemplateVariables();
            if (templateVars == null) {
                templateVars = new HashMap<>();
                notification.setTemplateVariables(templateVars);
            }
            
            // Merge custom preferences
            templateVars.putAll(preference.getCustomPreferences());

        } catch (Exception e) {
            log.error("Error applying custom personalization: notificationId={}, error={}", 
                    notification.getId(), e.getMessage(), e);
        }
    }

    /**
     * Check if notification type is marketing-related
     */
    private boolean isMarketingNotification(NotificationType type) {
        return type == NotificationType.PROMOTIONAL || 
               type == NotificationType.NEWSLETTER ||
               type == NotificationType.ANNOUNCEMENT;
    }

    /**
     * Check if current time is in quiet hours
     */
    private boolean isInQuietHours(NotificationPreference preference) {
        if (!Boolean.TRUE.equals(preference.getQuietHoursEnabled()) || 
            preference.getQuietHoursStart() == null || 
            preference.getQuietHoursEnd() == null) {
            return false;
        }

        try {
            ZoneId userTimezone = preference.getTimezone() != null ? 
                    ZoneId.of(preference.getTimezone()) : ZoneId.systemDefault();
            ZonedDateTime userTime = ZonedDateTime.now(userTimezone);
            
            return isTimeInQuietHours(userTime.toLocalTime(), preference.getQuietHoursStart(), preference.getQuietHoursEnd());

        } catch (Exception e) {
            log.error("Error checking quiet hours: userId={}, error={}", preference.getUserId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if time is within quiet hours range
     */
    private boolean isTimeInQuietHours(java.time.LocalTime currentTime, java.time.LocalTime start, java.time.LocalTime end) {
        if (start.isBefore(end)) {
            // Same day range (e.g., 22:00 - 08:00 next day)
            return currentTime.isAfter(start) && currentTime.isBefore(end);
        } else {
            // Cross midnight range (e.g., 22:00 - 08:00 next day)
            return currentTime.isAfter(start) || currentTime.isBefore(end);
        }
    }

    /**
     * Check if user exceeds frequency limit
     */
    private boolean exceedsFrequencyLimit(Long userId, NotificationPreference preference) {
        if (preference.getFrequencyLimit() == null || preference.getFrequencyLimit() <= 0) {
            return false; // No limit set
        }

        // This would typically check against a cache or database of recent notifications
        // For now, we'll return false (not implemented)
        return false;
    }
}
