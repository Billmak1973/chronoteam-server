package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_archive", indexes = {
        @Index(name = "idx_archive_author", columnList = "author_username"),
        @Index(name = "idx_archive_product", columnList = "product_id"),
        @Index(name = "idx_archive_deleted_by", columnList = "deleted_by")
})
@Data
public class ReviewArchive {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 原評論的 ID (用於追溯)
    @Column(name = "original_review_id", nullable = false)
    private Long originalReviewId;

    // 關聯的商品 ID
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    // 原作者用戶名
    @Column(name = "author_username", nullable = false, length = 50)
    private String authorUsername;

    // 原文內容 (快照)
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

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

    //  核心審計字段：刪除時間
    @CreationTimestamp
    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;

    //  核心審計字段：誰刪的？ (管理員賬號 / "SYSTEM" / "SELF")
    @Column(name = "deleted_by", nullable = false, length = 50)
    private String deletedBy;

    //  核心審計字段：刪除原因 (管理員填寫的理由)
    @Column(name = "delete_reason", columnDefinition = "TEXT")
    private String deleteReason;

    // 刪除時的點贊/踩數 (可選，用於評估刪除影響)
    @Column(name = "like_count_at_delete")
    private Integer likeCountAtDelete;

    @Column(name = "dislike_count_at_delete")
    private Integer dislikeCountAtDelete;
}