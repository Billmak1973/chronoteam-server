package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_adjustment_log", indexes = {
        @Index(name = "idx_log_product", columnList = "product_id"),
        @Index(name = "idx_log_operator", columnList = "operator_id"),
        @Index(name = "idx_log_type", columnList = "adjustment_type"), // 新增：按調整類型查詢
        @Index(name = "idx_log_batch", columnList = "transfer_batch_id"), // 新增：按調撥批次對賬
        @Index(name = "idx_log_created_at", columnList = "created_at")
})
@Data
public class InventoryAdjustmentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    @Enumerated(EnumType.STRING)
    @Column(name = "warehouse_type", nullable = false, length = 20)
    private WarehouseType warehouseType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private OfflineStore store;

    @Column(name = "previous_quantity", nullable = false)
    private Integer previousQuantity;

    @Column(name = "new_quantity", nullable = false)
    private Integer newQuantity;

    @Column(name = "change_quantity", nullable = false)
    private Integer changeQuantity;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    // ================= 【新增核心字段】 =================

    /**
     * 調整類型 (精確區分業務場景)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 20)
    private AdjustmentType adjustmentType;

    /**
     * 調撥批次號 (Transfer Batch ID)
     * 【業務邏輯】：
     * 當 adjustmentType 為 TRANSFER_OUT (調撥出庫) 或 TRANSFER_IN (調撥入庫) 時，
     * 「源倉庫」和「目標倉庫」的兩條日誌記錄會共享同一個 UUID 批次號。
     * 後台可透過此 ID 將兩筆記錄關聯，確保調撥數量的絕對平衡 (出庫量 = 入庫量)。
     */
    @Column(name = "transfer_batch_id", length = 50)
    private String transferBatchId;

    // ====================================================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 倉庫類型枚舉
     */
    public enum WarehouseType {
        ONLINE,  // 線上總倉 (對應 Product 表中的 stock)
        OFFLINE  // 線下門店 (對應 StoreInventory 表中的 quantity)
    }

    /**
     * 庫存調整類型枚舉
     */
    public enum AdjustmentType {
        PURCHASE_IN,      // 採購進貨入庫 (配合 PurchaseOrder 使用)
        MANUAL_ADJUST,    // 手動盤點調整 (盤盈為正，盤虧為負)
        TRANSFER_OUT,     // 調撥出庫 (源倉庫扣減，changeQuantity 為負數)
        TRANSFER_IN,      // 調撥入庫 (目標倉庫增加，changeQuantity 為正數)
        DAMAGE,           // 破損報廢 (扣減)
        RETURN,           // 客戶退貨入庫 (增加)
        SALE_OUT          // 銷售出庫 (扣減)
    }
}