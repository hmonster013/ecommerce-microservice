package org.de013.notificationservice.delivery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.de013.notificationservice.entity.enums.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Result of a delivery attempt
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryResult {

    private boolean success;
    private DeliveryStatus status;
    private String externalId;
    private String providerMessageId;
    private String responseCode;
    private String responseMessage;
    private String errorMessage;
    private Long processingTimeMs;
    private Integer costCents;
    private LocalDateTime deliveredAt;
    private Map<String, Object> providerResponse;
    private Map<String, Object> metadata;

    /**
     * Create successful delivery result
     */
    public static DeliveryResult success(String externalId, String providerMessageId) {
        return DeliveryResult.builder()
                .success(true)
                .status(DeliveryStatus.SUCCESS)
                .externalId(externalId)
                .providerMessageId(providerMessageId)
                .deliveredAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create successful delivery result with additional data
     */
    public static DeliveryResult success(String externalId, String providerMessageId, 
                                       String responseCode, Long processingTimeMs) {
        return DeliveryResult.builder()
                .success(true)
                .status(DeliveryStatus.SUCCESS)
                .externalId(externalId)
                .providerMessageId(providerMessageId)
                .responseCode(responseCode)
                .processingTimeMs(processingTimeMs)
                .deliveredAt(LocalDateTime.now())
                .build();
    }

    /**
     * Create failed delivery result
     */
    public static DeliveryResult failure(DeliveryStatus status, String errorMessage) {
        return DeliveryResult.builder()
                .success(false)
                .status(status)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * Create failed delivery result with response details
     */
    public static DeliveryResult failure(DeliveryStatus status, String errorMessage, 
                                       String responseCode, String responseMessage) {
        return DeliveryResult.builder()
                .success(false)
                .status(status)
                .errorMessage(errorMessage)
                .responseCode(responseCode)
                .responseMessage(responseMessage)
                .build();
    }

    /**
     * Create pending delivery result
     */
    public static DeliveryResult pending(String externalId) {
        return DeliveryResult.builder()
                .success(true)
                .status(DeliveryStatus.PENDING)
                .externalId(externalId)
                .build();
    }

    /**
     * Create in-progress delivery result
     */
    public static DeliveryResult inProgress(String externalId, String providerMessageId) {
        return DeliveryResult.builder()
                .success(true)
                .status(DeliveryStatus.IN_PROGRESS)
                .externalId(externalId)
                .providerMessageId(providerMessageId)
                .build();
    }
}
