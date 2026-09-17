package org.example.website.repository;

import org.example.website.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 訂單數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link Order} 實體的權限校驗、分頁及狀態篩選查詢方法。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 根據訂單編號和用戶名查詢訂單。
     * <p>
     * 【安全防護】：同時校驗訂單號和所屬用戶，確保用戶只能查看或操作自己的訂單，防止越權訪問 (IDOR)。
     * </p>
     *
     * @param orderNo  訂單編號
     * @param username 用戶名
     * @return 匹配的訂單對象 (若無權限或不存在則返回 {@link Optional#empty()})
     */
    Optional<Order> findByOrderNoAndUser_Username(String orderNo, String username);

    /**
     * 查詢指定用戶的所有訂單，並按創建時間降序排列。
     * <p>
     * 通常用於用戶個人中心的「我的訂單」列表展示。
     * </p>
     *
     * @param username 用戶名
     * @return 該用戶的訂單列表 (最新訂單在前)
     */
    List<Order> findByUser_UsernameOrderByCreatedAtDesc(String username);

    /**
     * 查詢指定用戶的特定狀態且未被隱藏 (可見) 的訂單。
     * <p>
     * 通常用於用戶端的「取消與退貨訂單」頁面，過濾掉用戶已手動刪除（軟刪除）的記錄。
     * </p>
     *
     * @param username 用戶名
     * @param status   訂單狀態枚舉
     * @return 符合條件的訂單列表
     */
    List<Order> findByUser_UsernameAndStatusAndIsVisibleTrue(String username, Order.OrderStatus status);

    /**
     * 分頁查詢所有訂單，並使用 JOIN FETCH 預先加載關聯的 User 實體。
     * <p>
     * 【性能優化】：解決後台管理員查看訂單列表時的 N+1 查詢問題，一次 SQL 獲取訂單及買家信息。
     * </p>
     *
     * @param pageable 分頁與排序參數
     * @return 包含訂單及用戶信息的分頁結果
     */
    @Query(value = "SELECT o FROM Order o JOIN FETCH o.user",
            countQuery = "SELECT count(o) FROM Order o")
    Page<Order> findAllWithUsers(Pageable pageable);

    /**
     * 僅根據訂單編號查詢訂單 (通常用於後台管理員或內部系統邏輯)。
     *
     * @param orderNo 訂單編號
     * @return 匹配的訂單對象
     */
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 【新增】分頁查詢當前用戶的「待付款」線上訂單。
     * <p>
     * 篩選條件：支付方式為線上模擬支付 (PAYPAL_SIM) 且 支付狀態為未付款 (UNPAID)。
     * </p>
     *
     * @param username 用戶名
     * @param pageable 分頁參數
     * @return 待付款訂單的分頁結果
     */
    @Query("SELECT o FROM Order o WHERE o.user.username = :username " +
            "AND o.paymentMethod = 'PAYPAL_SIM' " +
            "AND o.paymentStatus = 'UNPAID' " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findUnpaidOrders(@Param("username") String username, Pageable pageable);

    /**
     * 【新增】分頁查詢當前用戶的「待線下付款」訂單。
     * <p>
     * 篩選條件：支付方式為線下店鋪 (OFFLINE_STORE) 且 支付狀態為待線下付款 (PENDING_OFFLINE)。
     * </p>
     *
     * @param username 用戶名
     * @param pageable 分頁參數
     * @return 待線下付款訂單的分頁結果
     */
    @Query("SELECT o FROM Order o WHERE o.user.username = :username " +
            "AND o.paymentMethod = 'OFFLINE_STORE' " +
            "AND o.paymentStatus = 'PENDING_OFFLINE' " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findPendingOfflineOrders(@Param("username") String username, Pageable pageable);

    /**
     * 【新增】分頁查詢當前用戶的「已支付」訂單。
     * <p>
     * 篩選條件：支付狀態為已付款 (包含 PAID_SIMULATED, PAID_REAL, PAID_OFFLINE)。
     * </p>
     *
     * @param username 用戶名
     * @param pageable 分頁參數
     * @return 已支付訂單的分頁結果
     */
    @Query("SELECT o FROM Order o WHERE o.user.username = :username " +
            "AND o.paymentStatus IN ('PAID_SIMULATED', 'PAID_REAL', 'PAID_OFFLINE') " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findPaidOrders(@Param("username") String username, Pageable pageable);
}