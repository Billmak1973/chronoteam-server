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

@Repository
public interface ViewHistoryRepository extends JpaRepository<ViewHistory, Long> {

    // 查詢用戶的瀏覽歷史，按時間倒序
    List<ViewHistory> findByUser_UsernameOrderByViewedAtDesc(String username);

    // 查找用戶是否已瀏覽過某個商品
    ViewHistory findByUser_UsernameAndProduct_ProductId(String username, Integer productId);

    // 刪除半年前的記錄 (供定時任務使用)
    @Modifying
    @Transactional
    @Query("DELETE FROM ViewHistory vh WHERE vh.viewedAt < :cutoffDate")
    void deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    //  修改：刪除「最近」指定時間範圍內的記錄 (直接使用 userId，避免 JOIN 問題)
    @Modifying
    @Transactional
    @Query("DELETE FROM ViewHistory vh WHERE vh.user.id = :userId AND vh.viewedAt >= :cutoffDate")
    void deleteRecentForUserId(@Param("userId") Long userId, @Param("cutoffDate") LocalDateTime cutoffDate);

    //  修改：刪除用戶的所有瀏覽記錄 (直接使用 userId，避免 JOIN 問題)
    @Modifying
    @Transactional
    @Query("DELETE FROM ViewHistory vh WHERE vh.user.id = :userId")
    void deleteAllForUserId(@Param("userId") Long userId);

    /**
     * 批量刪除：根據 ID 列表和用戶名刪除（防止越權刪除別人的記錄）
     */
    @Modifying
    void deleteByHistoryIdInAndUser_Username(List<Long> historyIds, String username);
}