package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.entity.CheckoutAttempt;
import org.de013.orderservice.repository.CheckoutAttemptRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Repository
@RequiredArgsConstructor
public class CheckoutAttemptStore {
    private final CheckoutAttemptRepository attempts;
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(String key, String userId) {
        if (attempts.existsById(key)) throw new org.springframework.dao.DuplicateKeyException("Checkout already reserved");
        entityManager.persist(new CheckoutAttempt(key, userId));
        entityManager.flush();
    }
}
