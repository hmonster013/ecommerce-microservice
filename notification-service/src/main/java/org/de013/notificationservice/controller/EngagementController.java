package org.de013.notificationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.event.publisher.UserEngagementEventPublisher;
import org.de013.notificationservice.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for handling user engagement with notifications
 */
@RestController
@RequestMapping("/api/v1/notifications/engagement")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notification Engagement", description = "Track user engagement with notifications")
public class EngagementController {

    private final UserEngagementEventPublisher engagementEventPublisher;
    private final NotificationRepository notificationRepository;

    /**
     * Track email opened
     */
    @GetMapping("/email/{notificationId}/opened")
    @Operation(summary = "Track email opened", description = "Track when a user opens an email notification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Email open tracked successfully"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<byte[]> trackEmailOpened(
            @Parameter(description = "Notification ID") @PathVariable Long notificationId,
            HttpServletRequest request) {
        
        log.info("Tracking email opened: notificationId={}", notificationId);

        try {
            Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
            if (notificationOpt.isEmpty()) {
                log.warn("Notification not found for email tracking: id={}", notificationId);
                return ResponseEntity.notFound().build();
            }

            Notification notification = notificationOpt.get();
            
            // Mark notification as read if not already
            if (notification.getStatus() != NotificationStatus.READ) {
                notification.markAsRead();
                notificationRepository.save(notification);
            }

            // Publish engagement event
            String userAgent = request.getHeader("User-Agent");
            String ipAddress = getClientIpAddress(request);
            
            engagementEventPublisher.publishEmailOpened(
                    notificationId,
                    notification.getUserId(),
                    notification.getType(),
                    userAgent,
                    ipAddress,
                    notification.getCorrelationId()
            );

            // Return 1x1 transparent pixel
            byte[] pixel = new byte[]{
                (byte) 0x47, (byte) 0x49, (byte) 0x46, (byte) 0x38, (byte) 0x39, (byte) 0x61,
                (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x80, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0x21, (byte) 0xF9, (byte) 0x04, (byte) 0x01, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x2C, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x00,
                (byte) 0x00, (byte) 0x02, (byte) 0x02, (byte) 0x04, (byte) 0x01, (byte) 0x00,
                (byte) 0x3B
            };

            return ResponseEntity.ok()
                    .header("Content-Type", "image/gif")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .body(pixel);
            
        } catch (Exception e) {
            log.error("Error tracking email opened: notificationId={}, error={}", notificationId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Track link clicked
     */
    @GetMapping("/link/{notificationId}/clicked")
    @Operation(summary = "Track link clicked", description = "Track when a user clicks a link in a notification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "302", description = "Redirect to target URL"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<Void> trackLinkClicked(
            @Parameter(description = "Notification ID") @PathVariable Long notificationId,
            @Parameter(description = "Target URL") @RequestParam String url,
            HttpServletRequest request) {
        
        log.info("Tracking link clicked: notificationId={}, url={}", notificationId, url);

        try {
            Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
            if (notificationOpt.isEmpty()) {
                log.warn("Notification not found for link tracking: id={}", notificationId);
                return ResponseEntity.notFound().build();
            }

            Notification notification = notificationOpt.get();

            // Publish engagement event
            String userAgent = request.getHeader("User-Agent");
            String ipAddress = getClientIpAddress(request);
            
            engagementEventPublisher.publishLinkClicked(
                    notificationId,
                    notification.getUserId(),
                    notification.getType(),
                    notification.getChannel(),
                    url,
                    userAgent,
                    ipAddress,
                    notification.getCorrelationId()
            );

            // Redirect to target URL
            return ResponseEntity.status(302)
                    .header("Location", url)
                    .build();
            
        } catch (Exception e) {
            log.error("Error tracking link clicked: notificationId={}, error={}", notificationId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Track push notification opened
     */
    @PostMapping("/push/{notificationId}/opened")
    @Operation(summary = "Track push opened", description = "Track when a user opens a push notification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Push open tracked successfully"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> trackPushOpened(
            @Parameter(description = "Notification ID") @PathVariable Long notificationId,
            @RequestBody Map<String, String> payload) {
        
        log.info("Tracking push opened: notificationId={}", notificationId);

        try {
            Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
            if (notificationOpt.isEmpty()) {
                log.warn("Notification not found for push tracking: id={}", notificationId);
                org.de013.common.dto.ApiResponse<String> response = 
                        org.de013.common.dto.ApiResponse.error("Notification not found");
                return ResponseEntity.status(404).body(response);
            }

            Notification notification = notificationOpt.get();
            
            // Mark notification as read if not already
            if (notification.getStatus() != NotificationStatus.READ) {
                notification.markAsRead();
                notificationRepository.save(notification);
            }

            // Publish engagement event
            String deviceType = payload.getOrDefault("deviceType", "UNKNOWN");
            String platform = payload.getOrDefault("platform", "UNKNOWN");
            
            engagementEventPublisher.publishPushOpened(
                    notificationId,
                    notification.getUserId(),
                    notification.getType(),
                    deviceType,
                    platform,
                    notification.getCorrelationId()
            );

            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.success("Push open tracked successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error tracking push opened: notificationId={}, error={}", notificationId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to track push open: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Track unsubscribe
     */
    @PostMapping("/unsubscribe/{notificationId}")
    @Operation(summary = "Track unsubscribe", description = "Track when a user unsubscribes from notifications")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Unsubscribe tracked successfully"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> trackUnsubscribe(
            @Parameter(description = "Notification ID") @PathVariable Long notificationId) {
        
        log.info("Tracking unsubscribe: notificationId={}", notificationId);

        try {
            Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
            if (notificationOpt.isEmpty()) {
                log.warn("Notification not found for unsubscribe tracking: id={}", notificationId);
                org.de013.common.dto.ApiResponse<String> response = 
                        org.de013.common.dto.ApiResponse.error("Notification not found");
                return ResponseEntity.status(404).body(response);
            }

            Notification notification = notificationOpt.get();

            // Publish engagement event
            engagementEventPublisher.publishUnsubscribe(
                    notificationId,
                    notification.getUserId(),
                    notification.getType(),
                    notification.getChannel(),
                    notification.getCorrelationId()
            );

            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.success("Unsubscribe tracked successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error tracking unsubscribe: notificationId={}, error={}", notificationId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to track unsubscribe: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Track spam report
     */
    @PostMapping("/spam/{notificationId}")
    @Operation(summary = "Track spam report", description = "Track when a user reports a notification as spam")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Spam report tracked successfully"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> trackSpamReport(
            @Parameter(description = "Notification ID") @PathVariable Long notificationId) {
        
        log.warn("Tracking spam report: notificationId={}", notificationId);

        try {
            Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
            if (notificationOpt.isEmpty()) {
                log.warn("Notification not found for spam tracking: id={}", notificationId);
                org.de013.common.dto.ApiResponse<String> response = 
                        org.de013.common.dto.ApiResponse.error("Notification not found");
                return ResponseEntity.status(404).body(response);
            }

            Notification notification = notificationOpt.get();

            // Publish engagement event
            engagementEventPublisher.publishSpamReport(
                    notificationId,
                    notification.getUserId(),
                    notification.getType(),
                    notification.getChannel(),
                    notification.getCorrelationId()
            );

            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.success("Spam report tracked successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error tracking spam report: notificationId={}, error={}", notificationId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to track spam report: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
