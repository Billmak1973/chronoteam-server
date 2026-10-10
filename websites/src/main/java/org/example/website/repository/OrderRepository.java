package org.example.website.repository;

import org.example.website.entity.AfterSalesRequest;
import org.example.website.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    @Query(value = "SELECT o FROM Order o LEFT JOIN FETCH o.user LEFT JOIN FETCH o.userAddress", countQuery = "SELECT count(o) FROM Order o")
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
            "AND o.paymentStatus IN ('PAID_SIMULATED', 'PAID_REAL', 'PAID_OFFLINE','PARTIALLY_REFUNDED') " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findPaidOrders(@Param("username") String username, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o " +
            "JOIN AfterSalesRequest asr ON o.orderId = asr.originalOrder.orderId " +
            "JOIN asr.items asri " +
            "JOIN asri.product p " +
            "WHERE o.user.username = :username " +
            "AND o.paymentStatus IN ('PAID_SIMULATED', 'PAID_REAL', 'PAID_OFFLINE','PARTIALLY_REFUNDED') " +
            "AND (:requestType IS NULL OR asr.requestType = :requestType) " +
            "AND (:requestStatus IS NULL OR asr.status = :requestStatus) " +
            "AND (:productName IS NULL OR p.description LIKE %:productName%) " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findPaidOrdersWithAfterSalesFilter(
            @Param("username") String username,
            @Param("requestType") AfterSalesRequest.RequestType requestType,
            @Param("requestStatus") AfterSalesRequest.RequestStatus requestStatus,
            @Param("productName") String productName,
            Pageable pageable
    );

    @Query("SELECT o FROM Order o " +
            "LEFT JOIN o.user u " +
            "LEFT JOIN o.courier c " +
            "LEFT JOIN o.offlineStore s " +
            "LEFT JOIN o.userAddress ua " +
            "WHERE (:buyerUsername IS NULL OR u.username = :buyerUsername) " +
            "AND (:courierId IS NULL OR c.id = :courierId) " +
            "AND (:deliveryMethod IS NULL OR o.deliveryMethod = :deliveryMethod) " +
            "AND (:storeId IS NULL OR s.storeId = :storeId) " +
            "AND (:needDelivery IS NULL OR o.delivery = :needDelivery) " +
            "AND (:paymentMethod IS NULL OR o.paymentMethod = :paymentMethod) " +
            "AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus) " +
            "AND (:orderStatus IS NULL OR o.status = :orderStatus) " +
            "AND (:searchStartDt IS NULL OR o.createdAt >= :searchStartDt) " +
            "AND (:searchEndDt IS NULL OR o.createdAt <= :searchEndDt) " +
            "AND (:addressKeyword IS NULL OR ua.fullAddress LIKE CONCAT('%', :addressKeyword, '%')) " +
            "AND (:deliveryDateStart IS NULL OR o.estimatedDeliveryDate >= :deliveryDateStart) " +
            "AND (:deliveryDateEnd IS NULL OR o.estimatedDeliveryDate <= :deliveryDateEnd) " +
            "AND (:appointmentDateStart IS NULL OR o.appointmentDate >= :appointmentDateStart) " +
            "AND (:appointmentDateEnd IS NULL OR o.appointmentDate <= :appointmentDateEnd) " +
            // ================= 【新增】：是否已過期篩選邏輯 =================
            // 邏輯說明：
            // 1. 如果 isExpired 為 null，則不過濾。
            // 2. 如果 isExpired = true (已過期)：
            //    - 線上訂單：創建時間 <= 線上截止時間
            //    - 線下訂單：創建時間 <= 線下截止時間
            // 3. 如果 isExpired = false (未過期)：
            //    - 線上訂單：創建時間 > 線上截止時間
            //    - 線下訂單：創建時間 > 線下截止時間
            //    - 其他非待付款狀態的訂單：視為未過期
            "AND (" +
            "   :isExpired IS NULL OR " +
            "   ( " +
            "       :isExpired = true AND ( " +
            "           (:onlineDeadline IS NOT NULL AND o.paymentMethod = 'PAYPAL_SIM' AND o.createdAt <= :onlineDeadline) OR " +
            "           (:offlineDeadline IS NOT NULL AND o.paymentMethod = 'OFFLINE_STORE' AND o.createdAt <= :offlineDeadline) " +
            "       ) " +
            "   ) OR " +
            "   ( " +
            "       :isExpired = false AND ( " +
            "           (:onlineDeadline IS NOT NULL AND o.paymentMethod = 'PAYPAL_SIM' AND o.createdAt > :onlineDeadline) OR " +
            "           (:offlineDeadline IS NOT NULL AND o.paymentMethod = 'OFFLINE_STORE' AND o.createdAt > :offlineDeadline) OR " +
            "           (o.paymentMethod NOT IN ('PAYPAL_SIM', 'OFFLINE_STORE')) " + // 其他支付方式視為未過期
            "       ) " +
            "   ) " +
            ") " +
            "AND (" +
            "   :amountMode IS NULL OR " +
            "   (:amountMode = 'gt' AND o.totalAmount > :amountVal1) OR " +
            "   (:amountMode = 'lt' AND o.totalAmount < :amountVal1) OR " +
            "   (:amountMode = 'eq' AND o.totalAmount = :amountVal1) OR " +
            "   (:amountMode = 'between' AND o.totalAmount >= :amountVal1 AND o.totalAmount <= :amountVal2)" +
            ")")
    Page<Order> findOrdersWithAdvancedFilters(
            @Param("buyerUsername") String buyerUsername,
            @Param("courierId") Long courierId,
            @Param("amountMode") String amountMode,
            @Param("amountVal1") BigDecimal amountVal1,
            @Param("amountVal2") BigDecimal amountVal2,
            @Param("deliveryMethod") String deliveryMethod,
            @Param("storeId") Long storeId,
            @Param("needDelivery") Boolean needDelivery,
            @Param("paymentMethod") String paymentMethod,
            @Param("paymentStatus") Order.PaymentStatus paymentStatus,
            @Param("orderStatus") Order.OrderStatus orderStatus,
            @Param("searchStartDt") LocalDateTime searchStartDt,
            @Param("searchEndDt") LocalDateTime searchEndDt,
            @Param("addressKeyword") String addressKeyword,
            @Param("deliveryDateStart") LocalDate deliveryDateStart,
            @Param("deliveryDateEnd") LocalDate deliveryDateEnd,
            @Param("appointmentDateStart") LocalDate appointmentDateStart,
            @Param("appointmentDateEnd") LocalDate appointmentDateEnd,
            // 【新增】：過期篩選參數
            @Param("isExpired") Boolean isExpired,
            @Param("onlineDeadline") LocalDateTime onlineDeadline,
            @Param("offlineDeadline") LocalDateTime offlineDeadline,
            Pageable pageable
    );
}