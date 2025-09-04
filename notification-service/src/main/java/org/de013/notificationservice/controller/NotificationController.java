package org.de013.notificationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.de013.notificationservice.dto.CreateNotificationRequest;
import org.de013.notificationservice.dto.NotificationDto;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.mapper.NotificationMapper;
import org.de013.notificationservice.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;



/**
 * REST Controller for Notification operations
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Notification management operations")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    /**
     * Send a new notification
     */
    @PostMapping("/send")
    @Operation(summary = "Send a new notification", description = "Create and send a notification to a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Notification created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<NotificationDto>> sendNotification(
            @Valid @RequestBody CreateNotificationRequest request) {

        log.info("Received request to send notification for user={}, type={}, channel={}",
                request.getUserId(), request.getType(), request.getChannel());

        try {
            Notification notification = notificationService.createNotification(request);
            NotificationDto notificationDto = notificationMapper.toDto(notification);

            org.de013.common.dto.ApiResponse<NotificationDto> response =
                    org.de013.common.dto.ApiResponse.success(notificationDto);

            log.info("Notification sent successfully with id={}", notification.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error sending notification: {}", e.getMessage(), e);
            org.de013.common.dto.ApiResponse<NotificationDto> response =
                    org.de013.common.dto.ApiResponse.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get notification by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get notification by ID", description = "Retrieve a specific notification by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notification found"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<NotificationDto>> getNotification(
            @Parameter(description = "Notification ID") @PathVariable Long id) {

        log.debug("Getting notification with id={}", id);

        return notificationService.findById(id)
                .map(notification -> {
                    NotificationDto dto = notificationMapper.toDto(notification);
                    return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(dto));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get notifications for a user
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user notifications", description = "Retrieve notifications for a specific user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notifications retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Page<NotificationDto>>> getUserNotifications(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {

        log.debug("Getting notifications for user={}, page={}, size={}", userId, page, size);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Notification> notifications = notificationService.findByUserId(userId, pageable);
        Page<NotificationDto> notificationDtos = notifications.map(notificationMapper::toDto);

        org.de013.common.dto.ApiResponse<Page<NotificationDto>> response =
                org.de013.common.dto.ApiResponse.success(notificationDtos);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark notification as read
     */
    @PutMapping("/{id}/read")
    @Operation(summary = "Mark notification as read", description = "Mark a notification as read by the user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notification marked as read"),
        @ApiResponse(responseCode = "404", description = "Notification not found"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> markAsRead(
            @Parameter(description = "Notification ID") @PathVariable Long id,
            @Parameter(description = "User ID") @RequestParam Long userId) {

        log.info("Marking notification as read: id={}, userId={}", id, userId);

        try {
            notificationService.markAsRead(id, userId);
            org.de013.common.dto.ApiResponse<String> response =
                    org.de013.common.dto.ApiResponse.success("Notification marked as read");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error marking notification as read: {}", e.getMessage(), e);
            org.de013.common.dto.ApiResponse<String> response =
                    org.de013.common.dto.ApiResponse.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get unread notification count for user
     */
    @GetMapping("/user/{userId}/unread-count")
    @Operation(summary = "Get unread notification count", description = "Get the count of unread notifications for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Unread count retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Long>> getUnreadCount(
            @Parameter(description = "User ID") @PathVariable Long userId) {

        log.debug("Getting unread count for user={}", userId);

        long unreadCount = notificationService.countUnreadByUserId(userId);
        org.de013.common.dto.ApiResponse<Long> response =
                org.de013.common.dto.ApiResponse.success(unreadCount);
        return ResponseEntity.ok(response);
    }

    /**
     * Get notifications by correlation ID
     */
    @GetMapping("/correlation/{correlationId}")
    @Operation(summary = "Get notifications by correlation ID", description = "Retrieve notifications by correlation ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Notifications retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<NotificationDto>>> getNotificationsByCorrelationId(
            @Parameter(description = "Correlation ID") @PathVariable String correlationId) {

        log.debug("Getting notifications by correlationId={}", correlationId);

        List<Notification> notifications = notificationService.findByCorrelationId(correlationId);
        List<NotificationDto> notificationDtos = notifications.stream()
                .map(notificationMapper::toDto)
                .toList();

        org.de013.common.dto.ApiResponse<List<NotificationDto>> response =
                org.de013.common.dto.ApiResponse.success(notificationDtos);
        return ResponseEntity.ok(response);
    }

    /**
     * Bulk send notifications
     */
    @PostMapping("/bulk-send")
    @Operation(summary = "Bulk send notifications", description = "Send multiple notifications at once")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Notifications created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<NotificationDto>>> bulkSendNotifications(
            @Valid @RequestBody List<CreateNotificationRequest> requests) {

        log.info("Received bulk send request for {} notifications", requests.size());

        try {
            List<NotificationDto> results = requests.stream()
                    .map(request -> {
                        try {
                            Notification notification = notificationService.createNotification(request);
                            return notificationMapper.toDto(notification);
                        } catch (Exception e) {
                            log.error("Error creating notification in bulk: {}", e.getMessage(), e);
                            // Return null for failed notifications, filter them out later
                            return null;
                        }
                    })
                    .filter(dto -> dto != null)
                    .toList();

            org.de013.common.dto.ApiResponse<List<NotificationDto>> response =
                    org.de013.common.dto.ApiResponse.success(results);

            log.info("Bulk send completed: {} out of {} notifications created successfully",
                    results.size(), requests.size());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error in bulk send: {}", e.getMessage(), e);
            org.de013.common.dto.ApiResponse<List<NotificationDto>> response =
                    org.de013.common.dto.ApiResponse.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
