package org.de013.notificationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.de013.notificationservice.dto.*;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.service.NotificationService;
import org.de013.common.constant.ApiPaths;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Simple REST Controller for Notification operations
 */
@RestController
@RequestMapping(ApiPaths.NOTIFICATIONS)
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Notifications", description = "Simple notification management - Email and SMS only")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Send email notification
     */
    @PostMapping(ApiPaths.SEND_EMAIL)
    @Operation(
        summary = "Send email notification",
        description = """
            Send an email notification to a user. This endpoint accepts email details and sends
            the notification via SMTP. The notification status is tracked in the database.

            **Required fields:**
            - to: Valid email address
            - subject: Email subject (max 200 characters)
            - message: Email content (max 5000 characters)

            **Optional fields:**
            - userId: For tracking purposes
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Email notification details",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = EmailNotificationRequest.class),
                examples = @ExampleObject(
                    name = "Email notification example",
                    value = """
                        {
                          "userId": 123,
                          "to": "user@example.com",
                          "subject": "Welcome to our platform!",
                          "message": "Thank you for joining our platform. We're excited to have you!"
                        }
                        """
                )
            )
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Email sent successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = NotificationResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<NotificationResponse> sendEmail(
            @Valid @RequestBody EmailNotificationRequest request) {

        try {
            log.info("Sending email to: {}, subject: {}", request.getTo(), request.getSubject());

            Notification notification = notificationService.sendEmail(
                request.getUserId(),
                request.getTo(),
                request.getSubject(),
                request.getMessage()
            );

            NotificationResponse response = NotificationResponse.success(
                "Email sent successfully",
                notification.getId(),
                notification.getStatus().toString()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error sending email: {}", e.getMessage(), e);
            NotificationResponse response = NotificationResponse.error(
                "Failed to send email: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Send SMS notification
     */
    @PostMapping(ApiPaths.SEND_SMS)
    @Operation(
        summary = "Send SMS notification",
        description = """
            Send an SMS notification to a user. This endpoint accepts SMS details and sends
            the notification via Twilio (or mock mode for development). The notification status is tracked in the database.

            **Required fields:**
            - phoneNumber: Valid phone number (E.164 format recommended)
            - message: SMS content (max 160 characters)

            **Optional fields:**
            - userId: For tracking purposes
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "SMS notification details",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = SmsNotificationRequest.class),
                examples = @ExampleObject(
                    name = "SMS notification example",
                    value = """
                        {
                          "userId": 123,
                          "phoneNumber": "+1234567890",
                          "message": "Your verification code is: 123456"
                        }
                        """
                )
            )
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "SMS sent successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = NotificationResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<NotificationResponse> sendSms(
            @Valid @RequestBody SmsNotificationRequest request) {

        try {
            log.info("Sending SMS to: {}", request.getPhoneNumber());

            Notification notification = notificationService.sendSms(
                request.getUserId(),
                request.getPhoneNumber(),
                request.getMessage()
            );

            NotificationResponse response = NotificationResponse.success(
                "SMS sent successfully",
                notification.getId(),
                notification.getStatus().toString()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error sending SMS: {}", e.getMessage(), e);
            NotificationResponse response = NotificationResponse.error(
                "Failed to send SMS: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Send both email and SMS notification
     */
    @PostMapping(ApiPaths.SEND_BOTH)
    @Operation(
        summary = "Send both email and SMS",
        description = """
            Send both email and SMS notifications to a user simultaneously. This is useful for
            critical notifications that need to reach the user through multiple channels.

            **Required fields:**
            - email: Valid email address
            - phoneNumber: Valid phone number (E.164 format recommended)
            - subject: Email subject (max 200 characters)
            - message: Content for both email and SMS (max 160 characters for SMS compatibility)

            **Optional fields:**
            - userId: For tracking purposes
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Both email and SMS notification details",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BothNotificationRequest.class),
                examples = @ExampleObject(
                    name = "Both notifications example",
                    value = """
                        {
                          "userId": 123,
                          "email": "user@example.com",
                          "phoneNumber": "+1234567890",
                          "subject": "Account Verification",
                          "message": "Your verification code is: 123456. Please enter this code to verify your account."
                        }
                        """
                )
            )
        )
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Both notifications sent successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = BothNotificationResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<BothNotificationResponse> sendBoth(
            @Valid @RequestBody BothNotificationRequest request) {

        try {
            log.info("Sending both email and SMS to user: {}", request.getUserId());

            List<Notification> notifications = notificationService.sendBoth(
                request.getUserId(),
                request.getEmail(),
                request.getPhoneNumber(),
                request.getSubject(),
                request.getMessage()
            );

            BothNotificationResponse response = BothNotificationResponse.success(
                "Both notifications sent successfully",
                notifications.get(0).getId(), notifications.get(0).getStatus().toString(),
                notifications.get(1).getId(), notifications.get(1).getStatus().toString()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error sending both notifications: {}", e.getMessage(), e);
            BothNotificationResponse response = BothNotificationResponse.error(
                "Failed to send notifications: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get notification by ID
     */
    @GetMapping(ApiPaths.ID_PARAM)
    @Operation(
        summary = "Get notification by ID",
        description = """
            Retrieve a specific notification by its ID. Returns detailed information about
            the notification including its status, content, and timestamps.

            **Parameters:**
            - id: The ID of the notification to retrieve

            **Returns:**
            - Complete notification details including status and timestamps
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Notification found successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class),
                examples = @ExampleObject(
                    name = "Notification response",
                    value = """
                        {
                          "success": true,
                          "notification": {
                            "id": 123,
                            "userId": 456,
                            "channel": "EMAIL",
                            "recipient": "user@example.com",
                            "subject": "Welcome!",
                            "content": "Thank you for joining our platform.",
                            "status": "SENT",
                            "createdAt": "2023-12-07T10:30:00",
                            "sentAt": "2023-12-07T10:30:05"
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<Map<String, Object>> getNotification(
            @Parameter(description = "Notification ID", example = "123", required = true)
            @PathVariable Long id) {

        log.debug("Getting notification with id={}", id);

        return notificationService.findById(id)
                .map(notification -> {
                    Map<String, Object> response = Map.of(
                        "success", true,
                        "notification", Map.of(
                            "id", notification.getId(),
                            "userId", notification.getUserId(),
                            "channel", notification.getChannel().toString(),
                            "recipient", notification.getRecipient(),
                            "subject", notification.getSubject() != null ? notification.getSubject() : "",
                            "content", notification.getContent(),
                            "status", notification.getStatus().toString(),
                            "createdAt", notification.getCreatedAt(),
                            "sentAt", notification.getSentAt()
                        )
                    );
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get notifications for a user (simplified)
     */
    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM)
    @Operation(
        summary = "Get user notifications",
        description = """
            Retrieve paginated notifications for a specific user. Returns notifications ordered by creation date (newest first).

            **Parameters:**
            - userId: The ID of the user whose notifications to retrieve
            - page: Page number (0-based, default: 0)
            - size: Number of notifications per page (default: 20, max: 100)
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Notifications retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getUserNotifications(
            @Parameter(description = "User ID", example = "123", required = true)
            @PathVariable Long userId,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) int size) {

        log.debug("Getting notifications for user={}, page={}, size={}", userId, page, size);

        try {
            // Limit page size to prevent abuse
            int limitedSize = Math.min(size, 100);
            Pageable pageable = Pageable.ofSize(limitedSize).withPage(page);
            Page<Notification> notifications = notificationService.findByUserId(userId, pageable);

            List<Map<String, Object>> notificationList = notifications.getContent().stream()
                    .map(notification -> {
                        Map<String, Object> map = new java.util.HashMap<>();
                        map.put("id", notification.getId());
                        map.put("channel", notification.getChannel().toString());
                        map.put("recipient", notification.getRecipient());
                        map.put("subject", notification.getSubject() != null ? notification.getSubject() : "");
                        map.put("content", notification.getContent());
                        map.put("status", notification.getStatus().toString());
                        map.put("createdAt", notification.getCreatedAt());
                        map.put("sentAt", notification.getSentAt());
                        return map;
                    })
                    .toList();

            Map<String, Object> response = Map.of(
                "success", true,
                "notifications", notificationList,
                "totalElements", notifications.getTotalElements(),
                "totalPages", notifications.getTotalPages(),
                "currentPage", page,
                "pageSize", limitedSize
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting user notifications: {}", e.getMessage(), e);
            Map<String, Object> response = Map.of(
                "success", false,
                "message", "Failed to get notifications: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Mark notification as read
     */
    @PutMapping(ApiPaths.ID_PARAM + ApiPaths.READ)
    @Operation(
        summary = "Mark notification as read",
        description = """
            Mark a specific notification as read by the user. This updates the notification status
            and can be used to track which notifications the user has seen.

            **Parameters:**
            - id: The ID of the notification to mark as read
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Notification marked as read successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class)
            )
        ),
        @ApiResponse(responseCode = "404", description = "Notification not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> markAsRead(
            @Parameter(description = "Notification ID", example = "123", required = true)
            @PathVariable Long id) {

        log.info("Marking notification as read: id={}", id);

        try {
            notificationService.markAsRead(id);
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Notification marked as read"
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error marking notification as read: {}", e.getMessage(), e);
            Map<String, Object> response = Map.of(
                "success", false,
                "message", "Failed to mark as read: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get unread notification count for user
     */
    @GetMapping(ApiPaths.USER + ApiPaths.USER_ID_PARAM + ApiPaths.UNREAD_COUNT)
    @Operation(
        summary = "Get unread notification count",
        description = """
            Get the count of unread notifications for a specific user. This is useful for
            displaying notification badges or indicators in the UI.

            **Parameters:**
            - userId: The ID of the user whose unread count to retrieve

            **Returns:**
            - unreadCount: Number of unread notifications for the user
            """
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Unread count retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class),
                examples = @ExampleObject(
                    name = "Unread count response",
                    value = """
                        {
                          "success": true,
                          "unreadCount": 5
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> getUnreadCount(
            @Parameter(description = "User ID", example = "123", required = true)
            @PathVariable Long userId) {

        log.debug("Getting unread count for user={}", userId);

        try {
            long unreadCount = notificationService.countUnreadByUserId(userId);
            Map<String, Object> response = Map.of(
                "success", true,
                "unreadCount", unreadCount
            );
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting unread count: {}", e.getMessage(), e);
            Map<String, Object> response = Map.of(
                "success", false,
                "message", "Failed to get unread count: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

}
