package org.example.website.repository;

import org.example.website.entity.ViewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 瀏覽歷史數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，用於記錄和管理用戶的商品瀏覽歷史，並提供定時清理與批量刪除功能。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface ViewHistoryRepository extends JpaRepository<ViewHistory, Long> {

    /**
     * 查詢用戶的瀏覽歷史記錄，並按瀏覽時間倒序排列。
     * <p>
     * 通常用於用戶個人中心的「瀏覽歷史」頁面展示。
     * </p>
     *
     * @param username 用戶名
     * @return 按瀏覽時間倒序排列的歷史記錄列表
     */
    List<ViewHistory> findByUser_UsernameOrderByViewedAtDesc(String username);

    /**
     * 查找用戶是否已瀏覽過某個特定商品。
     * <p>
     * 若已存在，則更新其瀏覽時間；若不存在，則創建新記錄。
     * </p>
     *
     * @param username  用戶名
     * @param productId 商品 ID
     * @return 匹配的瀏覽歷史記錄，若無則返回 null
     */
    ViewHistory findByUser_UsernameAndProduct_ProductId(String username, Integer productId);

    /**
     * 刪除指定截止日期之前的瀏覽記錄。
     * <p>
     * 通常由 Spring {@link org.springframework.scheduling.annotation.Scheduled} 定時任務調用，
     * 用於定期清理半年前的歷史數據，釋放數據庫空間。
     * </p>
     *
     * @param cutoffDate 截止日期 ({@link LocalDateTime})
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ViewHistory vh WHERE vh.viewedAt < :cutoffDate")
    void deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * 刪除指定用戶在特定時間範圍內的瀏覽記錄。
     * <p>
     * 通常用於用戶個人中心的「清除最近 1 天/1 週/1 個月」功能。
     * 【核心優化】：直接使用 userId 進行刪除，避免 JOIN User 表帶來的性能損耗。
     * </p>
     *
     * @param userId     用戶 ID
     * @param cutoffDate 截止日期
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ViewHistory vh WHERE vh.user.id = :userId AND vh.viewedAt >= :cutoffDate")
    void deleteRecentForUserId(@Param("userId") Long userId, @Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * 刪除指定用戶的所有瀏覽記錄。
     * <p>
     * 通常用於用戶個人中心的「清空所有瀏覽歷史」功能。
     * 【核心優化】：直接使用 userId 進行刪除，避免 JOIN User 表帶來的性能損耗。
     * </p>
     *
     * @param userId 用戶 ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ViewHistory vh WHERE vh.user.id = :userId")
    void deleteAllForUserId(@Param("userId") Long userId);

    /**
     * 根據 ID 列表和用戶名批量刪除瀏覽記錄。
     * <p>
     * 【安全防護】：同時校驗 historyId 和 username，防止惡意用戶構造請求越權刪除別人的瀏覽記錄。
     * </p>
     *
     * @param historyIds 瀏覽歷史記錄 ID 列表
     * @param username   用戶名
     */
    @Modifying
    void deleteByHistoryIdInAndUser_Username(List<Long> historyIds, String username);
}