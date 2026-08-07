package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_request", indexes = {
        @Index(name = "idx_exchange_order", columnList = "original_order_id"),
        @Index(name = "idx_exchange_return", columnList = "return_request_id"),
        @Index(name = "idx_exchange_new_order", columnList = "new_order_id")
})
@Data
public class ExchangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exchange_id")
    private Long exchangeId;

    // 關聯原始訂單 (顧客A買的舊手錶)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_order_id", nullable = false)
    private Order originalOrder;

    // 關聯退貨申請 (舊手錶退回來的記錄)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    // 關聯新訂單 (顧客A換的新手錶)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_order_id")
    private Order newOrder;

    // 換貨原因
    @Column(name = "reason", length = 500)
    private String reason;

    /**
     * 鑑定後舊錶的實際成色
     * 用於判斷是否需要扣減舊錶的抵扣價值
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "actual_old_condition", length = 20)
    private WatchCondition actualOldCondition;

    /**
     * 【唯一結算標準】最終結算差價
     * 管理員根據 actualOldCondition 重新評估舊錶價值後，計算得出的最終差價。
     * 正數：用戶需補款；負數：平台需退還多餘款項；0：等價交換。
     * 此字段不為 null 時，代表換貨金額已最終鎖定並可執行支付/退款。
     */
    @Column(name = "final_price_difference", precision = 10, scale = 2)
    private BigDecimal finalPriceDifference;

    // 換貨狀態
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExchangeStatus status = ExchangeStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum ExchangeStatus {
        PENDING,          // 待處理 (等待店鋪/倉庫驗收舊錶)
        VERIFIED,         // 已驗收 (舊錶無損壞，同意換貨)
        WAITING_PAYMENT,  // 待補差價 (如果新錶更貴或舊錶降級導致差價變正)
        COMPLETED,        // 已完成 (新訂單已支付/發貨)
        REJECTED,         // 已拒絕 (舊錶有損壞或不符合換貨條件)
        CANCELLED         // 已取消 (用戶主動放棄換貨)
    }
}