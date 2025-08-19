package org.de013.orderservice.repository.custom.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.de013.orderservice.entity.OrderAudit;
import org.de013.orderservice.repository.custom.OrderAuditRepositoryCustom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderAuditRepositoryImpl implements OrderAuditRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<OrderAudit> searchAudit(Long orderId, String action, Long actorUserId, LocalDateTime start, LocalDateTime end, String ipContains, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<OrderAudit> cq = cb.createQuery(OrderAudit.class);
        Root<OrderAudit> root = cq.from(OrderAudit.class);

        List<Predicate> preds = new ArrayList<>();
        if (orderId != null) preds.add(cb.equal(root.get("order").get("id"), orderId));
        if (StringUtils.hasText(action)) preds.add(cb.equal(root.get("action"), action));
        if (actorUserId != null) preds.add(cb.equal(root.get("actorUserId"), actorUserId));
        if (start != null) preds.add(cb.greaterThanOrEqualTo(root.get("actionAt"), start));
        if (end != null) preds.add(cb.lessThanOrEqualTo(root.get("actionAt"), end));
        if (StringUtils.hasText(ipContains)) preds.add(cb.like(cb.lower(root.get("ipAddress")), "%" + ipContains.toLowerCase() + "%"));

        if (!preds.isEmpty()) cq.where(cb.and(preds.toArray(new Predicate[0])));
        cq.orderBy(cb.desc(root.get("actionAt")));

        TypedQuery<OrderAudit> tq = em.createQuery(cq);
        tq.setFirstResult((int) pageable.getOffset());
        tq.setMaxResults(pageable.getPageSize());
        List<OrderAudit> content = tq.getResultList();

        CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
        Root<OrderAudit> countRoot = countQ.from(OrderAudit.class);
        if (!preds.isEmpty()) countQ.where(cb.and(preds.toArray(new Predicate[0])));
        countQ.select(cb.count(countRoot));
        Long total = em.createQuery(countQ).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<OrderAudit> findRecentActionsForOrder(Long orderId, int limit) {
        return em.createQuery("SELECT oa FROM OrderAudit oa WHERE oa.order.id = :orderId ORDER BY oa.actionAt DESC", OrderAudit.class)
            .setParameter("orderId", orderId)
            .setMaxResults(limit)
            .getResultList();
    }
}

