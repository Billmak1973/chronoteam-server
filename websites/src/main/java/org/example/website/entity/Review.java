package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "review", indexes = {
        @Index(name = "idx_review_prod_id", columnList = "prod_id"),
        @Index(name = "idx_review_order_no", columnList = "order_no"),
        @Index(name = "idx_review_parent_id", columnList = "parent_id")
})
@Data
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    // 從 private Long id; 改成 private Long reviewId;
    private Long reviewId;

    @Column(name = "order_no", length = 50, nullable = true)
    private String orderNo;

    // 從 @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "cust_username", referencedColumnName = "cust_username", nullable = false)
    // private Customer customer;
    // 改成 @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    // private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", referencedColumnName = "prod_id", nullable = false)
    private Product product;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "content", length = 4000, nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "reply_to_user", length = 50)
    private String replyToUser;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;

    @Column(name = "dislike_count", nullable = false)
    private Integer dislikeCount = 0;

    @Column(name = "pinned", nullable = false)
    private Boolean pinned = false;

    // 存储富文本格式（HTML格式）
    @Column(name = "formatted_content", length = 4000, columnDefinition = "TEXT")
    private String formattedContent;

    // 标记是否为富文本
    @Column(name = "is_formatted", nullable = false)
    private Boolean isFormatted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}