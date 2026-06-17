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

    // 發送者 (可選，系統通知可能沒有發送者)
    @Column(name = "sender_username", length = 50)
    private String senderUsername;

    // 通知類型: REPLY (回復我), MENTION (@我)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    // 消息內容摘要
    @Column(name = "content", length = 255)
    private String content;

    // 點擊跳轉的目標 URL (例如: /product/1#review-123)
    @Column(name = "target_url", length = 255)
    private String targetUrl;

    // 是否已讀
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    // 關聯的評論 ID (方便跳轉到具體位置)
    @Column(name = "related_review_id")
    private Long relatedReviewId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum NotificationType {
        REPLY,      // 別人回復了我的評論
        MENTION     // 別人在評論中 @了我
    }
}