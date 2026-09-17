package org.example.website.repository;

import org.example.website.entity.RateLimitLog;
import org.example.website.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 限流與封禁日誌數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，用於記錄和查詢系統自動觸發的頻繁操作限流及臨時封禁日誌。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface RateLimitLogRepository extends JpaRepository<RateLimitLog, Long> {

    /**
     * 查找用戶當前有效的封禁記錄。
     * <p>
     * 【雙重保險機制】：
     * 配合 Redis 使用。當 Redis 數據丟失時，可透過此方法從數據庫中確認用戶是否仍處於封禁期內。
     * </p>
     *
     * @param user   目標用戶實體
     * @param status 狀態枚舉 (必須為 {@link RateLimitLog.LimitStatus#BANNED})
     * @param now    當前時間 (用於與 bannedUntil 比較)
     * @return 包含有效封禁記錄的 {@link Optional}，若無或未過期則返回 {@link Optional#empty()}
     */
    Optional<RateLimitLog> findTopByUserAndStatusAndBannedUntilAfter(
            User user,
            RateLimitLog.LimitStatus status,
            LocalDateTime now
    );

    /**
     * 查找用戶最新的一條限流/封禁記錄。
     * <p>
     * 主要用於在觸發新的限流閾值時，判斷是否需要延長現有封禁的結束時間，而非創建新記錄。
     * </p>
     *
     * @param user 目標用戶實體
     * @return 包含最新記錄的 {@link Optional}
     */
    Optional<RateLimitLog> findTopByUserOrderByActionTimeDesc(User user);

    /**
     * 核心邏輯：將已經過期的 BANNED 狀態批量更新為 EXPIRED。
     * <p>
     * 【修復說明】：使用 {@link Param} 綁定枚舉值，避免 Hibernate 6 將其誤解析為路徑表達式而導致 SQL 語法錯誤。
     * 此方法通常由定時任務 (Scheduled Task) 調用，以保持數據庫狀態的清潔。
     * </p>
     *
     * @param expiredStatus 目標狀態 (應傳入 {@link RateLimitLog.LimitStatus#EXPIRED})
     * @param bannedStatus  當前狀態 (應傳入 {@link RateLimitLog.LimitStatus#BANNED})
     * @return 受影響的行數
     */
    @Modifying
    @Transactional
    @Query("UPDATE RateLimitLog r SET r.status = :expiredStatus WHERE r.status = :bannedStatus AND r.bannedUntil < CURRENT_TIMESTAMP")
    int updateExpiredBans(@Param("expiredStatus") RateLimitLog.LimitStatus expiredStatus,
                          @Param("bannedStatus") RateLimitLog.LimitStatus bannedStatus);
}