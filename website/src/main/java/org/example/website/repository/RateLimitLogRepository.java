package org.example.website.repository;

import org.example.website.entity.RateLimitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RateLimitLogRepository extends JpaRepository<RateLimitLog, Long> {

    // 查找用户在过去1分钟内的记录（不区分操作类型！）
    @Query("SELECT r FROM RateLimitLog r WHERE r.username = :username " +
            "AND r.actionTime > :oneMinuteAgo " +
            "ORDER BY r.actionTime DESC")
    Optional<RateLimitLog> findRecentAction(
            @Param("username") String username,
            @Param("oneMinuteAgo") LocalDateTime oneMinuteAgo
    );

    // 检查用户是否被封禁（不区分操作类型！）
    @Query("SELECT r FROM RateLimitLog r WHERE r.username = :username " +
            "AND r.bannedUntil IS NOT NULL " +
            "AND r.bannedUntil > :now")
    Optional<RateLimitLog> findBannedAction(
            @Param("username") String username,
            @Param("now") LocalDateTime now
    );

    // 清理旧记录
    @Modifying
    @Query("DELETE FROM RateLimitLog r WHERE r.actionTime < :cutoffTime")
    void deleteOldRecords(@Param("cutoffTime") LocalDateTime cutoffTime);
}