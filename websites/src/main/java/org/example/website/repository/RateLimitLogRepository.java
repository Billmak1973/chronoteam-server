package org.example.website.repository;

import org.example.website.entity.RateLimitLog;
import org.example.website.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RateLimitLogRepository extends JpaRepository<RateLimitLog, Long> {

    /**
     * 查找用戶當前有效的封禁記錄
     * 條件：狀態為 BANNED 且 解封時間大於當前時間
     */
    Optional<RateLimitLog> findTopByUserAndStatusAndBannedUntilAfter(
            User user,
            RateLimitLog.LimitStatus status,
            LocalDateTime now
    );

    /**
     * 查找用戶最新的一條限流記錄 (用於更新現有記錄)
     */
    Optional<RateLimitLog> findTopByUserOrderByActionTimeDesc(User user);

    /**
     * 核心邏輯：將已經過期的 BANNED 狀態批量更新為 EXPIRED
     * 【修復】：使用 @Param 綁定枚舉值，避免 Hibernate 6 將其誤解析為路徑表達式
     */
    @Modifying
    @Transactional
    @Query("UPDATE RateLimitLog r SET r.status = :expiredStatus WHERE r.status = :bannedStatus AND r.bannedUntil < CURRENT_TIMESTAMP")
    int updateExpiredBans(@Param("expiredStatus") RateLimitLog.LimitStatus expiredStatus,
                          @Param("bannedStatus") RateLimitLog.LimitStatus bannedStatus);
}