package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Simple Notification Service
 * Handles email and SMS notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final SmsService smsService;

    /**
     * Send email notification
     */
    @Transactional
    public Notification sendEmail(Long userId, String recipient, String subject, String content) {
        log.info("Sending email notification to: {}", recipient);
        
        Notification notification = Notification.builder()
                .userId(userId)
                .channel(NotificationChannel.EMAIL)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .status(NotificationStatus.PENDING)
                .build();
        
        notification = notificationRepository.save(notification);
        
        try {
            emailService.sendEmail(recipient, subject, content);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            log.info("Email sent successfully to: {}", recipient);
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
            log.error("Failed to send email to {}: {}", recipient, e.getMessage());
        }
        
        return notificationRepository.save(notification);
    }

    /**
     * Send SMS notification
     */
    @Transactional
    public Notification sendSms(Long userId, String phoneNumber, String message) {
        log.info("Sending SMS notification to: {}", phoneNumber);
        
        Notification notification = Notification.builder()
                .userId(userId)
                .channel(NotificationChannel.SMS)
                .recipient(phoneNumber)
                .content(message)
                .status(NotificationStatus.PENDING)
                .build();
        
        notification = notificationRepository.save(notification);
        
        try {
            smsService.sendSms(phoneNumber, message);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            log.info("SMS sent successfully to: {}", phoneNumber);
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
            log.error("Failed to send SMS to {}: {}", phoneNumber, e.getMessage());
        }
        
        return notificationRepository.save(notification);
    }

    /**
     * Send both email and SMS notification
     */
    @Transactional
    public List<Notification> sendBoth(Long userId, String email, String phoneNumber, String subject, String message) {
        log.info("Sending both email and SMS notifications for user: {}", userId);

        // Send email
        Notification emailNotification = sendEmail(userId, email, subject, message);

        // Send SMS
        Notification smsNotification = sendSms(userId, phoneNumber, message);

        return List.of(emailNotification, smsNotification);
    }

    /**
     * Find notification by ID
     */
    public Optional<Notification> findById(Long id) {
        return notificationRepository.findById(id);
    }

    /**
     * Find notifications by user ID
     */
    public Page<Notification> findByUserId(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Count unread notifications for user
     */
    public long countUnreadByUserId(Long userId) {
        return notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.SENT);
    }

    /**
     * Mark notification as read
     */
    @Transactional
    public void markAsRead(Long id) {
        Optional<Notification> notificationOpt = notificationRepository.findById(id);
        if (notificationOpt.isPresent()) {
            Notification notification = notificationOpt.get();
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
            log.info("Notification marked as read: {}", id);
        } else {
            log.warn("Notification not found: {}", id);
            throw new RuntimeException("Notification not found: " + id);
        }
    }
}
