package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rate_limit_log", indexes = {
        // 核心索引：快速查詢某個用戶當前是否處於封禁狀態且未過期
        @Index(name = "idx_user_status_banned", columnList = "user_id, status, banned_until")
})
public class RateLimitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;  // 觸發限流/封禁的時間

    @Column(name = "times", nullable = false)
    private Integer times = 1;  // 觸發時的違規操作次數 (例如 30)

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;  // 最後更新時間

    @Column(name = "banned_until")
    private LocalDateTime bannedUntil;  // 封禁結束時間

    @Column(name = "banned_by", length = 50)
    private String bannedBy = "SYSTEM"; // 觸發來源，默認系統自動觸發

    @Column(name = "ban_reason", columnDefinition = "TEXT")
    private String banReason; // 封禁原因

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LimitStatus status = LimitStatus.NORMAL;

    public enum LimitStatus {
        NORMAL,   // 正常 (此表主要記錄異常，NORMAL 可作為初始狀態)
        BANNED,   // 已封禁 (正在封禁期中)
        EXPIRED   // 已解封 (封禁期已過，可選，主要靠 banned_until 判斷)
    }
}