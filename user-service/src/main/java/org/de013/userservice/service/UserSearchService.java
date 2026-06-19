package org.de013.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.userservice.entity.User;
import org.de013.userservice.repository.UserRepository;
import org.de013.userservice.repository.specification.UserSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserSearchService {

    private final UserRepository userRepository;

    /**
     * Search users with keyword
     */
    public Page<User> searchUsers(String keyword, Pageable pageable) {
        log.debug("Searching users with keyword: {}", keyword);
        return userRepository.searchUsers(keyword, pageable);
    }

    /**
     * Advanced search with multiple filters using Specifications
     */
    public Page<User> advancedSearch(String keyword, String roleName,
                                     LocalDateTime startDate, LocalDateTime endDate,
                                     Pageable pageable) {
        log.debug("Advanced search - keyword: {}, role: {}, dateRange: {} to {}",
                keyword, roleName, startDate, endDate);

        Specification<User> spec = UserSpecification.withFilters(keyword, roleName, startDate, endDate);
        return userRepository.findAll(spec, pageable);
    }

    /**
     * Find users by role
     */
    public List<User> findUsersByRole(String roleName) {
        log.debug("Finding users by role: {}", roleName);
        return userRepository.findByRoleName(roleName);
    }

    /**
     * Find users created in date range
     */
    public Page<User> findUsersCreatedBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        log.debug("Finding users created between {} and {}", startDate, endDate);
        return userRepository.findUsersCreatedBetween(startDate, endDate, pageable);
    }

    /**
     * Get user statistics
     */
    public UserStatistics getUserStatistics() {
        log.debug("Getting user statistics");

        long totalUsers = userRepository.count();
        long adminUsers = userRepository.countByRoleName("ADMIN");
        long customerUsers = userRepository.countByRoleName("CUSTOMER");

        return UserStatistics.builder()
                .totalUsers(totalUsers)
                .adminUsers(adminUsers)
                .customerUsers(customerUsers)
                .build();
    }

    /**
     * User statistics DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class UserStatistics {
        private long totalUsers;
        private long adminUsers;
        private long customerUsers;
    }
}
