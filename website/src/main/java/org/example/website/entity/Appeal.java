package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "appeal", indexes = {
        @Index(name = "idx_appeal_username", columnList = "username"),
        @Index(name = "idx_appeal_type", columnList = "appeal_type"),
        @Index(name = "idx_appeal_status", columnList = "status")
})
@Data
public class Appeal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 關聯的通知ID (如果是針對某條系統通知申訴，可以關聯起來)
    @Column(name = "notification_id")
    private Long notificationId;

    //  核心新增：申訴類型 (區分是禁言、黑名單還是刪除評論)
    @Enumerated(EnumType.STRING)
    @Column(name = "appeal_type", nullable = false, length = 20)
    private AppealType appealType;

    // 申訴人 (即被處罰的用戶)
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    // 申訴原因 (用戶填寫的申訴理由)
    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    // 優化：使用枚舉管理狀態
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppealStatus status = AppealStatus.PENDING;

    // 管理員審核後的回覆/備註
    @Column(name = "admin_response", columnDefinition = "TEXT")
    private String adminResponse;

    // 審核該申訴的管理員帳號
    @Column(name = "reviewed_by", length = 50)
    private String reviewedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // ================= 枚舉定義 =================

    /**
     * 申訴類型枚舉
     */
    public enum AppealType {
        BAN,            // 禁言申訴 (包含管理員手動禁言、系統頻繁操作自動禁言)
        BLACKLIST,      // 黑名單申訴 (被其他普通用戶拉黑)
        DELETE_REVIEW   // 評論被刪申訴 (管理員刪除了用戶的評論)
    }

    /**
     * 申訴狀態枚舉
     */
    public enum AppealStatus {
        PENDING,    // 待處理 (剛提交)
        APPROVED,   // 申訴成功 (管理員同意，系統自動解除對應處罰)
        REJECTED    // 申訴駁回 (管理員拒絕，維持原處罰)
    }
}