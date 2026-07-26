package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "announcements", indexes = {
        @Index(name = "idx_ann_type", columnList = "type"),
        @Index(name = "idx_ann_target", columnList = "target_type, target_id")
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

    // 目標受眾類型
    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType; // 例如: "ALL" 或 "SPECIFIC_PRODUCT"

    // 關聯的目標 ID (例如商品 ID，若為全站公告則為 NULL)
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

    public enum AnnouncementType {
        SYSTEM,         // 系統全局公告 (例如：平台升級、節日活動)
        STOCK,          // 到貨通知 (例如：某款手錶補貨)
        MAINTENANCE,    // 停機維護通知
        PROMOTION       // 專屬優惠/促銷
    }
}