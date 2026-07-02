package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rate_limit_log_history")
public class RateLimitLogHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long historyId; 並明確指定數據庫列名為 history_id
    @Column(name = "history_id")
    private Long historyId;

    // 從 @Column(name = "username", nullable = false, length = 50) private String username; 改成 @ManyToOne 關聯 User 實體的主鍵 id，字段名改為 user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;

    @Column(name = "times", nullable = false)
    private Integer times;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "banned_until")
    private LocalDateTime bannedUntil;

    @Column(name = "unbanned_at")
    private LocalDateTime unbannedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}