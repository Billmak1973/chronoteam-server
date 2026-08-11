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

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // 根據訂單號和用戶名查詢，確保用戶只能操作自己的訂單（防越權）
    Optional<Order> findByOrderNoAndUser_Username(String orderNo, String username);

    List<Order> findByUser_UsernameOrderByCreatedAtDesc(String username);

    List<Order> findByUser_UsernameAndStatusAndIsVisibleTrue(String username, Order.OrderStatus status);

    @Query(value = "SELECT o FROM Order o JOIN FETCH o.user",
            countQuery = "SELECT count(o) FROM Order o")
    Page<Order> findAllWithUsers(Pageable pageable);

    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 【新增】待付款分頁查詢 (線上支付 PAYPAL_SIM + 狀態 UNPAID)
     */
    @Query("SELECT o FROM Order o WHERE o.user.username = :username " +
            "AND o.paymentMethod = 'PAYPAL_SIM' " +
            "AND o.paymentStatus = 'UNPAID' " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findUnpaidOrders(@Param("username") String username, Pageable pageable);

    /**
     * 【新增】待線下付款分頁查詢 (支付方式 OFFLINE_STORE + 狀態 PENDING_OFFLINE)
     */
    @Query("SELECT o FROM Order o WHERE o.user.username = :username " +
            "AND o.paymentMethod = 'OFFLINE_STORE' " +
            "AND o.paymentStatus = 'PENDING_OFFLINE' " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findPendingOfflineOrders(@Param("username") String username, Pageable pageable);

    /**
     * 【新增】已支付分頁查詢 (包含 PAID_SIMULATED, PAID_REAL, PAID_OFFLINE)
     */
    @Query("SELECT o FROM Order o WHERE o.user.username = :username " +
            "AND o.paymentStatus IN ('PAID_SIMULATED', 'PAID_REAL', 'PAID_OFFLINE') " +
            "ORDER BY o.createdAt DESC")
    Page<Order> findPaidOrders(@Param("username") String username, Pageable pageable);

}