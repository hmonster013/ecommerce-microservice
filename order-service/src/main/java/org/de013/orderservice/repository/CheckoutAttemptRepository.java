package org.de013.orderservice.repository;

import org.de013.orderservice.entity.CheckoutAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutAttemptRepository extends JpaRepository<CheckoutAttempt, String> {}
