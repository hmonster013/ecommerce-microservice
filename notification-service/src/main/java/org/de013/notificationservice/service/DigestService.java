package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.dto.CreateNotificationRequest;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.NotificationPreference;
import org.de013.notificationservice.entity.enums.DigestFrequency;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.entity.enums.Priority;
import org.de013.notificationservice.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing digest notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DigestService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;
    private final NotificationService notificationService;

    /**
     * Process daily digests - runs every day at 9 AM
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void processDailyDigests() {
        log.info("Processing daily digests");
        processDigests(DigestFrequency.DAILY);
    }

    /**
     * Process weekly digests - runs every Monday at 9 AM
     */
    @Scheduled(cron = "0 0 9 * * MON")
    @Transactional
    public void processWeeklyDigests() {
        log.info("Processing weekly digests");
        processDigests(DigestFrequency.WEEKLY);
    }

    /**
     * Process monthly digests - runs on 1st of every month at 9 AM
     */
    @Scheduled(cron = "0 0 9 1 * *")
    @Transactional
    public void processMonthlyDigests() {
        log.info("Processing monthly digests");
        processDigests(DigestFrequency.MONTHLY);
    }

    /**
     * Process digests for specific frequency
     */
    private void processDigests(DigestFrequency frequency) {
        try {
            // Get users who have digest mode enabled for this frequency
            List<NotificationPreference> digestPreferences = preferenceService.getUsersWithDigestMode(frequency);
            
            log.info("Found {} users with {} digest enabled", digestPreferences.size(), frequency);
            
            for (NotificationPreference preference : digestPreferences) {
                try {
                    processUserDigest(preference, frequency);
                } catch (Exception e) {
                    log.error("Error processing digest for user: userId={}, frequency={}, error={}", 
                            preference.getUserId(), frequency, e.getMessage(), e);
                }
            }
            
            log.info("Completed processing {} digests", frequency);
            
        } catch (Exception e) {
            log.error("Error processing {} digests: {}", frequency, e.getMessage(), e);
        }
    }

    /**
     * Process digest for specific user
     */
    private void processUserDigest(NotificationPreference preference, DigestFrequency frequency) {
        Long userId = preference.getUserId();
        log.debug("Processing {} digest for user: userId={}", frequency, userId);

        // Calculate time range for digest
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateDigestStartTime(endTime, frequency);

        // Get pending notifications for user in time range
        List<Notification> pendingNotifications = notificationRepository.findPendingNotificationsForDigest(
                userId, startTime, endTime);

        if (pendingNotifications.isEmpty()) {
            log.debug("No pending notifications for digest: userId={}, frequency={}", userId, frequency);
            return;
        }

        log.info("Creating {} digest for user: userId={}, notificationCount={}", 
                frequency, userId, pendingNotifications.size());

        // Group notifications by type
        Map<NotificationType, List<Notification>> groupedNotifications = pendingNotifications.stream()
                .collect(Collectors.groupingBy(Notification::getType));

        // Create digest notification
        CreateNotificationRequest digestRequest = createDigestNotification(
                userId, frequency, groupedNotifications, startTime, endTime);

        // Send digest notification
        Notification digestNotification = notificationService.createNotification(digestRequest);

        // Mark original notifications as included in digest
        markNotificationsAsDigested(pendingNotifications, digestNotification.getId());

        log.info("Digest created successfully: userId={}, frequency={}, digestId={}", 
                userId, frequency, digestNotification.getId());
    }

    /**
     * Create digest notification request
     */
    private CreateNotificationRequest createDigestNotification(Long userId, DigestFrequency frequency,
                                                             Map<NotificationType, List<Notification>> groupedNotifications,
                                                             LocalDateTime startTime, LocalDateTime endTime) {
        
        // Calculate total notification count
        int totalCount = groupedNotifications.values().stream()
                .mapToInt(List::size)
                .sum();

        // Create subject
        String subject = String.format("%s Digest - %d notifications", 
                frequency.getDisplayName(), totalCount);

        // Create template variables
        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("frequency", frequency.getDisplayName().toLowerCase());
        templateVariables.put("totalCount", totalCount);
        templateVariables.put("startDate", startTime.toLocalDate().toString());
        templateVariables.put("endDate", endTime.toLocalDate().toString());
        templateVariables.put("groupedNotifications", createDigestSummary(groupedNotifications));

        return CreateNotificationRequest.builder()
                .userId(userId)
                .type(NotificationType.DIGEST)
                .channel(NotificationChannel.EMAIL) // Digests are typically sent via email
                .priority(Priority.LOW)
                .subject(subject)
                .templateId(getDigestTemplateId(frequency))
                .templateVariables(templateVariables)
                .correlationId("digest-" + frequency.name().toLowerCase() + "-" + userId + "-" + System.currentTimeMillis())
                .referenceType("DIGEST")
                .referenceId(frequency.name() + "_" + userId)
                .build();
    }

    /**
     * Create digest summary from grouped notifications
     */
    private Map<String, Object> createDigestSummary(Map<NotificationType, List<Notification>> groupedNotifications) {
        Map<String, Object> summary = new HashMap<>();
        
        for (Map.Entry<NotificationType, List<Notification>> entry : groupedNotifications.entrySet()) {
            NotificationType type = entry.getKey();
            List<Notification> notifications = entry.getValue();
            
            Map<String, Object> typeSummary = new HashMap<>();
            typeSummary.put("count", notifications.size());
            typeSummary.put("type", type.getDisplayName());
            typeSummary.put("notifications", notifications.stream()
                    .map(this::createNotificationSummary)
                    .collect(Collectors.toList()));
            
            summary.put(type.name().toLowerCase(), typeSummary);
        }
        
        return summary;
    }

    /**
     * Create summary for individual notification
     */
    private Map<String, Object> createNotificationSummary(Notification notification) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", notification.getId());
        summary.put("subject", notification.getSubject());
        summary.put("content", truncateContent(notification.getContent(), 100));
        summary.put("createdAt", notification.getCreatedAt().toString());
        summary.put("priority", notification.getPriority().name());
        return summary;
    }

    /**
     * Truncate content for digest summary
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    /**
     * Mark notifications as included in digest
     */
    @Transactional
    private void markNotificationsAsDigested(List<Notification> notifications, Long digestId) {
        for (Notification notification : notifications) {
            notification.setStatus(NotificationStatus.DIGESTED);
            notification.setDigestId(digestId);
            notification.setUpdatedAt(LocalDateTime.now());
        }
        notificationRepository.saveAll(notifications);
    }

    /**
     * Calculate digest start time based on frequency
     */
    private LocalDateTime calculateDigestStartTime(LocalDateTime endTime, DigestFrequency frequency) {
        return switch (frequency) {
            case DAILY -> endTime.minusDays(1);
            case WEEKLY -> endTime.minusWeeks(1);
            case MONTHLY -> endTime.minusMonths(1);
        };
    }

    /**
     * Get digest template ID based on frequency
     */
    private Long getDigestTemplateId(DigestFrequency frequency) {
        return switch (frequency) {
            case DAILY -> 100L;   // Daily digest template
            case WEEKLY -> 101L;  // Weekly digest template
            case MONTHLY -> 102L; // Monthly digest template
        };
    }

    /**
     * Check if user should receive digest
     */
    public boolean shouldReceiveDigest(Long userId, DigestFrequency frequency) {
        try {
            NotificationPreference preference = preferenceService.getPreference(userId, null, null);
            
            if (preference == null || !Boolean.TRUE.equals(preference.getDigestMode())) {
                return false;
            }
            
            if (preference.getDigestFrequency() != frequency) {
                return false;
            }
            
            // Check if user has global opt-out
            if (Boolean.TRUE.equals(preference.getGlobalOptOut())) {
                return false;
            }
            
            // Check if user is in snooze mode
            if (preference.getSnoozeUntil() != null && preference.getSnoozeUntil().isAfter(LocalDateTime.now())) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error checking if user should receive digest: userId={}, frequency={}, error={}", 
                    userId, frequency, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get digest statistics
     */
    public Map<String, Object> getDigestStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Get digest counts by frequency
            for (DigestFrequency frequency : DigestFrequency.values()) {
                long count = notificationRepository.countDigestNotifications(frequency.name(), startDate, endDate);
                stats.put(frequency.name().toLowerCase() + "_count", count);
            }
            
            // Get total digest count
            long totalDigests = notificationRepository.countAllDigestNotifications(startDate, endDate);
            stats.put("total_digests", totalDigests);
            
            // Get average notifications per digest
            double avgNotificationsPerDigest = notificationRepository.getAverageNotificationsPerDigest(startDate, endDate);
            stats.put("avg_notifications_per_digest", avgNotificationsPerDigest);
            
            stats.put("start_date", startDate.toString());
            stats.put("end_date", endDate.toString());
            stats.put("generated_at", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            log.error("Error getting digest statistics: {}", e.getMessage(), e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
}
