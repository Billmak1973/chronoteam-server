package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification", indexes = {
        // 從 @Index(name = "idx_notification_recipient", columnList = "recipient_username")
        // 改成 @Index(name = "idx_notification_recipient", columnList = "recipient_user_id")
        @Index(name = "idx_notification_recipient", columnList = "recipient_user_id"),
        @Index(name = "idx_notification_read", columnList = "is_read"),
        @Index(name = "idx_notification_broadcast", columnList = "is_broadcast, broadcast_target_type, broadcast_target_id")
})
@Data
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 從 private Long id; 改成 private Long notificationId; 並明確指定數據庫列名為 notification_id
    @Column(name = "notification_id")
    private Long notificationId;

    // 從 @Column(name = "recipient_username", nullable = false, length = 50) private String recipientUsername;
    // 改成 @ManyToOne 關聯 User 實體的主鍵 id，字段名改為 recipient
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id")
    private User recipient;

    // 從 @Column(name = "sender_username", length = 50) private String senderUsername;
    // 改成 @ManyToOne 關聯 User 實體的主鍵 id，字段名改為 sender
    // 注意：原本代碼中可能將 sender 設為 "system" 等字符串，改為實體關聯後需確保數據庫中有對應的 User 記錄，否則會報外鍵錯誤。若系統通知無需關聯具體用戶，可考慮改回 Long senderId 或保留 String。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private User sender;

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


    /** 是否為廣播通知 (默認 false) */
    @Column(name = "is_broadcast", nullable = false)
    private Boolean isBroadcast = false;

    /** 廣播目標類型: ALL (全員), PRODUCT_SUBSCRIBERS (特定商品訂閱者) */
    @Enumerated(EnumType.STRING)
    @Column(name = "broadcast_target_type", length = 30)
    private BroadcastTargetType broadcastTargetType;

    /** 廣播目標 ID (例如: 當 targetType 為 PRODUCT_SUBSCRIBERS 時，這裡存 productId) */
    @Column(name = "broadcast_target_id")
    private Integer broadcastTargetId;

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
        SYSTEM,     // 系統通知（評論被刪除、賬戶異常、封禁等）
        STOCK       // 到貨通知（關注的商品已補貨到貨）
    }

    public enum BroadcastTargetType {
        ALL,                // 全員廣播
        PRODUCT_SUBSCRIBERS // 特定商品的訂閱者
    }
}