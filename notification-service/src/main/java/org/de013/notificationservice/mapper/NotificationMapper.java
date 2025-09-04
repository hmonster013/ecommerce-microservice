package org.de013.notificationservice.mapper;

import org.de013.notificationservice.dto.NotificationDto;
import org.de013.notificationservice.entity.Notification;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Notification entity and DTO
 */
@Component
public class NotificationMapper {

    /**
     * Convert Notification entity to DTO
     */
    public NotificationDto toDto(Notification notification) {
        if (notification == null) {
            return null;
        }

        return NotificationDto.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType())
                .channel(notification.getChannel())
                .status(notification.getStatus())
                .priority(notification.getPriority())
                .subject(notification.getSubject())
                .content(notification.getContent())
                .htmlContent(notification.getHtmlContent())
                .recipientAddress(notification.getRecipientAddress())
                .senderAddress(notification.getSenderAddress())
                .templateId(notification.getTemplateId())
                .templateVariables(notification.getTemplateVariables())
                .metadata(notification.getMetadata())
                .scheduledAt(notification.getScheduledAt())
                .sentAt(notification.getSentAt())
                .deliveredAt(notification.getDeliveredAt())
                .readAt(notification.getReadAt())
                .expiresAt(notification.getExpiresAt())
                .retryCount(notification.getRetryCount())
                .maxRetryAttempts(notification.getMaxRetryAttempts())
                .nextRetryAt(notification.getNextRetryAt())
                .errorMessage(notification.getErrorMessage())
                .externalId(notification.getExternalId())
                .correlationId(notification.getCorrelationId())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .createdBy(notification.getCreatedBy())
                .updatedBy(notification.getUpdatedBy())
                .build();
    }

    /**
     * Convert Notification DTO to entity
     */
    public Notification toEntity(NotificationDto dto) {
        if (dto == null) {
            return null;
        }

        return Notification.builder()
                .id(dto.getId())
                .userId(dto.getUserId())
                .type(dto.getType())
                .channel(dto.getChannel())
                .status(dto.getStatus())
                .priority(dto.getPriority())
                .subject(dto.getSubject())
                .content(dto.getContent())
                .htmlContent(dto.getHtmlContent())
                .recipientAddress(dto.getRecipientAddress())
                .senderAddress(dto.getSenderAddress())
                .templateId(dto.getTemplateId())
                .templateVariables(dto.getTemplateVariables())
                .metadata(dto.getMetadata())
                .scheduledAt(dto.getScheduledAt())
                .sentAt(dto.getSentAt())
                .deliveredAt(dto.getDeliveredAt())
                .readAt(dto.getReadAt())
                .expiresAt(dto.getExpiresAt())
                .retryCount(dto.getRetryCount())
                .maxRetryAttempts(dto.getMaxRetryAttempts())
                .nextRetryAt(dto.getNextRetryAt())
                .errorMessage(dto.getErrorMessage())
                .externalId(dto.getExternalId())
                .correlationId(dto.getCorrelationId())
                .referenceType(dto.getReferenceType())
                .referenceId(dto.getReferenceId())
                .build();
    }
}
