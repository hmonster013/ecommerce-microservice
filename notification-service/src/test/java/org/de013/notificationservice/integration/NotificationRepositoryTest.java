package org.de013.notificationservice.integration;

import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void testCreateAndFindNotification() {
        Notification notification = Notification.builder()
                .userId("user-uuid-abc")
                .recipient("john@example.com")
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .subject("Order Confirmed")
                .content("Your order has been placed successfully")
                .build();

        Notification saved = notificationRepository.save(notification);
        assertNotNull(saved.getId());

        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                "user-uuid-abc", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("Order Confirmed", page.getContent().get(0).getSubject());

        long count = notificationRepository.countByUserIdAndStatus("user-uuid-abc", NotificationStatus.PENDING);
        assertEquals(1, count);
    }
}
