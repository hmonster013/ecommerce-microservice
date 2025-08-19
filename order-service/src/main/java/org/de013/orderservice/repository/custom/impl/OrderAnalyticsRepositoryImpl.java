package org.de013.orderservice.repository.custom.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.repository.custom.OrderAnalyticsRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Order Analytics Repository Implementation
 */
@Repository
public class OrderAnalyticsRepositoryImpl implements OrderAnalyticsRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public OrderMetrics getOrderMetrics(LocalDateTime startDate, LocalDateTime endDate) {
        OrderMetrics metrics = new OrderMetrics();

        // Total orders
        Long totalOrders = em.createQuery(
                "SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end",
                Long.class)
            .setParameter("start", startDate)
            .setParameter("end", endDate)
            .getSingleResult();
        metrics.setTotalOrders(totalOrders);

        // Completed orders (DELIVERED or COMPLETED)
        Long completedOrders = em.createQuery(
                "SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end " +
                "AND o.status IN ('DELIVERED','COMPLETED')",
                Long.class)
            .setParameter("start", startDate)
            .setParameter("end", endDate)
            .getSingleResult();
        metrics.setCompletedOrders(completedOrders);

        // Cancelled orders
        Long cancelledOrders = em.createQuery(
                "SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end " +
                "AND o.status = 'CANCELLED'",
                Long.class)
            .setParameter("start", startDate)
            .setParameter("end", endDate)
            .getSingleResult();
        metrics.setCancelledOrders(cancelledOrders);

        // Average order value (in default currency)
        Double aov = em.createQuery(
                "SELECT AVG(o.totalAmount.amount) FROM Order o WHERE o.createdAt BETWEEN :start AND :end",
                Double.class)
            .setParameter("start", startDate)
            .setParameter("end", endDate)
            .getSingleResult();
        metrics.setAverageOrderValue(aov != null ? aov : 0.0);

        // Unique customers
        Long totalCustomers = em.createQuery(
                "SELECT COUNT(DISTINCT o.userId) FROM Order o WHERE o.createdAt BETWEEN :start AND :end",
                Long.class)
            .setParameter("start", startDate)
            .setParameter("end", endDate)
            .getSingleResult();
        metrics.setTotalCustomers(totalCustomers);

        // Returning vs new customers (simple heuristic)
        Long newCustomers = em.createQuery(
                "SELECT COUNT(DISTINCT o.userId) FROM Order o WHERE o.createdAt BETWEEN :start AND :end " +
                "AND o.userId NOT IN (SELECT o2.userId FROM Order o2 WHERE o2.createdAt < :start)",
                Long.class)
            .setParameter("start", startDate)
            .setParameter("end", endDate)
            .getSingleResult();
        metrics.setNewCustomers(newCustomers);
        metrics.setReturningCustomers(totalCustomers - newCustomers);

