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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}