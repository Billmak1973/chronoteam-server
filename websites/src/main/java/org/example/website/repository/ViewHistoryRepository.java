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

    // 刪除半年前的記錄 (供定時任務使用，這裡保持 < 不變！)
    @Modifying
    @Transactional
    @Query("DELETE FROM ViewHistory vh WHERE vh.viewedAt < :cutoffDate")
    void deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    // 刪除「最近」指定時間範圍內的記錄 (將 < 改為 >=)
    // 注意：因為 Customer 實體的 @Id 就是 username，所以 vh.customer.username 是正確的寫法，千萬不要改成 id！
    @Modifying
    @Transactional
    @Query("DELETE FROM ViewHistory vh WHERE vh.user.username = :username AND vh.viewedAt >= :cutoffDate")
    void deleteRecentForUser(@Param("username") String username, @Param("cutoffDate") LocalDateTime cutoffDate);

    // 刪除用戶的所有瀏覽記錄 (保持不變)
    @Modifying
    @Transactional
    @Query("DELETE FROM ViewHistory vh WHERE vh.user.username = :username")
    void deleteAllForUser(@Param("username") String username);
}