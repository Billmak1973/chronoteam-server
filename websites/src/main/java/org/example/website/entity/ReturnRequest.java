package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "return_request", indexes = {
        @Index(name = "idx_return_user", columnList = "user_id"),
        @Index(name = "idx_return_order", columnList = "order_id"),
        @Index(name = "idx_return_status", columnList = "status"),
        // 新增：方便後台查詢「今天有哪些用戶預約到店退款」
        @Index(name = "idx_return_appointment", columnList = "appointment_date")
})
@Data
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_id")
    private Long returnId;

    // 關聯訂單 (orders.order_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // 關聯訂單明細 (order_item.order_item_id) —— 具體退貨的那一件
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    // 關聯商品 (product.prod_id) —— 退貨成功時恢復庫存用
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Product product;

    // 申請人 (買家)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 退貨原因 (無需照片)
    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    // 狀態：申請中 / 成功 / 取消 / 失敗
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReturnStatus status = ReturnStatus.PENDING;

    // 管理員回覆 (選填，審核成功/失敗時填寫)
    @Column(name = "admin_response", columnDefinition = "TEXT")
    private String adminResponse;

    // ==========================================
    //  線下退款預約 (退款需用戶親自到店辦理)
    // ==========================================
    /** 退款店鋪 ID (與線下店鋪體系一致: store-central / store-tsimsatsui / store-causeway) */
    @Column(name = "refund_store_id", length = 50, nullable = false)
    private String refundStoreId;

    /** 預約退款日期 (用戶預約親自到店的日期，不可早於今天) */
    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    /** 預約時間段 (選填，例如: "14:00 - 16:00"，不需要可刪除) */
    @Column(name = "appointment_time_slot", length = 50)
    private String appointmentTimeSlot;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 審核/取消時間
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public enum ReturnStatus {
        PENDING,    // 申請中
        APPROVED,   // 成功
        CANCELLED,  // 取消
        REJECTED    // 失敗
    }
}