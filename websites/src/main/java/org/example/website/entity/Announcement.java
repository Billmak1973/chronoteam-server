package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "announcements", indexes = {
        @Index(name = "idx_ann_type", columnList = "type"),
        @Index(name = "idx_ann_target", columnList = "target_type, target_id") // 優化複合索引
})
@Data
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "announcement_id")
    private Long announcementId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private AnnouncementType type;

    // 【核心修改】：將 String 改為枚舉，並使用 EnumType.STRING 確保數據庫存儲為字符串
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private TargetAudience targetType;

    // 【輔助字段】：當 targetType 為 SPECIFIC_ROLE 時，儲存具體的角色字符串
    @Column(name = "target_role", length = 50)
    private String targetRole;

    // 關聯的目標 ID
    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==========================================
    // 枚舉定義
    // ==========================================
    public enum AnnouncementType {
        SYSTEM,         // 系統全局公告
        STOCK,          // 到貨通知
        MAINTENANCE,    // 停機維護通知
        PROMOTION       // 專屬優惠/促銷
    }

    // 目標受眾枚舉
    public enum TargetAudience {
        ALL,             // 全體用戶
        ALL_STAFF,       // 全體工作人員
        SPECIFIC_ROLE,   // 指定角色 (配合 targetRole 字段使用)
        SPECIFIC_STORE,  // 指定店鋪 (配合 targetId 使用)
        SPECIFIC_USER,   // 指定用戶 (配合 targetId 使用)
        SPECIFIC_PRODUCT // 指定商品 (配合 targetId 使用)
    }
}