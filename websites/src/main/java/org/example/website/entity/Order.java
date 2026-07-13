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
        @Index(name = "uk_order_no", columnList = "order_no", unique = true),
        @Index(name = "idx_order_user", columnList = "user_id"),
        @Index(name = "idx_order_courier", columnList = "courier_id")
})
@Data
public class Order {

    // ================= 1. 主鍵與唯一標識 =================
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_no", length = 50, nullable = false, unique = true)
    private String orderNo;

    // ================= 2. 關聯實體 (Relations) =================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 買家

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id")
    private User courier; // 關聯的快遞員

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>(); // 訂單商品明細

    // ================= 3. 金額與費用 (Financials) =================
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount; // 訂單總金額 (商品總價 + 快遞費)

    @Column(name = "shipping_fee", precision = 10, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO; // 快遞費用

    // ================= 4. 配送與物流信息 (Delivery) =================
    @Column(name = "delivery_method", length = 20)
    private String deliveryMethod; // 配送方式: "EXPRESS" (快遞) 或 "STORE_PICKUP" (自取)

    @Column(name = "delivery")
    private Boolean delivery = false; // 是否為快遞配送 (true: 需要快遞, false: 門店自取/線下)

    // ================= 5. 支付與狀態 (Payment & Status) =================
    @Column(name = "payment_method", length = 20)
    private String paymentMethod = "PAYPAL_SIM";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "offline_store_id", length = 50)
    private String offlineStoreId; // 記錄具體的線下店鋪 ID (可為空)

    // ================= 6. 時間戳 (Timestamps) =================
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // ================= 7. 發貨時效管理 (新增) =================
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** 發貨截止時間 (後端根據收貨時間自動計算的「下一個週日 00:00」，前端 JS 倒計時用) */
    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    // ================= 枚舉定義 =================
    public enum OrderStatus {
        PENDING, PAID, SHIPPED, COMPLETED, CANCELLED
    }

    public enum PaymentStatus {
        UNPAID, PAID_SIMULATED, PAID_REAL, PENDING_OFFLINE, PAID_OFFLINE
    }
}