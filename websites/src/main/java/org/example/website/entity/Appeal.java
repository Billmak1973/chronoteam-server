package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "appeal", indexes = {
        // 從 @Index(name = "idx_appeal_username", columnList = "username") 改成 @Index(name = "idx_appeal_user", columnList = "user_id")
        @Index(name = "idx_appeal_user", columnList = "user_id"),
        @Index(name = "idx_appeal_type", columnList = "appeal_type"),
        @Index(name = "idx_appeal_status", columnList = "status")
})
@Data
public class Appeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long appealId; 並明確指定數據庫列名為 appeal_id
    @Column(name = "appeal_id")
    private Long appealId;

    // 關聯的通知ID
    @Column(name = "notification_id")
    private Long notificationId;

    // 申訴類型
    @Enumerated(EnumType.STRING)
    @Column(name = "appeal_type", nullable = false, length = 20)
    private AppealType appealType;

    // 從 @Column(name = "username", nullable = false, length = 50) private String username;
    // 改成 @ManyToOne 關聯 User 實體的主鍵 id，字段名改為 user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 申訴原因
    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    // 申訴狀態
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppealStatus status = AppealStatus.PENDING;

    // 管理員審核後的回覆/備註
    @Column(name = "admin_response", columnDefinition = "TEXT")
    private String adminResponse;

    // 從 @Column(name = "reviewed_by", length = 50) private String reviewedBy;
    // 改成 @Column(name = "reviewed_by_id") private Long reviewedById; 以統一存儲管理員的 ID
    @Column(name = "reviewed_by_id")
    private Long reviewedById;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // ================= 枚舉定義 =================

    public enum AppealType {
        BAN,            // 禁言申訴
        BLACKLIST,      // 黑名單申訴
        DELETE_REVIEW   // 評論被刪申訴
    }

    public enum AppealStatus {
        PENDING,    // 待處理
        APPROVED,   // 申訴成功
        REJECTED    // 申訴駁回
    }
}