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
        @Index(name = "idx_review_parent_id", columnList = "parent_id") // 新增索引
})
// ⚠️ 注意：移除了原有的 uniqueConstraints，因为楼中楼回复没有 order_no，会导致冲突。
// 唯一性校验将在 Service/Controller 层通过代码实现。
@Data
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @Column(name = "order_no", length = 50, nullable = true) // 改为允许为空
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cust_username", referencedColumnName = "cust_username", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", referencedColumnName = "prod_id", nullable = false)
    private Product product;

    @Column(name = "rating")
    private Double rating; // 楼中楼回复此字段为 null

    @Column(name = "content", length = 1000, nullable = false)
    private String content;

    //  核心字段：区分主评论和楼中楼
    @Column(name = "parent_id")
    private Long parentId; // NULL = 根评论，有值 = 楼中楼回复

    @Column(name = "reply_to_user", length = 50)
    private String replyToUser; // 记录回复了谁（用于前端显示 "回复 @xxx"）

    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;

    @Column(name = "dislike_count", nullable = false)
    private Integer dislikeCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}