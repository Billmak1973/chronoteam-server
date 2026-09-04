package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_adjustment_log", indexes = {
        // 建立索引以提升後台查詢日誌的性能
        @Index(name = "idx_log_product", columnList = "product_id"),
        @Index(name = "idx_log_operator", columnList = "operator_id"),
        @Index(name = "idx_log_created_at", columnList = "created_at")
})
@Data
public class InventoryAdjustmentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    // 1. 關聯商品
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 2. 關聯操作人 (管理員/員工)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    // 3. 倉庫類型：ONLINE (線上總倉), OFFLINE (線下門店)
    @Enumerated(EnumType.STRING)
    @Column(name = "warehouse_type", nullable = false, length = 20)
    private WarehouseType warehouseType;

    // 4. 關聯門店 (如果是調整線下門店庫存，則記錄具體門店；線上總倉則為 null)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private OfflineStore store;

    // 5. 調整前的庫存數量
    @Column(name = "previous_quantity", nullable = false)
    private Integer previousQuantity;

    // 6. 調整後的庫存數量
    @Column(name = "new_quantity", nullable = false)
    private Integer newQuantity;

    // 7. 變動數量 (正數為增加，負數為減少，方便財務統計)
    @Column(name = "change_quantity", nullable = false)
    private Integer changeQuantity;

    // 8. 調整原因 (例如：盤點修正、進貨入庫、破損報廢、客戶退貨、系統錯誤修正)
    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    // 9. 總成本 / 新增花費 (記錄此次庫存調整涉及的總金額，方便財務對帳與統計)
    // precision = 12 表示總共 12 位數字，scale = 2 表示小數點後 2 位 (例如: 9999999999.99)
    @Column(name = "total_cost", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalCost;

    // 10. 操作時間 (自動記錄)
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
}