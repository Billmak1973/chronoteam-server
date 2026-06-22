package org.example.website.repository;

import org.example.website.entity.StockNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockNotificationRepository extends JpaRepository<StockNotification, Long> {

    /**
     * 1. 客戶端：檢查某個用戶是否已經訂閱了某個商品 (防止重複點擊)
     */
    Optional<StockNotification> findByProduct_IdAndUsername(Integer productId, String username);

    /**
     * 2. 管理員/前端：獲取某個商品的所有「等待中」的訂閱記錄
     */
    List<StockNotification> findByProduct_IdAndNotifiedFalseOrderByCreatedAtAsc(Integer productId);

    /**
     * 3. 前端顯示：統計某個商品目前有多少人在等待到貨
     */
    long countByProduct_IdAndNotifiedFalse(Integer productId);

    /**
     * 4. 管理員批量通知後：將該商品的所有等待記錄標記為已通知
     * (配合 @Modifying 使用，或者在 Service 層遍歷 save)
     */
    List<StockNotification> findByProduct_IdAndNotifiedFalse(Integer productId);

    /**
     * 5. 客戶端：查看某個用戶訂閱了哪些缺貨商品 (可用於個人中心顯示)
     */
    List<StockNotification> findByUsernameAndNotifiedFalseOrderByCreatedAtDesc(String username);

    Page<StockNotification> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

}