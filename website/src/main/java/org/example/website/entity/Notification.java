package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipient_username"),
        @Index(name = "idx_notification_read", columnList = "is_read")
})
@Data
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 接收者 (關聯用戶名)
    @Column(name = "recipient_username", nullable = false, length = 50)
    private String recipientUsername;

    // 發送者/操作者：對於系統通知，這裡存儲執行操作的管理員賬號
    @Column(name = "sender_username", length = 50)
    private String senderUsername;

    //  簡化：只保留 SYSTEM 類型，用於涵蓋所有平台級通知（如刪除評論、封禁等）
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    // 通知標題/摘要：顯示在列表中的簡短文字
    // 例如："您的評論已被移除"
    @Column(name = "title", length = 100)
    private String title;

    // 通知詳細內容：顯示具體說明
    // 例如："由於您的評論包含違規廣告鏈接，根據社區規範第X條，我們已將其移除。"
    @Column(name = "content", length = 500, columnDefinition = "TEXT")
    private String content;

    //  核心字段：被刪除的原始評論內容（讓用戶知道哪句話被刪了）
    @Column(name = "deleted_content", length = 1000, columnDefinition = "TEXT")
    private String deletedContent;

    //  核心字段：管理員填寫的刪除原因（透明化執法，減少不滿）
    @Column(name = "delete_reason", length = 500, columnDefinition = "TEXT")
    private String deleteReason;

    // 點擊跳轉的目標 URL (例如: /product/1#review-123，雖然評論沒了，但可以跳轉到商品頁)
    @Column(name = "target_url", length = 255)
    private String targetUrl;

    // 是否已讀
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    // 關聯的評論 ID (方便後台審計或前端標記)
    @Column(name = "related_review_id")
    private Long relatedReviewId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum NotificationType {
        SYSTEM      // 系統通知（評論被刪除、賬戶異常等）
    }
}