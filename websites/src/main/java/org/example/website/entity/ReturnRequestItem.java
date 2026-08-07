package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "return_request_item")
@Data
public class ReturnRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_item_id")
    private Long returnItemId;

    // 關聯退貨申請主表
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private ReturnRequest returnRequest;

    // 關聯訂單明細 (鎖定當時購買的價格和數量)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    // 關聯商品 (用於恢復庫存)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Product product;

    /**
     * 本次申請退貨的數量
     */
    @Column(name = "return_quantity", nullable = false)
    private Integer returnQuantity;

    /**
     * 鑑定後的實際成色
     * 記錄退貨時手錶的真實狀態，作為調整退款金額的唯一依據
     * 例如：從 NEAR_MINT 降級為 EXCELLENT
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "actual_condition", length = 20)
    private WatchCondition actualCondition;

    /**
     * 【唯一結算標準】最終退款金額
     * 管理員根據 actualCondition 重新定價後填寫。
     * 若為 null，表示尚未完成最終定價；若不為 null，則以此為準進行退款/入賬。
     */
    @Column(name = "final_refund_amount", precision = 10, scale = 2)
    private BigDecimal finalRefundAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}