package com.happymouse.cryd.repository;

import com.happymouse.cryd.model.entity.UsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {
    List<UsageLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<UsageLog> findByFeature(String feature);

    @Query("SELECT COUNT(u) FROM UsageLog u WHERE u.createdAt BETWEEN ?1 AND ?2")
    long countByDateRange(LocalDateTime start, LocalDateTime end);

    @Query("SELECT u.feature, COUNT(u) FROM UsageLog u GROUP BY u.feature")
    List<Object[]> countByFeature();

    @Query("SELECT COUNT(u) FROM UsageLog u WHERE u.feature = ?1 AND u.userId = ?2 AND u.createdAt >= ?3")
    long countByFeatureAndUserIdSince(String feature, Long userId, LocalDateTime since);

    @Query("SELECT COUNT(u) FROM UsageLog u WHERE u.result = 'error' AND u.createdAt BETWEEN ?1 AND ?2")
    long countErrorsByDateRange(LocalDateTime start, LocalDateTime end);
}
