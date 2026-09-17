package org.example.website.repository;

import org.example.website.entity.UserInteractionStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 用戶互動統計數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，用於記錄和查詢用戶的互動消息（如回覆、@提及）的最後查看時間，
 * 以實現高效的「未讀消息數量」統計。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface UserInteractionStatsRepository extends JpaRepository<UserInteractionStats, Long> {

    /**
     * 根據用戶名和互動類型查詢互動統計記錄。
     * <p>
     * 通常用於獲取用戶上次查看某類消息（如 REVIEW_REPLY）的時間，以便計算未讀數量。
     * </p>
     *
     * @param username 用戶名
     * @param type     互動類型 (例如: "REVIEW_REPLY", "REVIEW_MENTION")
     * @return 包含統計記錄的 {@link Optional}，若無記錄則返回 {@link Optional#empty()}
     */
    Optional<UserInteractionStats> findByUser_UsernameAndType(String username, String type);

}