package org.example.website.repository;

import org.example.website.entity.StockNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 到貨通知訂閱數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，用於管理用戶對缺貨商品的到貨通知訂閱記錄。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface StockNotificationRepository extends JpaRepository<StockNotification, Long> {

    /**
     * 檢查某個用戶是否已經訂閱了某個特定商品。
     * <p>
     * 通常用於前端點擊「到貨通知」按鈕時，防止用戶重複提交訂閱請求。
     * </p>
     *
     * @param productId 商品 ID
     * @param username  用戶名
     * @return 包含訂閱記錄的 {@link Optional}，若未訂閱則返回 {@link Optional#empty()}
     */
    Optional<StockNotification> findByProduct_ProductIdAndUser_Username(Integer productId, String username);

    /**
     * 獲取某個商品的所有「等待中」(未通知) 的訂閱記錄，並按創建時間升序排列。
     * <p>
     * 通常用於管理員在商品補貨後，查看有哪些用戶需要被通知，以便進行批量發送。
     * </p>
     *
     * @param productId 商品 ID
     * @return 未通知的訂閱記錄列表 (按時間從早到晚)
     */
    List<StockNotification> findByProduct_ProductIdAndNotifiedFalseOrderByCreatedAtAsc(Integer productId);

    /**
     * 獲取某個商品的所有未通知的訂閱記錄 (不分頁、不排序)。
     * <p>
     * 通常配合 {@link org.springframework.data.jpa.repository.Modifying} 或在 Service 層遍歷 save，
     * 用於管理員批量發送通知後，將這些記錄標記為已通知 (notified = true)。
     * </p>
     *
     * @param productId 商品 ID
     * @return 未通知的訂閱記錄列表
     */
    List<StockNotification> findByProduct_ProductIdAndNotifiedFalse(Integer productId);

    /**
     * 根據用戶名分頁查詢其訂閱的到貨通知記錄，並按創建時間降序排列。
     * <p>
     * 通常用於用戶個人中心的「貨物訂閲」頁面，展示用戶正在等待到貨的商品列表。
     * </p>
     *
     * @param username 用戶名
     * @param pageable Spring Data 的分頁與排序參數對象
     * @return 訂閱記錄的分頁結果
     */
    Page<StockNotification> findByUser_UsernameOrderByCreatedAtDesc(String username, Pageable pageable);
}