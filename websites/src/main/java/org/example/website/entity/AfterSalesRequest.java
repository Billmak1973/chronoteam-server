package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "after_sales_request", indexes = {
        @Index(name = "idx_asr_user", columnList = "user_id"),
        @Index(name = "idx_asr_order", columnList = "original_order_id"),
        @Index(name = "idx_asr_type_status", columnList = "request_type, status")
})
@Data
public class AfterSalesRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    // 申請類型
    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private RequestType requestType; // RETURN (退貨) 或 EXCHANGE (換貨)

    // 關聯原始訂單 (無論是退還是換，都必須知道原訂單)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_order_id", nullable = false)
    private Order originalOrder;

    // 關聯新訂單 (僅在 EXCHANGE 換貨時有值，退貨時為 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_order_id")
    private Order newOrder;

    // 申請人 (買家)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 售後明細列表 (一對多關係)
    @OneToMany(mappedBy = "afterSalesRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<AfterSalesRequestItem> items = new ArrayList<>();

    // 申請原因
    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    // 狀態 (合併了退貨和換貨的生命週期)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    // --- 線下處理預約信息 (退貨和換貨都適用) ---
    @Column(name = "store_id", length = 50)
    private String storeId; // 處理退貨/換貨的門店ID

    @Column(name = "appointment_date")
    private LocalDate appointmentDate;

    @Column(name = "appointment_time_slot", length = 50)
    private String appointmentTimeSlot;

    // --- 財務結算字段 ---

    /**
     * 【換貨專用】最終結算差價
     * 新訂單總價 - 舊商品總鑑定抵扣價 = 差價。
     * 正數：用戶需補款；負數：平台需退還多餘款項；0：等價交換。
     * (若是純退貨，此字段為 null 或 0)
     */
    @Column(name = "final_price_difference", precision = 10, scale = 2)
    private BigDecimal finalPriceDifference;

    /**
     * 實際處理完成時間 (店員核實商品並執行退款/發出新貨的時間)
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==========================================
    // 💡 枚舉定義
    // ==========================================
    public enum RequestType {
        RETURN,   // 退貨退款
        EXCHANGE  // 換貨
    }

    public enum RequestStatus {
        PENDING,          // 待到店 / 待審核
        VERIFIED,         // 已驗收/已鑑定 (店鋪已收到舊物並確認成色)
        WAITING_PAYMENT,  // 待補差價 (僅換貨時，且新商品更貴或舊物降級時觸發)
        COMPLETED,        // 已完成 (退款已發放 或 新貨已發出)
        REJECTED,         // 已拒絕 (如商品人為損壞、超過期限等)
        CANCELLED         // 用戶主動取消
    }

    // ==========================================
    // 💡 業務邏輯輔助方法
    // ==========================================
    @Transient
    public boolean isFullOrderReturn() {
        if (this.requestType != RequestType.RETURN || this.originalOrder == null || this.items == null || this.items.isEmpty()) {
            return false;
        }
        List<OrderItem> originalItems = this.originalOrder.getItems();
        if (originalItems.size() != this.items.size()) {
            return false;
        }
        for (OrderItem original : originalItems) {
            boolean foundMatch = false;
            for (AfterSalesRequestItem returnItem : this.items) {
                if (returnItem.getProduct().getProductId().equals(original.getProduct().getProductId())) {
                    if (!returnItem.getReturnQuantity().equals(original.getQuantity())) {
                        return false;
                    }
                    foundMatch = true;
                    break;
                }
            }
            if (!foundMatch) return false;
        }
        return true;
    }
}