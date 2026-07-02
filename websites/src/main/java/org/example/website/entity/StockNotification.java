package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_notification", uniqueConstraints = {
        // 從 @UniqueConstraint(columnNames = {"product_id", "username"})
        // 改成 @UniqueConstraint(columnNames = {"product_id", "user_id"})
        @UniqueConstraint(columnNames = {"product_id", "user_id"})
})
@Data
public class StockNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long stockNotificationId; 並明確指定數據庫列名為 stock_notification_id
    @Column(name = "stock_notification_id")
    private Long stockNotificationId;

    // 關聯商品實體 (對應 product_id INT NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 從 @Column(name = "username", nullable = false, length = 50) private String username;
    // 改成 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",  nullable = false)
    private User user;

    // 是否已發送通知 (對應 notified BOOLEAN DEFAULT FALSE)
    @Column(name = "notified")
    private Boolean notified = false;

    // 訂閱時間 (對應 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}