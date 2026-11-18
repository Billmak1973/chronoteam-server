package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

//專門管理 「各線下門店的庫存」
@Entity
@Table(name = "store_inventory",
        uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "product_id"})) // 確保同一門店的同一商品只有一條記錄
@Data
public class StoreInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Long inventoryId;

    // 關聯線下門店
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private OfflineStore store;

    // 關聯商品
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 該門店的可用庫存數量
    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    // 該門店已被線上訂單「預留/鎖定」的數量
    // 這部分庫存雖然還在倉庫裡，但不能賣給線下散客，也不能被其他線上訂單搶佔
    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 【輔助方法】獲取當前實際可售庫存
    // 可售庫存 = 物理總庫存 - 已預留庫存
    @Transient
    public Integer getAvailableStock() {
        int available = this.quantity - this.reservedQuantity;
        return Math.max(available, 0); // 防止出現負數
    }
}