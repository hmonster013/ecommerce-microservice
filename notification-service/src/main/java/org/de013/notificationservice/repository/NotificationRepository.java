package org.de013.notificationservice.repository;

import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Simple Repository interface for Notification entity
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find notifications by user ID
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Count notifications by user and status
     */
    long countByUserIdAndStatus(Long userId, NotificationStatus status);

}
