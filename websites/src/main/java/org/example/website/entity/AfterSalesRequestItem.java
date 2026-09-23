package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "after_sales_request_item", indexes = {
        @Index(name = "idx_asri_request", columnList = "request_id"),
        @Index(name = "idx_asri_product", columnList = "prod_id")
})
@Data
public class AfterSalesRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_item_id")
    private Long requestItemId;

    // 關聯售後申請主表
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private AfterSalesRequest afterSalesRequest;

    // 關聯原始訂單明細 (鎖定當時購買的價格和數量)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    // 關聯商品 (用於恢復庫存)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Product product;

    /**
     * 本次申請退回的數量
     */
    @Column(name = "return_quantity", nullable = false)
    private Integer returnQuantity;

    /**
     * 鑑定後的實際成色
     * 【重要】：無論是退貨還是換貨，退回的手錶都需要鑑定！
     * 用於判斷是否需要扣減退款金額，或降低換貨時的抵扣價值。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "actual_condition", length = 20)
    private WatchCondition actualCondition;

    /**
     * 【唯一結算標準】該商品的最終結算金額
     * - 如果是退貨 (RETURN)：這代表管理員最終同意退給用戶的金額。
     * - 如果是換貨 (EXCHANGE)：這代表這件舊手錶能「抵扣」新訂單的金額。
     * 若為 null，表示尚未完成最終定價；若不為 null，則以此為準進行財務結算。
     */
    @Column(name = "final_settle_amount", precision = 10, scale = 2)
    private BigDecimal finalSettleAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}