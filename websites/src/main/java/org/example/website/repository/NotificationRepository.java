package org.example.website.repository;

import org.example.website.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系統通知數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link Notification} 實體的未讀統計、分頁查詢與批量已讀更新方法。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 統計指定用戶的未讀通知總數量。
     * <p>
     * 通常用於在網站導航欄右上角的消息鈴鐺圖標旁顯示紅色數字角標 (Badge)。
     * </p>
     *
     * @param username 接收者（用戶）的用戶名
     * @return 未讀通知的數量
     */
    long countByRecipient_UsernameAndIsReadFalse(String username);

    /**
     * 根據通知類型和接收者用戶名進行分頁查詢，並按創建時間降序排列。
     * <p>
     * 通常用於用戶中心的「消息中心」頁面，支持按類型（如系統通知、訂單通知、互動通知等）分類展示。
     * </p>
     *
     * @param type     通知類型枚舉 ({@link Notification.NotificationType})
     * @param username 接收者（用戶）的用戶名
     * @param pageable Spring Data 的分頁與排序參數對象 ({@link Pageable})
     * @return 包含通知記錄的分頁結果對象 ({@link Page})
     */
    Page<Notification> findByTypeAndRecipient_UsernameOrderByCreatedAtDesc(
            Notification.NotificationType type, String username, Pageable pageable);

    /**
     * 批量將指定用戶的所有未讀通知標記為已讀。
     * <p>
     * 使用 JPQL 的 UPDATE 語句直接在數據庫層面執行批量更新，避免逐條查詢修改帶來的性能損耗。
     * 注意：此方法帶有 {@link Modifying} 與 {@link Transactional} 註解，調用時需確保在事務環境中執行。
     * </p>
     *
     * @param userId 接收者（用戶）的唯一 ID
     * @return 受影響的行數（即被標記為已讀的通知數量）
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :userId AND n.isRead = false")
    int markAllAsReadByRecipientUserId(@Param("userId") Long userId);
}