        double completionRate = totalOrders > 0 ? completedOrders * 100.0 / totalOrders : 0.0;
        double cancellationRate = totalOrders > 0 ? cancelledOrders * 100.0 / totalOrders : 0.0;
        metrics.setCompletionRate(completionRate);
        metrics.setCancellationRate(cancellationRate);
        // retentionRate placeholder
        metrics.setCustomerRetentionRate(totalCustomers > 0 ? (metrics.getReturningCustomers() * 100.0 / totalCustomers) : 0.0);
        return metrics;
    }

    @Override
    public RevenueAnalytics getRevenueAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        RevenueAnalytics ra = new RevenueAnalytics();

        BigDecimal totalRevenue = getBigDecimal(
            em.createQuery("SELECT COALESCE(SUM(o.totalAmount.amount),0) FROM Order o WHERE o.createdAt BETWEEN :start AND :end", BigDecimal.class)
                .setParameter("start", startDate)
                .setParameter("end", endDate)
        );
        ra.setTotalRevenue(totalRevenue);

        BigDecimal totalDiscounts = getBigDecimal(
            em.createQuery("SELECT COALESCE(SUM(o.discountAmount.amount),0) FROM Order o WHERE o.createdAt BETWEEN :start AND :end", BigDecimal.class)
                .setParameter("start", startDate)
                .setParameter("end", endDate)
        );
        ra.setTotalDiscounts(totalDiscounts);

        BigDecimal totalTax = getBigDecimal(
            em.createQuery("SELECT COALESCE(SUM(o.taxAmount.amount),0) FROM Order o WHERE o.createdAt BETWEEN :start AND :end", BigDecimal.class)
                .setParameter("start", startDate)
                .setParameter("end", endDate)
        );
        ra.setTotalTax(totalTax);

        BigDecimal totalShipping = getBigDecimal(
            em.createQuery("SELECT COALESCE(SUM(o.shippingAmount.amount),0) FROM Order o WHERE o.createdAt BETWEEN :start AND :end", BigDecimal.class)
                .setParameter("start", startDate)
                .setParameter("end", endDate)
        );
        ra.setTotalShipping(totalShipping);

        // Net revenue approximated as total - refunds (refunded tracked in payments)
        BigDecimal totalRefunds = getBigDecimal(
            em.createQuery("SELECT COALESCE(SUM(p.refundedAmount.amount),0) FROM OrderPayment p WHERE p.order.createdAt BETWEEN :start AND :end", BigDecimal.class)
                .setParameter("start", startDate)
                .setParameter("end", endDate)
        );
        ra.setTotalRefunds(totalRefunds);
        ra.setNetRevenue(totalRevenue.subtract(totalRefunds));
        return ra;
    }

    @Override
    public CustomerAnalytics getCustomerAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        CustomerAnalytics ca = new CustomerAnalytics();
        Long totalCustomers = em.createQuery(
            "SELECT COUNT(DISTINCT o.userId) FROM Order o WHERE o.createdAt BETWEEN :start AND :end", Long.class)
            .setParameter("start", startDate).setParameter("end", endDate).getSingleResult();
        ca.setTotalCustomers(totalCustomers);
        Long newCustomers = em.createQuery(
            "SELECT COUNT(DISTINCT o.userId) FROM Order o WHERE o.createdAt BETWEEN :start AND :end " +
            "AND o.userId NOT IN (SELECT o2.userId FROM Order o2 WHERE o2.createdAt < :start)", Long.class)
            .setParameter("start", startDate).setParameter("end", endDate).getSingleResult();
        ca.setNewCustomers(newCustomers);
        ca.setReturningCustomers(totalCustomers - newCustomers);
        // Simple averages
        Double avgOrdersPerCustomer = totalCustomers > 0 ?
            em.createQuery("SELECT COUNT(o) * 1.0 / COUNT(DISTINCT o.userId) FROM Order o WHERE o.createdAt BETWEEN :start AND :end", Double.class)
                .setParameter("start", startDate).setParameter("end", endDate).getSingleResult() : 0.0;
        ca.setAverageOrdersPerCustomer(avgOrdersPerCustomer);
        ca.setRetentionRate(totalCustomers > 0 ? (ca.getReturningCustomers() * 100.0 / totalCustomers) : 0.0);
        return ca;
    }

    @Override
    public ProductAnalytics getProductAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        ProductAnalytics pa = new ProductAnalytics();
        // Totals from OrderItem
        Long totalProducts = em.createQuery(
            "SELECT COUNT(DISTINCT oi.productId) FROM OrderItem oi WHERE oi.createdAt BETWEEN :start AND :end", Long.class)
            .setParameter("start", startDate).setParameter("end", endDate).getSingleResult();
        pa.setTotalProducts(totalProducts);
        Long totalItemsSold = em.createQuery(
            "SELECT COALESCE(SUM(oi.quantity),0) FROM OrderItem oi WHERE oi.createdAt BETWEEN :start AND :end", Long.class)
            .setParameter("start", startDate).setParameter("end", endDate).getSingleResult();
        pa.setTotalItemsSold(totalItemsSold);
        Double avgItemsPerOrder = em.createQuery(
            "SELECT (COALESCE(SUM(oi.quantity),0) * 1.0) / NULLIF(COUNT(DISTINCT oi.order.id),0) FROM OrderItem oi WHERE oi.createdAt BETWEEN :start AND :end",
            Double.class).setParameter("start", startDate).setParameter("end", endDate).getSingleResult();
        pa.setAverageItemsPerOrder(avgItemsPerOrder != null ? avgItemsPerOrder : 0.0);
        return pa;
    }

    @Override
    public GeographicAnalytics getGeographicAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        GeographicAnalytics g = new GeographicAnalytics();
        g.setOrdersByCountry(toCountMap(
            em.createQuery("SELECT o.shippingAddress.country, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end GROUP BY o.shippingAddress.country", Object[].class)
                .setParameter("start", startDate).setParameter("end", endDate).getResultList()
        ));
        g.setOrdersByState(toCountMap(
            em.createQuery("SELECT o.shippingAddress.state, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end GROUP BY o.shippingAddress.state", Object[].class)
                .setParameter("start", startDate).setParameter("end", endDate).getResultList()
        ));
        g.setOrdersByCity(toCountMap(
            em.createQuery("SELECT o.shippingAddress.city, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end GROUP BY o.shippingAddress.city", Object[].class)
                .setParameter("start", startDate).setParameter("end", endDate).getResultList()
        ));
        return g;
    }

    @Override
    public PerformanceMetrics getPerformanceMetrics(LocalDateTime startDate, LocalDateTime endDate) {
        PerformanceMetrics p = new PerformanceMetrics();
        // Placeholder averages (calculate in service layer if needed)
        p.setAverageProcessingTime(null);
        p.setAverageDeliveryTime(null);
        p.setOnTimeDeliveryRate(null);
        p.setPaymentSuccessRate(null);
        p.setCustomerSatisfactionScore(null);
        return p;
    }

    @Override
    public List<DailyTrend> getDailyOrderTrends(LocalDateTime startDate, LocalDateTime endDate) {
        // Use database-specific DATE function; fallback to empty for portability
        return new ArrayList<>();
    }

    @Override
    public List<HourlyPattern> getHourlyOrderPatterns(LocalDateTime startDate, LocalDateTime endDate) {
        return new ArrayList<>();
    }

    @Override
    public List<TopCustomer> getTopCustomersByOrderCount(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        TypedQuery<Object[]> q = em.createQuery(
            "SELECT o.userId, COUNT(o) as cnt, SUM(o.totalAmount.amount) FROM Order o WHERE o.createdAt BETWEEN :start AND :end GROUP BY o.userId ORDER BY cnt DESC",
            Object[].class);
        q.setParameter("start", startDate); q.setParameter("end", endDate); q.setMaxResults(limit);
        List<Object[]> rows = q.getResultList();
        List<TopCustomer> res = new ArrayList<>();
        for (Object[] r : rows) {
            res.add(new TopCustomer((Long) r[0], null, (Long) r[1], toBigDecimal(r[2])));
        }
        return res;
    }

    @Override
    public List<TopCustomer> getTopCustomersByRevenue(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        TypedQuery<Object[]> q = em.createQuery(
            "SELECT o.userId, SUM(o.totalAmount.amount) as rev, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end GROUP BY o.userId ORDER BY rev DESC",
            Object[].class);
        q.setParameter("start", startDate); q.setParameter("end", endDate); q.setMaxResults(limit);
        List<Object[]> rows = q.getResultList();
        List<TopCustomer> res = new ArrayList<>();
        for (Object[] r : rows) {
            res.add(new TopCustomer((Long) r[0], null, (Long) r[2], toBigDecimal(r[1])));
        }
        return res;
    }

    @Override
    public List<TopProduct> getTopProductsByQuantity(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        TypedQuery<Object[]> q = em.createQuery(
            "SELECT oi.productId, oi.productName, oi.sku, SUM(oi.quantity) as qty, SUM(oi.totalPrice.amount) FROM OrderItem oi WHERE oi.createdAt BETWEEN :start AND :end GROUP BY oi.productId, oi.productName, oi.sku ORDER BY qty DESC",
            Object[].class);
        q.setParameter("start", startDate); q.setParameter("end", endDate); q.setMaxResults(limit);
        List<Object[]> rows = q.getResultList();
        List<TopProduct> res = new ArrayList<>();
        for (Object[] r : rows) {
            TopProduct p = new TopProduct((Long) r[0], (String) r[1], (String) r[2], (Long) r[3], toBigDecimal(r[4]));
            res.add(p);
        }
        return res;
    }

    @Override
    public List<TopProduct> getTopProductsByRevenue(LocalDateTime startDate, LocalDateTime endDate, int limit) {
        TypedQuery<Object[]> q = em.createQuery(
            "SELECT oi.productId, oi.productName, oi.sku, SUM(oi.totalPrice.amount) as rev, SUM(oi.quantity) FROM OrderItem oi WHERE oi.createdAt BETWEEN :start AND :end GROUP BY oi.productId, oi.productName, oi.sku ORDER BY rev DESC",
            Object[].class);
        q.setParameter("start", startDate); q.setParameter("end", endDate); q.setMaxResults(limit);
        List<Object[]> rows = q.getResultList();
        List<TopProduct> res = new ArrayList<>();
        for (Object[] r : rows) {
            TopProduct p = new TopProduct((Long) r[0], (String) r[1], (String) r[2], (Long) r[4], toBigDecimal(r[3]));
            res.add(p);
        }
        return res;
    }

    @Override
    public ConversionFunnel getConversionFunnel(LocalDateTime startDate, LocalDateTime endDate) {
        return new ConversionFunnel();
    }

    @Override
    public List<CohortData> getCohortAnalysis(LocalDateTime startDate, LocalDateTime endDate, String cohortType) {
        return new ArrayList<>();
    }

    @Override
    public List<SeasonalTrend> getSeasonalTrends(int year) {
        return new ArrayList<>();
    }

    @Override
    public Map<OrderStatus, Long> getOrderStatusDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> rows = em.createQuery(
            "SELECT o.status, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end GROUP BY o.status",
            Object[].class)
            .setParameter("start", startDate)
            .setParameter("end", endDate)
            .getResultList();
        Map<OrderStatus, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((OrderStatus) r[0], (Long) r[1]);
        }
        return map;
    }

    @Override
    public Map<OrderType, Long> getOrderTypeDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> rows = em.createQuery(
            "SELECT o.orderType, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end GROUP BY o.orderType",
            Object[].class)
            .setParameter("start", startDate)
            .setParameter("end", endDate)
            .getResultList();
        Map<OrderType, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((OrderType) r[0], (Long) r[1]);
        }
        return map;
    }

    @Override
    public Map<String, Long> getOrderSourceDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> rows = em.createQuery(
            "SELECT o.orderSource, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end GROUP BY o.orderSource",
            Object[].class)
            .setParameter("start", startDate)
            .setParameter("end", endDate)
            .getResultList();
        Map<String, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((String) r[0], (Long) r[1]);
        }
        return map;
    }

    @Override
    public Map<String, PaymentMethodStats> getPaymentMethodDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        // Placeholder: compute counts by method
        List<Object[]> rows = em.createQuery(
            "SELECT p.paymentMethod, COUNT(p) FROM OrderPayment p WHERE p.order.createdAt BETWEEN :start AND :end GROUP BY p.paymentMethod",
            Object[].class).setParameter("start", startDate).setParameter("end", endDate).getResultList();
        Map<String, PaymentMethodStats> map = new HashMap<>();
        for (Object[] r : rows) {
            String method = (String) r[0];
            Long count = (Long) r[1];
            PaymentMethodStats stats = new PaymentMethodStats();
            map.put(method, stats);
        }
        return map;
    }

    @Override
    public Map<String, ShippingMethodStats> getShippingMethodDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> rows = em.createQuery(
            "SELECT os.shippingMethod, COUNT(os) FROM OrderShipping os WHERE os.order.createdAt BETWEEN :start AND :end GROUP BY os.shippingMethod",
            Object[].class).setParameter("start", startDate).setParameter("end", endDate).getResultList();
        Map<String, ShippingMethodStats> map = new HashMap<>();
        for (Object[] r : rows) {
            String method = (String) r[0];
            Long count = (Long) r[1];
            ShippingMethodStats stats = new ShippingMethodStats();
            map.put(method, stats);
        }
        return map;
    }

    @Override
    public Map<String, Double> getAverageProcessingTimes(LocalDateTime startDate, LocalDateTime endDate) {
        return new HashMap<>();
    }

    @Override
    public List<CLVSegment> getCustomerLifetimeValueDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        return new ArrayList<>();
    }

    @Override
    public List<OrderValueSegment> getOrderValueDistribution(LocalDateTime startDate, LocalDateTime endDate) {
        return new ArrayList<>();
    }

    @Override
    public ReturnAnalytics getReturnAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        return new ReturnAnalytics();
    }

    @Override
    public FraudMetrics getFraudMetrics(LocalDateTime startDate, LocalDateTime endDate) {
        return new FraudMetrics();
    }

    private BigDecimal getBigDecimal(TypedQuery<BigDecimal> q) {
        BigDecimal res = q.getSingleResult();
        return res != null ? res : BigDecimal.ZERO;
    }

    private Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            if (r[0] != null) {
                map.put((String) r[0], (Long) r[1]);
            }
        }
        return map;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
