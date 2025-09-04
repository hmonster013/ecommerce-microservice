package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.NotificationPreference;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.repository.NotificationPreferenceRepository;
import org.de013.notificationservice.repository.NotificationRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing notification preferences
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationRepository notificationRepository;

    /**
     * Check if notification should be delivered based on user preferences
     */
    @Cacheable(value = "notification-preferences", key = "#userId + '_' + #channel + '_' + #type")
    public boolean shouldDeliverNotification(Long userId, NotificationChannel channel, NotificationType type) {
        log.debug("Checking delivery preferences for user={}, channel={}, type={}", userId, channel, type);

        // Find specific preference
        Optional<NotificationPreference> preference = preferenceRepository
                .findByUserIdAndChannelAndTypeAndDeletedFalse(userId, channel, type);

        if (preference.isPresent()) {
            NotificationPreference pref = preference.get();
            
            // Check if notification should be delivered
            boolean shouldDeliver = pref.shouldDeliverNotification();
            
            if (shouldDeliver) {
                // Check frequency limits
                shouldDeliver = !isFrequencyLimitExceeded(pref);
            }
            
            log.debug("Preference found for user={}, shouldDeliver={}", userId, shouldDeliver);
            return shouldDeliver;
        }

        // No specific preference found, create default and allow delivery
        log.debug("No preference found for user={}, creating default and allowing delivery", userId);
        createDefaultPreference(userId, channel, type);
        return true;
    }

    /**
     * Get user preference for channel and type
     */
    public Optional<NotificationPreference> getUserPreference(Long userId, NotificationChannel channel, NotificationType type) {
        log.debug("Getting user preference for user={}, channel={}, type={}", userId, channel, type);
        return preferenceRepository.findByUserIdAndChannelAndTypeAndDeletedFalse(userId, channel, type);
    }

    /**
     * Get all preferences for a user
     */
    public List<NotificationPreference> getUserPreferences(Long userId) {
        log.debug("Getting all preferences for user={}", userId);
        return preferenceRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId);
    }

    /**
     * Create or update user preference
     */
    @Transactional
    @CacheEvict(value = "notification-preferences", key = "#userId + '_' + #channel + '_' + #type")
    public NotificationPreference savePreference(Long userId, NotificationChannel channel, NotificationType type, 
                                               NotificationPreference preference) {
        log.info("Saving preference for user={}, channel={}, type={}", userId, channel, type);

        Optional<NotificationPreference> existing = preferenceRepository
                .findByUserIdAndChannelAndTypeAndDeletedFalse(userId, channel, type);

        NotificationPreference savedPreference;
        if (existing.isPresent()) {
            // Update existing preference
            NotificationPreference existingPref = existing.get();
            updatePreferenceFields(existingPref, preference);
            savedPreference = preferenceRepository.save(existingPref);
            log.info("Updated existing preference with id={}", savedPreference.getId());
        } else {
            // Create new preference
            preference.setUserId(userId);
            preference.setChannel(channel);
            preference.setType(type);
            savedPreference = preferenceRepository.save(preference);
            log.info("Created new preference with id={}", savedPreference.getId());
        }

        return savedPreference;
    }

    /**
     * Enable notifications for user, channel, and type
     */
    @Transactional
    @CacheEvict(value = "notification-preferences", key = "#userId + '_' + #channel + '_' + #type")
    public void enableNotifications(Long userId, NotificationChannel channel, NotificationType type) {
        log.info("Enabling notifications for user={}, channel={}, type={}", userId, channel, type);

        Optional<NotificationPreference> preference = preferenceRepository
                .findByUserIdAndChannelAndTypeAndDeletedFalse(userId, channel, type);

        if (preference.isPresent()) {
            NotificationPreference pref = preference.get();
            pref.optIn();
            preferenceRepository.save(pref);
        } else {
            // Create default enabled preference
            NotificationPreference newPref = NotificationPreference.createDefault(userId, channel, type);
            preferenceRepository.save(newPref);
        }

        log.info("Notifications enabled successfully");
    }

    /**
     * Disable notifications for user, channel, and type
     */
    @Transactional
    @CacheEvict(value = "notification-preferences", key = "#userId + '_' + #channel + '_' + #type")
    public void disableNotifications(Long userId, NotificationChannel channel, NotificationType type, String reason) {
        log.info("Disabling notifications for user={}, channel={}, type={}, reason={}", 
                userId, channel, type, reason);

        Optional<NotificationPreference> preference = preferenceRepository
                .findByUserIdAndChannelAndTypeAndDeletedFalse(userId, channel, type);

        if (preference.isPresent()) {
            NotificationPreference pref = preference.get();
            pref.setEnabled(false);
            pref.setOptOutReason(reason);
            preferenceRepository.save(pref);
        } else {
            // Create disabled preference
            NotificationPreference newPref = NotificationPreference.createDefault(userId, channel, type);
            newPref.setEnabled(false);
            newPref.setOptOutReason(reason);
            preferenceRepository.save(newPref);
        }

        log.info("Notifications disabled successfully");
    }

    /**
     * Set global opt-out for user
     */
    @Transactional
    @CacheEvict(value = "notification-preferences", allEntries = true)
    public void setGlobalOptOut(Long userId, boolean optOut, String reason) {
        log.info("Setting global opt-out for user={}, optOut={}, reason={}", userId, optOut, reason);

        int updated = preferenceRepository.updateGlobalOptOut(userId, optOut, LocalDateTime.now());
        
        if (updated == 0) {
            // No existing preferences, create default ones for common channels and types
            createDefaultPreferencesForUser(userId, optOut, reason);
        }

        log.info("Global opt-out updated for {} preferences", updated);
    }

    /**
     * Set quiet hours for user
     */
    @Transactional
    @CacheEvict(value = "notification-preferences", allEntries = true)
    public void setQuietHours(Long userId, NotificationChannel channel, NotificationType type,
                             LocalTime startTime, LocalTime endTime, String timezone) {
        log.info("Setting quiet hours for user={}, channel={}, type={}, start={}, end={}, timezone={}", 
                userId, channel, type, startTime, endTime, timezone);

        Optional<NotificationPreference> preference = preferenceRepository
                .findByUserIdAndChannelAndTypeAndDeletedFalse(userId, channel, type);

        NotificationPreference pref;
        if (preference.isPresent()) {
            pref = preference.get();
        } else {
            pref = NotificationPreference.createDefault(userId, channel, type);
        }

        pref.setQuietHours(startTime, endTime, timezone);
        preferenceRepository.save(pref);

        log.info("Quiet hours set successfully");
    }

    /**
     * Disable quiet hours for user
     */
    @Transactional
    @CacheEvict(value = "notification-preferences", allEntries = true)
    public void disableQuietHours(Long userId, NotificationChannel channel, NotificationType type) {
        log.info("Disabling quiet hours for user={}, channel={}, type={}", userId, channel, type);

        Optional<NotificationPreference> preference = preferenceRepository
                .findByUserIdAndChannelAndTypeAndDeletedFalse(userId, channel, type);

        if (preference.isPresent()) {
            NotificationPreference pref = preference.get();
            pref.disableQuietHours();
            preferenceRepository.save(pref);
            log.info("Quiet hours disabled successfully");
        }
    }

    /**
     * Check if frequency limit is exceeded for user
     */
    private boolean isFrequencyLimitExceeded(NotificationPreference preference) {
        if (preference.getFrequencyLimitPerHour() == null && preference.getFrequencyLimitPerDay() == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        
        // Check hourly limit
        if (preference.getFrequencyLimitPerHour() != null) {
            LocalDateTime oneHourAgo = now.minusHours(1);
            long countLastHour = notificationRepository.countSentInPeriod(
                    preference.getUserId(), preference.getChannel(), preference.getType(),
                    oneHourAgo, now);
            
            if (countLastHour >= preference.getFrequencyLimitPerHour()) {
                log.debug("Hourly frequency limit exceeded for user={}: {} >= {}", 
                        preference.getUserId(), countLastHour, preference.getFrequencyLimitPerHour());
                return true;
            }
        }

        // Check daily limit
        if (preference.getFrequencyLimitPerDay() != null) {
            LocalDateTime oneDayAgo = now.minusDays(1);
            long countLastDay = notificationRepository.countSentInPeriod(
                    preference.getUserId(), preference.getChannel(), preference.getType(),
                    oneDayAgo, now);
            
            if (countLastDay >= preference.getFrequencyLimitPerDay()) {
                log.debug("Daily frequency limit exceeded for user={}: {} >= {}", 
                        preference.getUserId(), countLastDay, preference.getFrequencyLimitPerDay());
                return true;
            }
        }

        return false;
    }

    /**
     * Create default preference for user
     */
    @Transactional
    private void createDefaultPreference(Long userId, NotificationChannel channel, NotificationType type) {
        log.debug("Creating default preference for user={}, channel={}, type={}", userId, channel, type);
        
        NotificationPreference defaultPref = NotificationPreference.createDefault(userId, channel, type);
        preferenceRepository.save(defaultPref);
    }

    /**
     * Create default preferences for user
     */
    private void createDefaultPreferencesForUser(Long userId, boolean globalOptOut, String reason) {
        log.debug("Creating default preferences for user={}", userId);
        
        // Create preferences for common combinations
        NotificationChannel[] channels = {NotificationChannel.EMAIL, NotificationChannel.SMS, NotificationChannel.PUSH};
        NotificationType[] types = {NotificationType.ORDER_PLACED, NotificationType.PAYMENT_SUCCESS, 
                                   NotificationType.USER_REGISTRATION};
        
        for (NotificationChannel channel : channels) {
            for (NotificationType type : types) {
                NotificationPreference pref = NotificationPreference.createDefault(userId, channel, type);
                if (globalOptOut) {
                    pref.optOutGlobally(reason);
                }
                preferenceRepository.save(pref);
            }
        }
    }

    /**
     * Update preference fields
     */
    private void updatePreferenceFields(NotificationPreference existing, NotificationPreference updated) {
        existing.setEnabled(updated.getEnabled());
        existing.setQuietHoursEnabled(updated.getQuietHoursEnabled());
        existing.setQuietHoursStart(updated.getQuietHoursStart());
        existing.setQuietHoursEnd(updated.getQuietHoursEnd());
        existing.setTimezone(updated.getTimezone());
        existing.setLanguage(updated.getLanguage());
        existing.setFrequencyLimitPerHour(updated.getFrequencyLimitPerHour());
        existing.setFrequencyLimitPerDay(updated.getFrequencyLimitPerDay());
        existing.setMinimumPriority(updated.getMinimumPriority());
        existing.setChannelSettings(updated.getChannelSettings());
        existing.setMetadata(updated.getMetadata());
        existing.setOptOutReason(updated.getOptOutReason());
        existing.setGlobalOptOut(updated.getGlobalOptOut());
    }
}
