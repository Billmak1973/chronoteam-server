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
        @Index(name = "idx_order_courier", columnList = "courier_id"),
        @Index(name = "idx_order_source", columnList = "order_source") // 為訂單來源建立索引，方便統計線下業績
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
    private User user; // 買家 (若是線下散客，可綁定一個預設的 "Guest" 或 "Walk-in" 用戶)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id")
    private User courier; // 關聯的快遞員 (線下訂單通常為 null)

    /**
     * 【新增】線下開單店員
     * 僅當此訂單為線下門店直接開單 (orderSource = "OFFLINE") 時記錄，用於業績歸屬與審計。
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salesperson_id")
    private User salesperson;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>(); // 訂單商品明細

    // ================= 3. 金額與費用 (Financials) =================
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount; // 訂單總金額 (商品總價 + 運費，線下訂單通常運費為 0)

    @Column(name = "shipping_fee", precision = 10, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO; // 快遞費用

    // ================= 4. 配送與物流信息 (Delivery) =================
    @Column(name = "delivery_method", length = 20)
    private String deliveryMethod; // 配送方式: "EXPRESS" (快遞), "STORE_PICKUP" (自取), "OFFLINE_DIRECT" (線下直接帶走)

    @Column(name = "delivery")
    private Boolean delivery = false; // 是否為快遞配送 (true: 需要快遞, false: 門店自取/線下直接帶走)

    /**
     * 【新增】訂單來源
     * "ONLINE" : 線上商城下單 (包含線上選好去線下付款)
     * "OFFLINE": 純線下門店直接開單 / POS機購買 (店員後台直接創建)
     */
    @Column(name = "order_source", length = 20)
    private String orderSource = "ONLINE";

    // ================= 5. 支付與狀態 (Payment & Status) =================
    @Column(name = "payment_method", length = 20)
    private String paymentMethod = "PAYPAL_SIM"; // 線下直接付款可設為 "CASH", "CREDIT_CARD", "OFFLINE_STORE"

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;
    //  線下直接開單且已收款時，應直接初始化為 PaymentStatus.PAID_OFFLINE 或 PAID_REAL

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", length = 20)
    private OrderStatus status = OrderStatus.PENDING;
    //  線下直接開單且已收款時，應直接初始化為 OrderStatus.COMPLETED 或 PAID

    /**
     * 關聯的線下門店
     * 使用 @ManyToOne 建立多對一關係
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offline_store_id") // 指定外鍵列名
    private OfflineStore offlineStore;

    // ================= 6. 關聯地址信息 (Address) =================
    /**
     * 關聯的收貨地址 (針對快遞配送)
     * 使用 @ManyToOne 建立多對一關係，方便直接獲取收件人姓名、電話和完整地址
     * (純線下訂單此字段通常為 null)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_address_id") // 對應 user_addresses 表的主鍵 address_id
    private UserAddress userAddress;

    // ================= 7. 時間戳 (Timestamps) =================
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt; //  線下直接開單時，應立即設置為當前時間

    // ================= 8. 發貨時效管理 =================
    @Column(name = "received_at")
    private LocalDateTime receivedAt; //  線下直接帶走時，可與 paidAt 設為相同時間

    /** 發貨截止時間 (後端根據收貨時間自動計算的「下一個週日 00:00」，前端 JS 倒計時用) */
    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    /**
     * 預計送達日期 (根據後台「配送模式規則」動態計算得出)
     * (純線下訂單此字段通常為 null 或等於創建日期)
     */
    @Column(name = "estimated_delivery_date")
    private java.time.LocalDate estimatedDeliveryDate;

    /** 預約到店取貨/付費日期 (針對門店自取) */
    @Column(name = "appointment_date")
    private java.time.LocalDate appointmentDate;

    // ================= 9. 用戶界面可見性 =================
    /**
     * 訂單在用戶端是否可見 (用於取消/退貨訂單的「軟刪除」/隱藏)
     * 默認為 true (可見)，用戶點擊刪除後設為 false (隱藏)
     */
    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible = true;

    // ================= 10. 取貨提醒追蹤 =================
    @Column(name = "pickup_reminder_count")
    private Integer pickupReminderCount = 0;

    @Column(name = "last_pickup_reminder_at")
    private LocalDateTime lastPickupReminderAt;

    // ================= 枚舉定義 =================
    public enum OrderStatus {
        PENDING, PAID, SHIPPED, COMPLETED, CANCELLED
    }

    public enum PaymentStatus {
        UNPAID, PAID_SIMULATED, PAID_REAL, PENDING_OFFLINE, PAID_OFFLINE, REFUNDED, REFUNDING, PARTIALLY_REFUNDED
        //  PAID_OFFLINE: 專門用於標識線下門店已完成現金/刷卡支付的訂單
    }
}