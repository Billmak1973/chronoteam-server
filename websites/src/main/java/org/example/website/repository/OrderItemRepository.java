package org.example.website.repository;

import org.example.website.entity.Order;
import org.example.website.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrder_OrderNo(String orderNo);

    //  核心優化：一次 SQL 查詢搞定！找出用戶購買過該商品且已付款的訂單號
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
}