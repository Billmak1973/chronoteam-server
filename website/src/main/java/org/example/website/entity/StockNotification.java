package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_notification", uniqueConstraints = {
        // 對應 SQL 中的 UNIQUE KEY unique_product_user (product_id, username)
        // 防止同一個用戶對同一個商品重複訂閱
        @UniqueConstraint(columnNames = {"product_id", "username"})
})
@Data
public class StockNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 關聯商品實體 (對應 product_id INT NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 訂閱用戶名 (對應 username VARCHAR(50) NOT NULL)
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    // 是否已發送通知 (對應 notified BOOLEAN DEFAULT FALSE)
    @Column(name = "notified")
    private Boolean notified = false;

    // 訂閱時間 (對應 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}