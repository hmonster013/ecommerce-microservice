package org.de013.notificationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.NotificationPreference;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.service.NotificationPreferenceService;
import org.de013.notificationservice.service.OptOutService;
import org.de013.notificationservice.service.PersonalizationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for User Notification Preferences and Personalization
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/preferences")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Preferences", description = "Manage user notification preferences and personalization settings")
public class PreferenceController {

    private final NotificationPreferenceService preferenceService;
    private final OptOutService optOutService;
    private final PersonalizationService personalizationService;

    /**
     * Get all preferences for a user
     */
    @GetMapping
    @Operation(summary = "Get user preferences", description = "Get all notification preferences for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Preferences retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<List<NotificationPreference>>> getUserPreferences(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        
        log.info("Getting preferences for user: userId={}", userId);

        try {
            List<NotificationPreference> preferences = preferenceService.getUserPreferences(userId);
            org.de013.common.dto.ApiResponse<List<NotificationPreference>> response = 
                    org.de013.common.dto.ApiResponse.success(preferences);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting user preferences: userId={}, error={}", userId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<List<NotificationPreference>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get user preferences: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get specific preference for user, channel, and type
     */
    @GetMapping("/channel/{channel}/type/{type}")
    @Operation(summary = "Get specific preference", description = "Get notification preference for specific channel and type")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Preference retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Preference not found")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<NotificationPreference>> getSpecificPreference(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Notification channel") @PathVariable NotificationChannel channel,
            @Parameter(description = "Notification type") @PathVariable NotificationType type) {
        
        log.info("Getting specific preference: userId={}, channel={}, type={}", userId, channel, type);

        try {
            NotificationPreference preference = preferenceService.getPreference(userId, channel, type);
            if (preference == null) {
                org.de013.common.dto.ApiResponse<NotificationPreference> response = 
                        org.de013.common.dto.ApiResponse.error("Preference not found");
                return ResponseEntity.status(404).body(response);
            }
            
            org.de013.common.dto.ApiResponse<NotificationPreference> response = 
                    org.de013.common.dto.ApiResponse.success(preference);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting specific preference: userId={}, channel={}, type={}, error={}", 
                    userId, channel, type, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<NotificationPreference> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get preference: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Update user preferences
     */
    @PutMapping
    @Operation(summary = "Update user preferences", description = "Update notification preferences for a user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Preferences updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<NotificationPreference>> updatePreferences(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @RequestBody NotificationPreference preference) {
        
        log.info("Updating preferences for user: userId={}", userId);

        try {
            preference.setUserId(userId);
            NotificationPreference updatedPreference = preferenceService.updatePreference(preference);
            
            org.de013.common.dto.ApiResponse<NotificationPreference> response = 
                    org.de013.common.dto.ApiResponse.success(updatedPreference);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error updating user preferences: userId={}, error={}", userId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<NotificationPreference> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to update preferences: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Set global opt-out
     */
    @PostMapping("/opt-out/global")
    @Operation(summary = "Set global opt-out", description = "Opt user out of all notifications")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Global opt-out set successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> setGlobalOptOut(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        
        log.info("Setting global opt-out for user: userId={}", userId);

        try {
            String reason = request.getOrDefault("reason", "User requested opt-out");
            optOutService.setGlobalOptOut(userId, reason);
            
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.success("Global opt-out set successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error setting global opt-out: userId={}, error={}", userId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to set global opt-out: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Remove global opt-out
     */
    @DeleteMapping("/opt-out/global")
    @Operation(summary = "Remove global opt-out", description = "Remove global opt-out for user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Global opt-out removed successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> removeGlobalOptOut(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        
        log.info("Removing global opt-out for user: userId={}", userId);

        try {
            optOutService.removeGlobalOptOut(userId);
            
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.success("Global opt-out removed successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error removing global opt-out: userId={}, error={}", userId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to remove global opt-out: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Set channel-specific opt-out
     */
    @PostMapping("/opt-out/channel/{channel}")
    @Operation(summary = "Set channel opt-out", description = "Opt user out of specific channel notifications")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Channel opt-out set successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> setChannelOptOut(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Notification channel") @PathVariable NotificationChannel channel,
            @RequestBody Map<String, String> request) {
        
        log.info("Setting channel opt-out: userId={}, channel={}", userId, channel);

        try {
            String reason = request.getOrDefault("reason", "User requested channel opt-out");
            optOutService.setChannelOptOut(userId, channel, reason);
            
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.success("Channel opt-out set successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error setting channel opt-out: userId={}, channel={}, error={}", 
                    userId, channel, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to set channel opt-out: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Set marketing opt-out
     */
    @PostMapping("/opt-out/marketing")
    @Operation(summary = "Set marketing opt-out", description = "Opt user out of marketing notifications (GDPR/CAN-SPAM compliance)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Marketing opt-out set successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> setMarketingOptOut(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        
        log.info("Setting marketing opt-out for user: userId={}", userId);

        try {
            String reason = request.getOrDefault("reason", "User requested marketing opt-out");
            optOutService.setMarketingOptOut(userId, reason);
            
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.success("Marketing opt-out set successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error setting marketing opt-out: userId={}, error={}", userId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to set marketing opt-out: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Set temporary opt-out (snooze)
     */
    @PostMapping("/opt-out/temporary")
    @Operation(summary = "Set temporary opt-out", description = "Temporarily opt user out of notifications (snooze)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Temporary opt-out set successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> setTemporaryOptOut(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Snooze until (yyyy-MM-dd HH:mm:ss)") 
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime snoozeUntil,
            @RequestBody(required = false) Map<String, String> request) {
        
        log.info("Setting temporary opt-out: userId={}, snoozeUntil={}", userId, snoozeUntil);

        try {
            String reason = request != null ? request.getOrDefault("reason", "User requested temporary opt-out") : "User requested temporary opt-out";
            optOutService.setTemporaryOptOut(userId, snoozeUntil, reason);
            
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.success("Temporary opt-out set successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error setting temporary opt-out: userId={}, error={}", userId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to set temporary opt-out: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get opt-out status
     */
    @GetMapping("/opt-out/status")
    @Operation(summary = "Get opt-out status", description = "Get current opt-out status for user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Opt-out status retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<OptOutService.OptOutStatus>> getOptOutStatus(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        
        log.info("Getting opt-out status for user: userId={}", userId);

        try {
            OptOutService.OptOutStatus status = optOutService.getOptOutStatus(userId);
            org.de013.common.dto.ApiResponse<OptOutService.OptOutStatus> response = 
                    org.de013.common.dto.ApiResponse.success(status);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting opt-out status: userId={}, error={}", userId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<OptOutService.OptOutStatus> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get opt-out status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Set GDPR consent
     */
    @PostMapping("/gdpr-consent")
    @Operation(summary = "Set GDPR consent", description = "Set GDPR consent status for user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "GDPR consent set successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<String>> setGdprConsent(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @RequestBody Map<String, Boolean> request) {
        
        log.info("Setting GDPR consent for user: userId={}", userId);

        try {
            boolean consent = request.getOrDefault("consent", false);
            optOutService.setGdprConsent(userId, consent);
            
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.success("GDPR consent set successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error setting GDPR consent: userId={}, error={}", userId, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<String> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to set GDPR consent: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get A/B test group for user
     */
    @GetMapping("/ab-test/{testName}")
    @Operation(summary = "Get A/B test group", description = "Get A/B test group assignment for user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "A/B test group retrieved successfully")
    })
    public ResponseEntity<org.de013.common.dto.ApiResponse<Map<String, String>>> getAbTestGroup(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Test name") @PathVariable String testName) {
        
        log.info("Getting A/B test group: userId={}, testName={}", userId, testName);

        try {
            String group = personalizationService.getAbTestGroup(userId, testName);
            Map<String, String> result = Map.of("testName", testName, "group", group);
            
            org.de013.common.dto.ApiResponse<Map<String, String>> response = 
                    org.de013.common.dto.ApiResponse.success(result);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting A/B test group: userId={}, testName={}, error={}", 
                    userId, testName, e.getMessage(), e);
            org.de013.common.dto.ApiResponse<Map<String, String>> response = 
                    org.de013.common.dto.ApiResponse.error("Failed to get A/B test group: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
