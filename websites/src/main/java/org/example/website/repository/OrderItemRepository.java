package org.example.website.repository;

import org.example.website.entity.Order;
import org.example.website.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 訂單明細數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link OrderItem} 實體的高效關聯查詢方法。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * 根據訂單編號查詢該訂單下的所有商品明細記錄。
     * <p>
     * 通常用於訂單詳情頁展示或結帳頁面的商品清單渲染。
     * </p>
     *
     * @param orderNo 訂單編號
     * @return 該訂單包含的所有商品明細列表
     */
    List<OrderItem> findByOrder_OrderNo(String orderNo);

    /**
     * 【核心優化】一次性 SQL 查詢：找出指定用戶購買過特定商品且已付款的訂單編號列表。
     * <p>
     * 通過 JOIN 關聯 Order 和 OrderItem，並過濾支付狀態，避免在內存中進行多次循環查詢 (N+1 問題)。
     * 通常用於判斷用戶是否具備對某商品發表評價的資格。
     * </p>
     *
     * @param username     用戶名
     * @param productId    商品 ID
     * @param paidStatuses 允許的已付款狀態列表 (例如: PAID_SIMULATED, PAID_REAL, PAID_OFFLINE)
     * @return 符合條件的訂單編號列表 (按創建時間倒序排列)
     */
    @Query("SELECT o.orderNo FROM OrderItem oi JOIN oi.order o " +
            "WHERE o.user.username = :username " +
            "AND oi.product.id = :productId " +
            "AND o.paymentStatus IN :paidStatuses " +
            "ORDER BY o.createdAt DESC")
    List<String> findPaidOrderNosByUsernameAndProductId(
            @Param("username") String username,
            @Param("productId") Integer productId,
            @Param("paidStatuses") List<Order.PaymentStatus> paidStatuses
    );

    /**
     * 根據訂單 ID 列表批量查詢對應的訂單明細記錄。
     * <p>
     * 通常用於後台訂單列表加載時，一次性獲取多個訂單的商品明細，以優化查詢性能。
     * </p>
     *
     * @param orderIds 訂單 ID 列表
     * @return 匹配這些訂單 ID 的所有明細記錄列表
     */
    List<OrderItem> findByOrder_OrderIdIn(List<Long> orderIds);
}