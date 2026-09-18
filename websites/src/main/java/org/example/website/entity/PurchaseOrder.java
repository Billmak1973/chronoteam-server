package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_order", indexes = {
        @Index(name = "idx_purchase_date", columnList = "purchase_date"),
        @Index(name = "idx_purchase_status", columnList = "status")
})
@Data
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id")
    private Long purchaseId;

    /** 關聯商品 (若是全新建檔的商品，需先在商品管理中新建，或在此處擴展新建邏輯) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 進貨數量 */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * 總進貨成本 (平台付出的總收購價)
     * 【核心業務】：當此訂單狀態變更為 COMPLETED 時，此數值將直接累加到
     * DailyBusinessReport 的 acquisitionCost (收購成本) 中。
     */
    @Column(name = "total_cost", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalCost;

    /** 供應商/渠道名稱 (例如：XX拍賣行、同行調貨、當鋪) */
    @Column(name = "supplier", length = 100)
    private String supplier;

    /** 入庫倉庫類型 (ONLINE: 線上總倉, OFFLINE: 線下門店) */
    @Enumerated(EnumType.STRING)
    @Column(name = "warehouse_type", length = 20, nullable = false)
    private WarehouseType warehouseType;

    /** 若入庫線下門店，則關聯具體門店 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private OfflineStore store;

    /** 進貨日期 (用於匹配當日的 DailyBusinessReport) */
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    /** 操作管理員 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id", nullable = false)
    private User operator;

    /** 狀態: PENDING (待入庫), COMPLETED (已完成), CANCELLED (已取消) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PurchaseStatus status = PurchaseStatus.PENDING;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    /**
     * 關聯的實際庫存記錄 ID
     * 【業務邏輯】：
     * 1. 若為「線下門店」入庫：此處將記錄 StoreInventory 表的 inventoryId。
     * 2. 若為「線上總倉」入庫：此處可為 null (因為直接更新 Product 表的 stock 字段，無獨立庫存記錄表)。
     * 3. 當 status 變更為 COMPLETED 且庫存實際更新成功後，此字段會被寫入。
     */
    @Column(name = "inventory_record_id")
    private Long inventoryRecordId;

    // ================= 【新增字段】 =================
    /**
     * 是否已經實際進入庫存 (入庫完成標誌)
     * 【業務邏輯】：
     * 默認為 false。當管理員執行「確認入庫」操作，且後端成功更新 Product.stock 或 StoreInventory.quantity
     * 並寫入 InventoryAdjustmentLog 後，將此字段設為 true。
     * 這可以防止重複入庫或訂單狀態為 COMPLETED 但庫存實際未更新的異常情況。
     */
    @Column(name = "is_stocked_in", nullable = false)
    private Boolean isStockedIn = false;
    // ================================================

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ================= 枚舉定義 =================

    public enum WarehouseType {
        ONLINE,   // 線上總倉
        OFFLINE   // 線下門店
    }

    public enum PurchaseStatus {
        PENDING,
        COMPLETED,
        CANCELLED
    }
}