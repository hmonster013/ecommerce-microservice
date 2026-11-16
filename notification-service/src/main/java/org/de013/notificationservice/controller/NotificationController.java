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
import org.de013.notificationservice.dto.BothNotificationRequest;
import org.de013.notificationservice.dto.BothNotificationResponse;
import org.de013.notificationservice.dto.EmailNotificationRequest;
import org.de013.notificationservice.dto.NotificationResponse;
import org.de013.notificationservice.dto.SmsNotificationRequest;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import java.time.LocalDateTime;
import org.de013.notificationservice.security.HeaderAuthenticationFilter.HeaderUserDetails;
import org.de013.notificationservice.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Simple REST Controller for Notification operations
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Notifications", description = "Simple notification management - Email and SMS only")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Send email notification
     */
    @PostMapping("/send-email")
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
                    description = "Email submitted to provider",
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


            Notification notification = notificationService.sendEmail(
                    request.userId(),
                    request.to(),
                    request.subject(),
                    request.message()
            );

            boolean submitted = notification.getStatus() == NotificationStatus.SENT;
            NotificationResponse response = new NotificationResponse(submitted,
                    submitted ? "Email submitted to provider" : "Email submission failed",
                    notification.getId(), notification.getStatus().toString(), LocalDateTime.now());
            return ResponseEntity.status(submitted ? HttpStatus.CREATED : HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } catch (Exception e) {
            log.error("Error processing email notification");
            NotificationResponse response = NotificationResponse.error(
                    "Failed to process email notification"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Send SMS notification
     */
    @PostMapping("/send-sms")
    @Operation(
            summary = "Send SMS notification",
            description = """
                    Send an SMS notification to a user. This endpoint accepts SMS details and sends
                    the notification via a configured provider. The notification status is tracked in the database.
                    
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
                    description = "SMS submitted to provider",
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


            Notification notification = notificationService.sendSms(
                    request.userId(),
                    request.phoneNumber(),
                    request.message()
            );

            boolean submitted = notification.getStatus() == NotificationStatus.SENT;
            NotificationResponse response = new NotificationResponse(submitted,
                    submitted ? "SMS submitted to provider" : "SMS submission failed",
                    notification.getId(), notification.getStatus().toString(), LocalDateTime.now());
            return ResponseEntity.status(submitted ? HttpStatus.CREATED : HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } catch (Exception e) {
            log.error("Error processing SMS notification");
            NotificationResponse response = NotificationResponse.error(
                    "Failed to process SMS notification"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Send both email and SMS notification
     */
    @PostMapping("/send-both")
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
                    description = "Both notifications submitted to providers",
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


            List<Notification> notifications = notificationService.sendBoth(
                    request.userId(),
                    request.email(),
                    request.phoneNumber(),
                    request.subject(),
                    request.message()
            );

            Notification email = notifications.get(0);
            Notification sms = notifications.get(1);
            boolean submitted = email.getStatus() == NotificationStatus.SENT && sms.getStatus() == NotificationStatus.SENT;
            BothNotificationResponse response = new BothNotificationResponse(submitted,
                    submitted ? "Both notifications submitted to providers" : "Notification submission failed",
                    email.getId(), sms.getId(), email.getStatus().toString(), sms.getStatus().toString(), LocalDateTime.now());
            return ResponseEntity.status(submitted ? HttpStatus.CREATED : HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } catch (Exception e) {
            log.error("Error processing both notifications notification");
            BothNotificationResponse response = BothNotificationResponse.error(
                    "Failed to process notifications"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get notification by ID
     */
    @GetMapping("/{id}")
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

        if (getCurrentUserDetails() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Access denied"));
        }
        return notificationService.findById(id)
                .map(notification -> {
                    if (!isCurrentUserOrAdmin(notification.getUserId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<Map<String, Object>>body(Map.of("success", false, "message", "Access denied"));
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", notification.getId());
                    data.put("userId", notification.getUserId());
                    data.put("channel", notification.getChannel().toString());
                    data.put("recipient", notification.getRecipient());
                    data.put("subject", notification.getSubject() != null ? notification.getSubject() : "");
                    data.put("content", notification.getContent());
                    data.put("status", notification.getStatus().toString());
                    data.put("createdAt", notification.getCreatedAt());
                    data.put("sentAt", notification.getSentAt());
                    Map<String, Object> response = Map.of(
                            "success", true,
                            "notification", data
                    );
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Helper methods for security & context
     */
    private HeaderUserDetails getCurrentUserDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getDetails() instanceof HeaderUserDetails details && StringUtils.hasText(details.getKeycloakId())) {
            return details;
        }
        return null;
    }

    private String getCurrentUserKeycloakId() {
        HeaderUserDetails details = getCurrentUserDetails();
        return details != null ? details.getKeycloakId() : null;
    }

    private boolean isCurrentUserOrAdmin(String targetUserId) {
        HeaderUserDetails details = getCurrentUserDetails();
        if (details == null) {
            return false;
        }
        if (details.getKeycloakId().equals(targetUserId)) {
            return true;
        }
        List<String> roles = details.getRoles();
        return roles != null && (roles.contains("ROLE_ADMIN") || roles.contains("ADMIN") ||
                roles.contains("ROLE_SUPPORT") || roles.contains("SUPPORT"));
    }

    /**
     * Get notifications for currently authenticated user
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user notifications", description = "Retrieve paginated notifications for authenticated user")
    public ResponseEntity<Map<String, Object>> getMyNotifications(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        String currentUserId = getCurrentUserKeycloakId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "User context is missing"
            ));
        }
        return getUserNotifications(currentUserId, page, size);
    }

    /**
     * Get unread notification count for currently authenticated user
     */
    @GetMapping("/me/unread-count")
    @Operation(summary = "Get current user unread notification count", description = "Retrieve unread notification count for authenticated user")
    public ResponseEntity<Map<String, Object>> getMyUnreadCount() {
        String currentUserId = getCurrentUserKeycloakId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "User context is missing"
            ));
        }
        return getUnreadCount(currentUserId);
    }

    /**
     * Get notifications for a user (simplified)
     */
    @GetMapping("/user/{userId}")
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
            @PathVariable String userId,
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (max 100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) int size) {

        log.debug("Getting notifications for user={}, page={}, size={}", userId, page, size);

        if (!isCurrentUserOrAdmin(userId)) {
            log.warn("Access denied: user {} attempted to access notifications for user {}", getCurrentUserKeycloakId(), userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Access denied"
            ));
        }

        try {
            // Limit page size to prevent abuse
            int limitedSize = Math.min(size, 100);
            Pageable pageable = Pageable.ofSize(limitedSize).withPage(page);
            Page<Notification> notifications = notificationService.findByUserId(userId, pageable);

            List<Map<String, Object>> notificationList = notifications.getContent().stream()
                    .map(notification -> {
                        Map<String, Object> map = new HashMap<>();
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

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("notifications", notificationList);
            response.put("totalElements", notifications.getTotalElements());
            response.put("totalItems", notifications.getTotalElements());
            response.put("totalPages", notifications.getTotalPages());
            response.put("currentPage", page);
            response.put("pageSize", limitedSize);

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
    @PutMapping("/{id}/read")
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

        if (getCurrentUserDetails() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "Access denied"));
        }
        log.info("Marking notification as read: id={}", id);

        try {
            Optional<Notification> notificationOpt = notificationService.findById(id);
            if (notificationOpt.isEmpty()) {
                log.warn("Notification not found while marking as read: id={}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "success", false,
                        "message", "Notification not found: " + id
                ));
            }

            Notification notification = notificationOpt.get();
            if (!isCurrentUserOrAdmin(notification.getUserId())) {
                log.warn("Access denied: user {} attempted to mark notification {} (owned by {}) as read",
                        getCurrentUserKeycloakId(), id, notification.getUserId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "Access denied"
                ));
            }

            notificationService.markAsRead(id);
            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Notification marked as read"
            );
            return ResponseEntity.ok(response);

        } catch (NoSuchElementException e) {
            log.warn("Notification not found while marking as read: id={}, message={}", id, e.getMessage());
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
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
    @GetMapping("/user/{userId}/unread-count")
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
            @PathVariable String userId) {

        log.debug("Getting unread count for user={}", userId);

        if (!isCurrentUserOrAdmin(userId)) {
            log.warn("Access denied: user {} attempted to access unread count for user {}", getCurrentUserKeycloakId(), userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "Access denied"
            ));
        }

        try {
            long unreadCount = notificationService.countUnreadByUserId(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("unreadCount", unreadCount);
            response.put("count", unreadCount);
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

