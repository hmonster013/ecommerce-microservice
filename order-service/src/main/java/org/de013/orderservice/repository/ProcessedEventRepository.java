package org.de013.orderservice.repository;

import org.de013.orderservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for ProcessedEvent entity
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
