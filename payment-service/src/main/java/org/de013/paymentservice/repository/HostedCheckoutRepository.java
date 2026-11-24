package org.de013.paymentservice.repository;

import jakarta.persistence.LockModeType;
import org.de013.paymentservice.entity.HostedCheckout;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface HostedCheckoutRepository extends JpaRepository<HostedCheckout, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HostedCheckout c where c.orderId = :orderId")
    Optional<HostedCheckout> lockByOrderId(@org.springframework.data.repository.query.Param("orderId") Long orderId);
}
