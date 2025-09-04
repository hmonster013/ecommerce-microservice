package org.de013.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.entity.enums.DigestFrequency;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Notification Preference Entity
 * Stores user preferences for notification delivery
 */
@Entity
@Table(name = "notification_preferences", indexes = {
    @Index(name = "idx_preference_user_id", columnList = "user_id"),
    @Index(name = "idx_preference_channel", columnList = "channel"),
    @Index(name = "idx_preference_type", columnList = "type"),
    @Index(name = "idx_preference_enabled", columnList = "enabled")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_preference_user_channel_type", 
                     columnNames = {"user_id", "channel", "type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
public class NotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "quiet_hours_enabled", nullable = false)
    @Builder.Default
    private Boolean quietHoursEnabled = false;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(name = "timezone")
    private String timezone; // User's timezone (e.g., "America/New_York")

    @Column(name = "language_code", length = 10)
    private String languageCode; // User's preferred language (e.g., "en", "vi", "fr")

    @Column(name = "global_opt_out")
    private Boolean globalOptOut = false; // Global opt-out from all notifications

    @Column(name = "marketing_opt_out")
    private Boolean marketingOptOut = false; // Opt-out from marketing notifications

    @Column(name = "snooze_until")
    private LocalDateTime snoozeUntil; // Temporary opt-out until this time

    @Column(name = "frequency_limit")
    private Integer frequencyLimit; // Max notifications per day (0 = unlimited)

    @Column(name = "digest_mode")
    private Boolean digestMode = false; // Receive notifications as digest

    @Column(name = "digest_frequency")
    @Enumerated(EnumType.STRING)
    private DigestFrequency digestFrequency; // DAILY, WEEKLY

    @Column(name = "personalization_enabled")
    private Boolean personalizationEnabled = true; // Enable content personalization

    @Column(name = "ab_test_group")
    private String abTestGroup; // A/B testing group identifier

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_preferences", columnDefinition = "jsonb")
    private Map<String, Object> customPreferences = new HashMap<>(); // Custom user preferences

    @Column(name = "gdpr_consent")
    private Boolean gdprConsent; // GDPR consent status

    @Column(name = "gdpr_consent_date")
    private LocalDateTime gdprConsentDate; // When GDPR consent was given

    @Column(name = "can_spam_compliant")
    private Boolean canSpamCompliant = true; // CAN-SPAM compliance status

    @Column(name = "last_engagement_date")
    private LocalDateTime lastEngagementDate; // Last time user engaged with notifications



    @Column(name = "frequency_limit_per_hour")
    private Integer frequencyLimitPerHour;

    @Column(name = "frequency_limit_per_day")
    private Integer frequencyLimitPerDay;

    @Column(name = "minimum_priority", length = 20)
    private String minimumPriority;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_settings", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> channelSettings = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "opt_out_reason", length = 500)
    private String optOutReason;



    // Business methods

    /**
     * Check if notifications are enabled for this preference
     */
    public boolean isNotificationEnabled() {
        return Boolean.TRUE.equals(enabled) && !Boolean.TRUE.equals(globalOptOut);
    }

    /**
     * Check if current time is within quiet hours
     */
    public boolean isInQuietHours() {
        if (!Boolean.TRUE.equals(quietHoursEnabled) || 
            quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }

        LocalTime now = LocalTime.now();
        
        // Handle quiet hours that span midnight
        if (quietHoursStart.isAfter(quietHoursEnd)) {
            return now.isAfter(quietHoursStart) || now.isBefore(quietHoursEnd);
        } else {
            return now.isAfter(quietHoursStart) && now.isBefore(quietHoursEnd);
        }
    }

    /**
     * Check if notification should be delivered based on preferences
     */
    public boolean shouldDeliverNotification() {
        return isNotificationEnabled() && !isInQuietHours();
    }

    /**
     * Get channel-specific setting
     */
    @SuppressWarnings("unchecked")
    public <T> T getChannelSetting(String key, Class<T> type) {
        if (channelSettings == null || !channelSettings.containsKey(key)) {
            return null;
        }
        
        Object value = channelSettings.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        
        return null;
    }

    /**
     * Set channel-specific setting
     */
    public void setChannelSetting(String key, Object value) {
        if (channelSettings == null) {
            channelSettings = new HashMap<>();
        }
        channelSettings.put(key, value);
    }

    /**
     * Check if frequency limit is exceeded
     */
    public boolean isFrequencyLimitExceeded(int notificationsInLastHour, int notificationsInLastDay) {
        if (frequencyLimitPerHour != null && notificationsInLastHour >= frequencyLimitPerHour) {
            return true;
        }
        
        if (frequencyLimitPerDay != null && notificationsInLastDay >= frequencyLimitPerDay) {
            return true;
        }
        
        return false;
    }

    /**
     * Opt out from all notifications
     */
    public void optOutGlobally(String reason) {
        this.globalOptOut = true;
        this.enabled = false;
        this.optOutReason = reason;
    }

    /**
     * Opt in to notifications
     */
    public void optIn() {
        this.globalOptOut = false;
        this.enabled = true;
        this.optOutReason = null;
    }

    /**
     * Set quiet hours
     */
    public void setQuietHours(LocalTime start, LocalTime end, String timezone) {
        this.quietHoursEnabled = true;
        this.quietHoursStart = start;
        this.quietHoursEnd = end;
        this.timezone = timezone;
    }

    /**
     * Disable quiet hours
     */
    public void disableQuietHours() {
        this.quietHoursEnabled = false;
        this.quietHoursStart = null;
        this.quietHoursEnd = null;
    }

    /**
     * Create default preference for user, channel, and type
     */
    public static NotificationPreference createDefault(Long userId, NotificationChannel channel, NotificationType type) {
        return NotificationPreference.builder()
                .userId(userId)
                .channel(channel)
                .type(type)
                .enabled(true)
                .globalOptOut(false)
                .quietHoursEnabled(false)
                .timezone("UTC")
                .languageCode("en")
                .build();
    }

    /**
     * Check if this preference matches the given criteria
     */
    public boolean matches(Long userId, NotificationChannel channel, NotificationType type) {
        return this.userId.equals(userId) && 
               this.channel == channel && 
               this.type == type;
    }
}
