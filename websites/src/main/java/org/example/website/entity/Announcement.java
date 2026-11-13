package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "announcements", indexes = {
        @Index(name = "idx_ann_type", columnList = "type"),
        @Index(name = "idx_ann_target", columnList = "target_type, target_id"),
        // 按門店維度查詢公告的索引（例如：某店鋪經理只看自己店鋪的公告）
        @Index(name = "idx_ann_target_store", columnList = "target_store_id")
})
@Data
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "announcement_id")
    private Long announcementId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    // 標題對齊方式，默認值設為 LEFT
    @Column(name = "title_alignment", length = 20)
    private String titleAlignment = "LEFT";

    /**
     * 公告內容：必須使用 @Lob 和 TEXT 類型，才能存儲帶有 HTML 標籤的長文本
     */
    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private AnnouncementType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private TargetAudience targetType;

    /**
     * 【輔助字段】：當 targetType 為 SPECIFIC_ROLE 時，儲存具體的角色字符串。
     * 【擴展】：當 targetType 為 SPECIFIC_STORE 時，此字段可儲存「該店鋪內」的角色篩選，
     * 例如 "ALL_STORE_STAFF"（全店員工）或 "SALES,APPRAISER"（多角色逗號分隔）。
     */
    @Column(name = "target_role", length = 100)
    private String targetRole;

    /**
     * 關聯的目標 ID（僅用於 SPECIFIC_USER / SPECIFIC_PRODUCT 場景）
     */
    @Column(name = "target_id")
    private Long targetId;

    // ==========================================
    // 門店專屬外鍵
    // ==========================================
    /**
     * 關聯的線下門店 ID (offline_stores.store_id)
     * 【業務邏輯】：
     * 僅當 targetType == SPECIFIC_STORE 時必填，其他場景必須為 null。
     * 獨立於 targetId 的原因：targetId 需兼顧用戶/商品場景，
     * 拆分後語義清晰，且可按門店維度建立索引與後續對賬。
     */
    @Column(name = "target_store_id")
    private Long targetStoreId;

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
        SYSTEM, STOCK, MAINTENANCE, PROMOTION
    }

    public enum TargetAudience {
        ALL,
        ALL_STAFF,
        SPECIFIC_ROLE,
        SPECIFIC_STORE,   // 指定店鋪 (配合 targetStoreId + targetRole 使用)
        SPECIFIC_USER,    // 指定用戶 (配合 targetId 使用)
        SPECIFIC_PRODUCT  // 指定商品 (配合 targetId 使用)
    }
}