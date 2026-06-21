package org.de013.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign client for Notification Service integration
 */
@FeignClient(name = "notification-service", path = "/notifications")
public interface NotificationServiceClient {

    /**
     * Send email notification
     */
    @PostMapping("/send-email")
    ResponseEntity<Object> sendEmail(@RequestBody Map<String, Object> request);
}
