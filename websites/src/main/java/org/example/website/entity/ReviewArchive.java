package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_archive", indexes = {
        @Index(name = "idx_archive_author", columnList = "author_user_id"),
        @Index(name = "idx_archive_product", columnList = "product_id"),
        @Index(name = "idx_archive_deleted_by", columnList = "deleted_by_id")
})
@Data
public class ReviewArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "archive_id")
    private Long archiveId;

    // 原評論的 ID (用於追溯)
    @Column(name = "original_review_id", nullable = false)
    private Long originalReviewId;

    // 關聯的商品 ID
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    // 關聯的訂單編號 (用於追溯是否為特定訂單的評價)
    @Column(name = "order_no", length = 50)
    private String orderNo;

    // 關聯的作者 (User 實體)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id", nullable = false)
    private User author;

    // 原文純文本內容 (快照，用於後台搜索或通知)
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    // 存儲富文本格式 (HTML格式) 的快照
    @Column(name = "formatted_content", length = 4000, columnDefinition = "TEXT")
    private String formattedContent;

    // 標記原評論是否為富文本
    @Column(name = "is_formatted", nullable = false)
    private Boolean isFormatted = false;

    // 原評分 (如果是根評論)
    @Column(name = "rating")
    private Double rating;

    // 樓中樓相關字段
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "reply_to_user", length = 50)
    private String replyToUser;

    // 原評論的創建時間
    @Column(name = "original_created_at", nullable = false)
    private LocalDateTime originalCreatedAt;

    // 核心審計字段：刪除時間
    @CreationTimestamp
    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;

    // 執行刪除操作的用戶 ID (若為系統自動刪除則為 null)
    @Column(name = "deleted_by_id")
    private Long deletedById;

    // 核心審計字段：刪除原因 (管理員填寫的理由)
    @Column(name = "delete_reason", columnDefinition = "TEXT")
    private String deleteReason;

    // 刪除時的點贊數 (可選，用於評估刪除影響)
    @Column(name = "like_count_at_delete")
    private Integer likeCountAtDelete;

    // 刪除時的踩數 (可選，用於評估刪除影響)
    @Column(name = "dislike_count_at_delete")
    private Integer dislikeCountAtDelete;
}