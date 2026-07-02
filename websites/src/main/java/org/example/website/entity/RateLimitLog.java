package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rate_limit_log")
public class RateLimitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long logId; 並明確指定數據庫列名為 log_id
    @Column(name = "log_id")
    private Long logId;

    // 從 @Column(name = "username", nullable = false, length = 50) private String username;
    // 改成 @ManyToOne 關聯 User 實體的主鍵 id，字段名改為 user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;  // 1分钟窗口的开始时间

    @Column(name = "times", nullable = false)
    private Integer times = 1;  // 1分钟内的操作次数，默认1

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;  // 最后更新时间

    @Column(name = "banned_until")
    private LocalDateTime bannedUntil;  // 封禁结束时间（如果被封禁）

    /**
     * 執行禁言的管理員帳號
     * 如果是系統自動觸發（如頻繁點贊），可以設為 "SYSTEM"
     */
    @Column(name = "banned_by", length = 50)
    private String bannedBy;

    /**
     * 禁言原因
     * 使用 TEXT 類型以支持較長的說明文字
     */
    @Column(name = "ban_reason", columnDefinition = "TEXT")
    private String banReason;
}