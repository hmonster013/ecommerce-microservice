package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.NotificationPreference;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing user opt-out preferences and legal compliance
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OptOutService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationPreferenceService preferenceService;

    /**
     * Set global opt-out for user (all notifications)
     */
    @Transactional
    public void setGlobalOptOut(Long userId, String reason) {
        log.info("Setting global opt-out for user: userId={}, reason={}", userId, reason);

        try {
            // Get or create global preference
            NotificationPreference globalPreference = preferenceService.getOrCreatePreference(userId, null, null);
            
            globalPreference.setGlobalOptOut(true);
            globalPreference.setOptOutReason(reason);
            globalPreference.setUpdatedAt(LocalDateTime.now());
            
            preferenceRepository.save(globalPreference);
            
            // Also set opt-out for all existing channel/type specific preferences
            List<NotificationPreference> userPreferences = preferenceRepository.findByUserIdAndDeletedFalse(userId);
            for (NotificationPreference preference : userPreferences) {
                if (!preference.equals(globalPreference)) {
                    preference.setEnabled(false);
                    preference.setOptOutReason(reason);
                    preference.setUpdatedAt(LocalDateTime.now());
                }
            }
            
            if (!userPreferences.isEmpty()) {
                preferenceRepository.saveAll(userPreferences);
            }
            
            log.info("Global opt-out set successfully: userId={}", userId);
            
        } catch (Exception e) {
            log.error("Error setting global opt-out: userId={}, error={}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to set global opt-out: " + e.getMessage(), e);
        }
    }

    /**
     * Remove global opt-out for user
     */
    @Transactional
    public void removeGlobalOptOut(Long userId) {
        log.info("Removing global opt-out for user: userId={}", userId);

        try {
            NotificationPreference globalPreference = preferenceService.getOrCreatePreference(userId, null, null);
            
            globalPreference.setGlobalOptOut(false);
            globalPreference.setOptOutReason(null);
            globalPreference.setUpdatedAt(LocalDateTime.now());
            
            preferenceRepository.save(globalPreference);
            
            log.info("Global opt-out removed successfully: userId={}", userId);
            
        } catch (Exception e) {
            log.error("Error removing global opt-out: userId={}, error={}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to remove global opt-out: " + e.getMessage(), e);
        }
    }

    /**
     * Set channel-specific opt-out
     */
    @Transactional
    public void setChannelOptOut(Long userId, NotificationChannel channel, String reason) {
        log.info("Setting channel opt-out: userId={}, channel={}, reason={}", userId, channel, reason);

        try {
            // Get all preferences for this user and channel
            List<NotificationPreference> channelPreferences = preferenceRepository.findByUserIdAndChannelAndDeletedFalse(userId, channel);
            
            if (channelPreferences.isEmpty()) {
                // Create a default preference for this channel
                NotificationPreference preference = preferenceService.getOrCreatePreference(userId, channel, null);
                preference.setEnabled(false);
                preference.setOptOutReason(reason);
                preference.setUpdatedAt(LocalDateTime.now());
                preferenceRepository.save(preference);
            } else {
                // Disable all preferences for this channel
                for (NotificationPreference preference : channelPreferences) {
                    preference.setEnabled(false);
                    preference.setOptOutReason(reason);
                    preference.setUpdatedAt(LocalDateTime.now());
                }
                preferenceRepository.saveAll(channelPreferences);
            }
            
            log.info("Channel opt-out set successfully: userId={}, channel={}", userId, channel);
            
        } catch (Exception e) {
            log.error("Error setting channel opt-out: userId={}, channel={}, error={}", 
                    userId, channel, e.getMessage(), e);
            throw new RuntimeException("Failed to set channel opt-out: " + e.getMessage(), e);
        }
    }

    /**
     * Remove channel-specific opt-out
     */
    @Transactional
    public void removeChannelOptOut(Long userId, NotificationChannel channel) {
        log.info("Removing channel opt-out: userId={}, channel={}", userId, channel);

        try {
            List<NotificationPreference> channelPreferences = preferenceRepository.findByUserIdAndChannelAndDeletedFalse(userId, channel);
            
            for (NotificationPreference preference : channelPreferences) {
                preference.setEnabled(true);
                preference.setOptOutReason(null);
                preference.setUpdatedAt(LocalDateTime.now());
            }
            
            if (!channelPreferences.isEmpty()) {
                preferenceRepository.saveAll(channelPreferences);
            }
            
            log.info("Channel opt-out removed successfully: userId={}, channel={}", userId, channel);
            
        } catch (Exception e) {
            log.error("Error removing channel opt-out: userId={}, channel={}, error={}", 
                    userId, channel, e.getMessage(), e);
            throw new RuntimeException("Failed to remove channel opt-out: " + e.getMessage(), e);
        }
    }

    /**
     * Set marketing opt-out (GDPR/CAN-SPAM compliance)
     */
    @Transactional
    public void setMarketingOptOut(Long userId, String reason) {
        log.info("Setting marketing opt-out: userId={}, reason={}", userId, reason);

        try {
            NotificationPreference globalPreference = preferenceService.getOrCreatePreference(userId, null, null);
            
            globalPreference.setMarketingOptOut(true);
            globalPreference.setOptOutReason(reason);
            globalPreference.setCanSpamCompliant(true);
            globalPreference.setUpdatedAt(LocalDateTime.now());
            
            preferenceRepository.save(globalPreference);
            
            log.info("Marketing opt-out set successfully: userId={}", userId);
            
        } catch (Exception e) {
            log.error("Error setting marketing opt-out: userId={}, error={}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to set marketing opt-out: " + e.getMessage(), e);
        }
    }

    /**
     * Set temporary opt-out (snooze)
     */
    @Transactional
    public void setTemporaryOptOut(Long userId, LocalDateTime snoozeUntil, String reason) {
        log.info("Setting temporary opt-out: userId={}, snoozeUntil={}, reason={}", userId, snoozeUntil, reason);

        try {
            NotificationPreference globalPreference = preferenceService.getOrCreatePreference(userId, null, null);
            
            globalPreference.setSnoozeUntil(snoozeUntil);
            globalPreference.setOptOutReason(reason);
            globalPreference.setUpdatedAt(LocalDateTime.now());
            
            preferenceRepository.save(globalPreference);
            
            log.info("Temporary opt-out set successfully: userId={}, snoozeUntil={}", userId, snoozeUntil);
            
        } catch (Exception e) {
            log.error("Error setting temporary opt-out: userId={}, error={}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to set temporary opt-out: " + e.getMessage(), e);
        }
    }

    /**
     * Remove temporary opt-out (unsnooze)
     */
    @Transactional
    public void removeTemporaryOptOut(Long userId) {
        log.info("Removing temporary opt-out: userId={}", userId);

        try {
            NotificationPreference globalPreference = preferenceService.getOrCreatePreference(userId, null, null);
            
            globalPreference.setSnoozeUntil(null);
            globalPreference.setOptOutReason(null);
            globalPreference.setUpdatedAt(LocalDateTime.now());
            
            preferenceRepository.save(globalPreference);
            
            log.info("Temporary opt-out removed successfully: userId={}", userId);
            
        } catch (Exception e) {
            log.error("Error removing temporary opt-out: userId={}, error={}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to remove temporary opt-out: " + e.getMessage(), e);
        }
    }

    /**
     * Set GDPR consent
     */
    @Transactional
    public void setGdprConsent(Long userId, boolean consent) {
        log.info("Setting GDPR consent: userId={}, consent={}", userId, consent);

        try {
            NotificationPreference globalPreference = preferenceService.getOrCreatePreference(userId, null, null);
            
            globalPreference.setGdprConsent(consent);
            globalPreference.setGdprConsentDate(LocalDateTime.now());
            globalPreference.setUpdatedAt(LocalDateTime.now());
            
            // If consent is withdrawn, set global opt-out
            if (!consent) {
                globalPreference.setGlobalOptOut(true);
                globalPreference.setOptOutReason("GDPR consent withdrawn");
            }
            
            preferenceRepository.save(globalPreference);
            
            log.info("GDPR consent set successfully: userId={}, consent={}", userId, consent);
            
        } catch (Exception e) {
            log.error("Error setting GDPR consent: userId={}, error={}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to set GDPR consent: " + e.getMessage(), e);
        }
    }

    /**
     * Check if user has opted out globally
     */
    public boolean hasGlobalOptOut(Long userId) {
        try {
            Optional<NotificationPreference> preference = preferenceRepository.findByUserIdAndChannelIsNullAndTypeIsNullAndDeletedFalse(userId);
            return preference.map(p -> Boolean.TRUE.equals(p.getGlobalOptOut())).orElse(false);
            
        } catch (Exception e) {
            log.error("Error checking global opt-out: userId={}, error={}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if user has opted out of marketing notifications
     */
    public boolean hasMarketingOptOut(Long userId) {
        try {
            Optional<NotificationPreference> preference = preferenceRepository.findByUserIdAndChannelIsNullAndTypeIsNullAndDeletedFalse(userId);
            return preference.map(p -> Boolean.TRUE.equals(p.getMarketingOptOut())).orElse(false);
            
        } catch (Exception e) {
            log.error("Error checking marketing opt-out: userId={}, error={}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if user has temporary opt-out (snooze) active
     */
    public boolean hasTemporaryOptOut(Long userId) {
        try {
            Optional<NotificationPreference> preference = preferenceRepository.findByUserIdAndChannelIsNullAndTypeIsNullAndDeletedFalse(userId);
            return preference.map(p -> p.getSnoozeUntil() != null && p.getSnoozeUntil().isAfter(LocalDateTime.now())).orElse(false);
            
        } catch (Exception e) {
            log.error("Error checking temporary opt-out: userId={}, error={}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get opt-out status for user
     */
    public OptOutStatus getOptOutStatus(Long userId) {
        try {
            Optional<NotificationPreference> preference = preferenceRepository.findByUserIdAndChannelIsNullAndTypeIsNullAndDeletedFalse(userId);
            
            if (preference.isEmpty()) {
                return OptOutStatus.builder()
                        .userId(userId)
                        .globalOptOut(false)
                        .marketingOptOut(false)
                        .temporaryOptOut(false)
                        .gdprConsent(null)
                        .build();
            }
            
            NotificationPreference pref = preference.get();
            return OptOutStatus.builder()
                    .userId(userId)
                    .globalOptOut(Boolean.TRUE.equals(pref.getGlobalOptOut()))
                    .marketingOptOut(Boolean.TRUE.equals(pref.getMarketingOptOut()))
                    .temporaryOptOut(pref.getSnoozeUntil() != null && pref.getSnoozeUntil().isAfter(LocalDateTime.now()))
                    .snoozeUntil(pref.getSnoozeUntil())
                    .gdprConsent(pref.getGdprConsent())
                    .gdprConsentDate(pref.getGdprConsentDate())
                    .optOutReason(pref.getOptOutReason())
                    .canSpamCompliant(Boolean.TRUE.equals(pref.getCanSpamCompliant()))
                    .build();
            
        } catch (Exception e) {
            log.error("Error getting opt-out status: userId={}, error={}", userId, e.getMessage(), e);
            return OptOutStatus.builder()
                    .userId(userId)
                    .globalOptOut(false)
                    .marketingOptOut(false)
                    .temporaryOptOut(false)
                    .build();
        }
    }

    /**
     * Opt-out status DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class OptOutStatus {
        private Long userId;
        private boolean globalOptOut;
        private boolean marketingOptOut;
        private boolean temporaryOptOut;
        private LocalDateTime snoozeUntil;
        private Boolean gdprConsent;
        private LocalDateTime gdprConsentDate;
        private String optOutReason;
        private boolean canSpamCompliant;
    }
}
