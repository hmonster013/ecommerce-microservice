package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.dto.CreateNotificationRequest;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.NotificationTemplate;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TemplateService templateService;
    private final NotificationPreferenceService preferenceService;
    private final DeliveryService deliveryService;

    /**
     * Create a new notification
     */
    @Transactional
    public Notification createNotification(CreateNotificationRequest request) {
        log.info("Creating notification for user={}, type={}, channel={}", 
                request.getUserId(), request.getType(), request.getChannel());

        // Validate request
        validateCreateRequest(request);

        // Check user preferences
        if (!preferenceService.shouldDeliverNotification(request.getUserId(), request.getChannel(), request.getType())) {
            log.info("Notification blocked by user preferences for user={}, type={}, channel={}", 
                    request.getUserId(), request.getType(), request.getChannel());
            throw new RuntimeException("Notification blocked by user preferences");
        }

        // Build notification
        var builder = Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .channel(request.getChannel())
                .priority(request.getPriority())
                .recipientAddress(request.getRecipientAddress())
                .senderAddress(request.getSenderAddress())
                .templateVariables(request.getTemplateVariables())
                .metadata(request.getMetadata())
                .scheduledAt(request.getScheduledAt())
                .expiresAt(request.getExpiresAt())
                .maxRetryAttempts(request.getMaxRetryAttempts())
                .correlationId(request.getCorrelationId())
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .status(NotificationStatus.DRAFT);

        // Generate correlation ID if not provided
        if (request.getCorrelationId() == null) {
            builder.correlationId(UUID.randomUUID().toString());
        }

        // Handle template-based or direct content
        if (request.getTemplateId() != null) {
            // Template-based notification
            builder.templateId(request.getTemplateId());
            renderTemplateContent(builder, request);
        } else {
            // Direct content notification
            builder.subject(request.getSubject())
                   .content(request.getContent())
                   .htmlContent(request.getHtmlContent());
        }

        Notification notification = builder.build();
        
        // Set status to pending if ready for immediate delivery
        if (notification.isReadyForDelivery()) {
            notification.setStatus(NotificationStatus.PENDING);
        }

        Notification savedNotification = notificationRepository.save(notification);
        log.info("Notification created successfully with id={}", savedNotification.getId());

        // Trigger delivery if notification is ready
        if (savedNotification.isReadyForDelivery()) {
            try {
                deliveryService.deliverNotification(savedNotification);
            } catch (Exception e) {
                log.error("Error triggering delivery for notification {}: {}", savedNotification.getId(), e.getMessage(), e);
                // Don't fail the creation, delivery will be retried by scheduler
            }
        }

        return savedNotification;
    }

    /**
     * Find notification by ID
     */
    public Optional<Notification> findById(Long id) {
        log.debug("Finding notification by id={}", id);
        return notificationRepository.findById(id);
    }

    /**
     * Find notifications by user ID
     */
    public Page<Notification> findByUserId(Long userId, Pageable pageable) {
        log.debug("Finding notifications for user={}", userId);
        return notificationRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Find notifications ready for delivery
     */
    public List<Notification> findReadyForDelivery() {
        log.debug("Finding notifications ready for delivery");
        List<NotificationStatus> readyStatuses = List.of(NotificationStatus.PENDING, NotificationStatus.QUEUED);
        return notificationRepository.findReadyForDelivery(readyStatuses, LocalDateTime.now());
    }

    /**
     * Find notifications ready for retry
     */
    public List<Notification> findReadyForRetry() {
        log.debug("Finding notifications ready for retry");
        return notificationRepository.findReadyForRetry(NotificationStatus.RETRY, LocalDateTime.now());
    }

    /**
     * Mark notification as sent
     */
    @Transactional
    public void markAsSent(Long notificationId, String externalId) {
        log.info("Marking notification as sent: id={}, externalId={}", notificationId, externalId);
        
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + notificationId));
        
        notification.markAsSent();
        notification.setExternalId(externalId);
        
        notificationRepository.save(notification);
        log.info("Notification marked as sent successfully");
    }

    /**
     * Mark notification as delivered
     */
    @Transactional
    public void markAsDelivered(Long notificationId) {
        log.info("Marking notification as delivered: id={}", notificationId);
        
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + notificationId));
        
        notification.markAsDelivered();
        notificationRepository.save(notification);
        log.info("Notification marked as delivered successfully");
    }

    /**
     * Mark notification as read
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        log.info("Marking notification as read: id={}, userId={}", notificationId, userId);
        
        int updated = notificationRepository.markAsRead(
                notificationId, userId, NotificationStatus.READ, LocalDateTime.now());
        
        if (updated == 0) {
            throw new RuntimeException("Notification not found or access denied");
        }
        
        log.info("Notification marked as read successfully");
    }

    /**
     * Mark notification as failed
     */
    @Transactional
    public void markAsFailed(Long notificationId, String errorMessage) {
        log.info("Marking notification as failed: id={}, error={}", notificationId, errorMessage);
        
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + notificationId));
        
        notification.markAsFailed(errorMessage);
        
        // Check if retry is possible
        if (notification.canRetry()) {
            int delaySeconds = calculateRetryDelay(notification.getRetryCount());
            notification.incrementRetry(delaySeconds);
            log.info("Notification scheduled for retry in {} seconds", delaySeconds);
        }
        
        notificationRepository.save(notification);
        log.info("Notification marked as failed successfully");
    }

    /**
     * Count unread notifications for user
     */
    public long countUnreadByUserId(Long userId) {
        log.debug("Counting unread notifications for user={}", userId);
        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Find notifications by correlation ID
     */
    public List<Notification> findByCorrelationId(String correlationId) {
        log.debug("Finding notifications by correlationId={}", correlationId);
        return notificationRepository.findByCorrelationIdAndDeletedFalseOrderByCreatedAtDesc(correlationId);
    }

    /**
     * Validate create notification request
     */
    private void validateCreateRequest(CreateNotificationRequest request) {
        if (!request.hasContentOrTemplate()) {
            throw new IllegalArgumentException("Either content or template ID must be provided");
        }
        
        if (!request.isValidEmailAddress()) {
            throw new IllegalArgumentException("Invalid email address format");
        }
        
        if (!request.isValidPhoneNumber()) {
            throw new IllegalArgumentException("Invalid phone number format");
        }
        
        if (!request.isValidScheduleAndExpiration()) {
            throw new IllegalArgumentException("Scheduled time must be before expiration time");
        }
    }

    /**
     * Render template content for notification
     */
    @SuppressWarnings("rawtypes")
    private void renderTemplateContent(Notification.NotificationBuilder builder, CreateNotificationRequest request) {
        log.debug("Rendering template content for templateId={}", request.getTemplateId());
        
        NotificationTemplate template = templateService.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found with id: " + request.getTemplateId()));
        
        if (!template.isUsable()) {
            throw new RuntimeException("Template is not active or has been deleted");
        }
        
        if (!template.supportsChannel(request.getChannel())) {
            throw new RuntimeException("Template does not support channel: " + request.getChannel());
        }
        
        Map<String, Object> variables = request.getTemplateVariables();
        
        // Render subject
        String subject = templateService.renderSubject(template, variables);
        if (subject != null) {
            builder.subject(subject);
        }
        
        // Render body
        String body = templateService.renderBody(template, variables);
        builder.content(body);
        
        // Render HTML if supported
        if (template.supportsHtml()) {
            String html = templateService.renderHtml(template, variables);
            if (html != null) {
                builder.htmlContent(html);
            }
        }
        
        // Set sender information from template
        if (template.getSenderEmail() != null && request.getSenderAddress() == null) {
            builder.senderAddress(template.getSenderEmail());
        }
    }

    /**
     * Calculate retry delay with exponential backoff
     */
    private int calculateRetryDelay(int retryCount) {
        // Base delay of 60 seconds, exponential backoff with max of 30 minutes
        int baseDelay = 60;
        int delay = baseDelay * (int) Math.pow(2, retryCount);
        return Math.min(delay, 1800); // Max 30 minutes
    }
}
