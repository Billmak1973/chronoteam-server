package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_interaction_stats",
        // 從 @UniqueConstraint(columnNames = {"username", "type"}) 改成 @UniqueConstraint(columnNames = {"user_id", "type"})
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "type"}))
@Data
public class UserInteractionStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long statsId; 並明確指定數據庫列名為 stats_id
    @Column(name = "stats_id")
    private Long statsId;

    // 從 @Column(name = "username", nullable = false, length = 50) private String username;
    // 改成 @ManyToOne 關聯 User 實體的主鍵 id，字段名改為 user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 定義枚舉或常量來區分類型，方便擴展
    @Column(name = "type", nullable = false, length = 20)
    private String type; // 例如: "REVIEW_REPLY", "REVIEW_MENTION"

    @Column(name = "last_viewed_at", nullable = false)
    private LocalDateTime lastViewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}