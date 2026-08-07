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
@Table(name = "return_request", indexes = {
        @Index(name = "idx_return_user", columnList = "user_id"),
        @Index(name = "idx_return_order", columnList = "order_id"),
        @Index(name = "idx_return_status", columnList = "status"),
        @Index(name = "idx_return_appointment", columnList = "appointment_date")
})
@Data
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_id")
    private Long returnId;

    // 關聯訂單 (無論整單還是部分退，都必須知道是哪個訂單)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // 申請人 (買家)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 退貨明細列表 (一對多關係)
    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<ReturnRequestItem> items = new ArrayList<>();

    // 退貨原因
    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    // 狀態
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReturnStatus status = ReturnStatus.PENDING;

    // 線下退款預約信息
    @Column(name = "refund_store_id", length = 50, nullable = false)
    private String refundStoreId;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time_slot", length = 50)
    private String appointmentTimeSlot;

    /**
     * 【新增】總退款金額
     * 由後端根據 items 中的 refundAmount 累加得出，避免每次都要遍歷查詢
     */
    @Column(name = "total_refund_amount", precision = 10, scale = 2)
    private BigDecimal totalRefundAmount;

    /**
     * 【新增】實際退款完成時間
     * 當店員在線下核實商品並執行退款時，記錄此時間戳
     * 用於區分「已預約但未到店」和「已完成退款」的狀態
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ReturnStatus {
        PENDING,    // 待到店 / 審核中
        APPROVED,   // 已退款 / 已完成
        CANCELLED,  // 用戶取消
        REJECTED    // 拒絕退款 (如商品損壞、超過期限等)
    }

    // ==========================================
    // 💡 業務邏輯輔助方法：如何區分整單與部分退貨？
    // ==========================================

    /**
     * 判斷是否為「整單退貨」
     * 邏輯：檢查該訂單下的每一個 OrderItem，是否都在退貨列表中，
     * 且每個商品的退貨數量(returnQuantity)都等於原購買數量(orderItem.quantity)。
     */
    @Transient
    public boolean isFullOrderReturn() {
        if (this.order == null || this.items == null || this.items.isEmpty()) {
            return false;
        }

        // 獲取訂單的所有原始明細
        List<OrderItem> originalItems = this.order.getItems();

        // 1. 數量校驗：退貨的商品種類數必須等於訂單原始種類數
        if (originalItems.size() != this.items.size()) {
            return false;
        }

        // 2. 深度校驗：每一種商品的退貨數量都必須等於原購買數量
        for (OrderItem original : originalItems) {
            boolean foundMatch = false;
            for (ReturnRequestItem returnItem : this.items) {
                // 通過 prod_id 匹配同一個商品
                if (returnItem.getProduct().getProductId().equals(original.getProduct().getProductId())) {
                    // 如果退貨數量 < 原購買數量，說明只是部分退貨
                    if (!returnItem.getReturnQuantity().equals(original.getQuantity())) {
                        return false;
                    }
                    foundMatch = true;
                    break;
                }
            }
            // 如果有一個原始商品沒在退貨列表裡找到，也不是整單退
            if (!foundMatch) {
                return false;
            }
        }
        return true;
    }
}