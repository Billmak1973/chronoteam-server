package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "view_history", indexes = {
        // 從 @Index(name = "idx_vh_username", columnList = "cust_username") 改成 @Index(name = "idx_vh_user", columnList = "user_id")！
        @Index(name = "idx_vh_user", columnList = "user_id"),
        @Index(name = "idx_vh_viewed_at", columnList = "viewed_at")
})
@Data
public class ViewHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long historyId; 並明確指定數據庫列名為 history_id！
    @Column(name = "history_id")
    private Long historyId;

    // 從 @JoinColumn(name = "cust_username", nullable = false) private Customer customer; 改成 @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false) private User user;！
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Product product;

    @CreationTimestamp
    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}