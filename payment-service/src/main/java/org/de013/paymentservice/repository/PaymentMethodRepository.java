package org.de013.paymentservice.repository;

import org.de013.paymentservice.entity.PaymentMethod;
import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PaymentMethod entity
 */
@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    // ========== BASIC QUERIES ==========

    /**
     * Find payment methods by user ID
     */
    List<PaymentMethod> findByUserId(Long userId);

    /**
     * Find payment methods by user ID with pagination
     */
    Page<PaymentMethod> findByUserId(Long userId, Pageable pageable);

    /**
     * Find active payment methods by user ID
     */
    List<PaymentMethod> findByUserIdAndIsActive(Long userId, Boolean isActive);

    /**
     * Find active payment methods by user ID with pagination
     */
    Page<PaymentMethod> findByUserIdAndIsActive(Long userId, Boolean isActive, Pageable pageable);

    /**
     * Find default payment method by user ID
     */
    Optional<PaymentMethod> findByUserIdAndIsDefaultAndIsActive(Long userId, Boolean isDefault, Boolean isActive);

    /**
     * Find payment methods by type
     */
    List<PaymentMethod> findByType(PaymentMethodType type);

    /**
     * Find payment methods by user ID and type
     */
    List<PaymentMethod> findByUserIdAndType(Long userId, PaymentMethodType type);

    /**
     * Find active payment methods by user ID and type
     */
    List<PaymentMethod> findByUserIdAndTypeAndIsActive(Long userId, PaymentMethodType type, Boolean isActive);

    // ========== STRIPE-SPECIFIC QUERIES ==========

    /**
     * Find payment method by Stripe payment method ID
     */
    Optional<PaymentMethod> findByStripePaymentMethodId(String stripePaymentMethodId);

    /**
     * Find payment methods by Stripe customer ID
     */
    List<PaymentMethod> findByStripeCustomerId(String stripeCustomerId);

    /**
     * Find active payment methods by Stripe customer ID
     */
    List<PaymentMethod> findByStripeCustomerIdAndIsActive(String stripeCustomerId, Boolean isActive);

    /**
     * Find payment method by user ID and Stripe payment method ID
     */
    Optional<PaymentMethod> findByUserIdAndStripePaymentMethodId(Long userId, String stripePaymentMethodId);

    // ========== DEFAULT PAYMENT METHOD QUERIES ==========

    /**
     * Find default payment method by user ID (simplified)
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.isDefault = true AND pm.isActive = true")
    Optional<PaymentMethod> findDefaultByUserId(@Param("userId") Long userId);

    /**
     * Find all default payment methods by user ID (for validation)
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.isDefault = true")
    List<PaymentMethod> findAllDefaultByUserId(@Param("userId") Long userId);

    /**
     * Clear default flag for all user's payment methods
     */
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.isDefault = false WHERE pm.userId = :userId")
    void clearDefaultFlagForUser(@Param("userId") Long userId);

    /**
     * Set payment method as default
     */
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.isDefault = :isDefault WHERE pm.id = :id")
    void updateDefaultFlag(@Param("id") Long id, @Param("isDefault") Boolean isDefault);

    // ========== CARD-SPECIFIC QUERIES ==========

    /**
     * Find card payment methods by user ID
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.type = 'CARD' AND pm.isActive = true ORDER BY pm.createdAt DESC")
    List<PaymentMethod> findCardPaymentMethodsByUserId(@Param("userId") Long userId);

    /**
     * Find expired card payment methods
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.type = 'CARD' AND pm.isActive = true AND " +
           "((pm.expiryYear < :currentYear) OR (pm.expiryYear = :currentYear AND pm.expiryMonth < :currentMonth))")
    List<PaymentMethod> findExpiredCardPaymentMethods(@Param("currentYear") Integer currentYear, @Param("currentMonth") Integer currentMonth);

    /**
     * Find card payment methods expiring soon
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.type = 'CARD' AND pm.isActive = true AND " +
           "((pm.expiryYear = :targetYear AND pm.expiryMonth = :targetMonth) OR " +
           "(pm.expiryYear = :targetYear AND pm.expiryMonth < :targetMonth AND :targetMonth <= 12))")
    List<PaymentMethod> findCardPaymentMethodsExpiringSoon(@Param("targetYear") Integer targetYear, @Param("targetMonth") Integer targetMonth);

    // ========== SEARCH AND FILTERING ==========

    /**
     * Search payment methods by criteria
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE " +
           "pm.userId = :userId AND " +
           "(:type IS NULL OR pm.type = :type) AND " +
           "(:isActive IS NULL OR pm.isActive = :isActive) AND " +
           "(:isDefault IS NULL OR pm.isDefault = :isDefault) AND " +
           "(:provider IS NULL OR pm.provider = :provider) AND " +
           "(:cardBrand IS NULL OR pm.cardBrand LIKE %:cardBrand%) " +
           "ORDER BY pm.isDefault DESC, pm.lastUsedAt DESC NULLS LAST, pm.createdAt DESC")
    Page<PaymentMethod> searchPaymentMethods(
            @Param("userId") Long userId,
            @Param("type") PaymentMethodType type,
            @Param("isActive") Boolean isActive,
            @Param("isDefault") Boolean isDefault,
            @Param("provider") String provider,
            @Param("cardBrand") String cardBrand,
            Pageable pageable
    );

    /**
     * Find recently used payment methods
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.isActive = true AND pm.lastUsedAt IS NOT NULL " +
           "ORDER BY pm.lastUsedAt DESC")
    List<PaymentMethod> findRecentlyUsedByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Find unused payment methods
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.isActive = true AND pm.lastUsedAt IS NULL " +
           "ORDER BY pm.createdAt DESC")
    List<PaymentMethod> findUnusedByUserId(@Param("userId") Long userId);

    // ========== STATISTICS QUERIES ==========

    /**
     * Count payment methods by user ID
     */
    @Query("SELECT COUNT(pm) FROM PaymentMethod pm WHERE pm.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);

    /**
     * Count active payment methods by user ID
     */
    @Query("SELECT COUNT(pm) FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.isActive = true")
    Long countActiveByUserId(@Param("userId") Long userId);

    /**
     * Count payment methods by user ID and type
     */
    @Query("SELECT COUNT(pm) FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.type = :type AND pm.isActive = true")
    Long countByUserIdAndType(@Param("userId") Long userId, @Param("type") PaymentMethodType type);

    /**
     * Get payment method statistics by user ID
     */
    @Query("SELECT " +
           "COUNT(pm) as totalMethods, " +
           "COUNT(CASE WHEN pm.isActive = true THEN 1 END) as activeMethods, " +
           "COUNT(CASE WHEN pm.isDefault = true THEN 1 END) as defaultMethods, " +
           "COUNT(CASE WHEN pm.type = 'CARD' THEN 1 END) as cardMethods, " +
           "COUNT(CASE WHEN pm.type = 'WALLET' THEN 1 END) as walletMethods " +
           "FROM PaymentMethod pm WHERE pm.userId = :userId")
    Object[] getPaymentMethodStatisticsByUserId(@Param("userId") Long userId);

    // ========== UPDATE OPERATIONS ==========

    /**
     * Update last used timestamp
     */
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.lastUsedAt = :lastUsedAt WHERE pm.id = :id")
    void updateLastUsedAt(@Param("id") Long id, @Param("lastUsedAt") LocalDateTime lastUsedAt);

    /**
     * Deactivate payment method
     */
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.isActive = false WHERE pm.id = :id")
    void deactivatePaymentMethod(@Param("id") Long id);

    /**
     * Activate payment method
     */
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.isActive = true WHERE pm.id = :id")
    void activatePaymentMethod(@Param("id") Long id);

    /**
     * Update nickname
     */
    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.nickname = :nickname WHERE pm.id = :id")
    void updateNickname(@Param("id") Long id, @Param("nickname") String nickname);

    // ========== EXISTENCE CHECKS ==========

    /**
     * Check if payment method exists by Stripe payment method ID
     */
    boolean existsByStripePaymentMethodId(String stripePaymentMethodId);

    /**
     * Check if user has default payment method
     */
    @Query("SELECT COUNT(pm) > 0 FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.isDefault = true AND pm.isActive = true")
    boolean hasDefaultPaymentMethod(@Param("userId") Long userId);

    /**
     * Check if user has active payment methods
     */
    @Query("SELECT COUNT(pm) > 0 FROM PaymentMethod pm WHERE pm.userId = :userId AND pm.isActive = true")
    boolean hasActivePaymentMethods(@Param("userId") Long userId);

    // ========== CLEANUP QUERIES ==========

    /**
     * Find inactive payment methods for cleanup
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.isActive = false AND pm.updatedAt < :cutoffDate")
    List<PaymentMethod> findInactivePaymentMethodsForCleanup(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find orphaned payment methods (no associated payments)
     */
    @Query("SELECT pm FROM PaymentMethod pm WHERE pm.isActive = false AND " +
           "NOT EXISTS (SELECT 1 FROM Payment p WHERE p.stripePaymentMethodId = pm.stripePaymentMethodId)")
    List<PaymentMethod> findOrphanedPaymentMethods();
}
