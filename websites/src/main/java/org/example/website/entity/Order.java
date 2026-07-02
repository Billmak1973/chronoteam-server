package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
        // 唯一訂單號
        @Index(name = "uk_order_no", columnList = "order_no", unique = true),
        // 從 @Index(name = "idx_order_username", columnList = "cust_username") 改成 @Index(name = "idx_order_user", columnList = "user_id")
        @Index(name = "idx_order_user", columnList = "user_id")
})
@Data
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    // 從 private Long id; 改成 private Long orderId;
    private Long orderId;

    @Column(name = "order_no", length = 50, nullable = false, unique = true)
    private String orderNo;

    // 從 @JoinColumn(name = "cust_username", referencedColumnName = "cust_username", nullable = false) private Customer customer;
    // 改成 @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    //  ENUM 字段：使用 @Enumerated + String 類型存儲
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod = "PAYPAL_SIM";

    // 記錄具體的線下店鋪 ID (可為空)
    @Column(name = "offline_store_id", length = 50)
    private String offlineStoreId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    //  訂單狀態枚舉
    public enum OrderStatus {
        PENDING, PAID, SHIPPED, COMPLETED, CANCELLED
    }

    //  支付狀態枚舉
    public enum PaymentStatus {
        UNPAID, PAID_SIMULATED, PAID_REAL, PENDING_OFFLINE, PAID_OFFLINE
    }
}