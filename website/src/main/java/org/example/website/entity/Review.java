package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "review", indexes = {
        // 加速按商品查詢評論
        @Index(name = "idx_review_prod_id", columnList = "prod_id")
}, uniqueConstraints = {
        // 關鍵：確保同一用戶對同一商品只能發表一次評論
        @UniqueConstraint(columnNames = {"cust_username", "prod_id"})
})
@Data
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    // 關聯用戶 (已登入的用戶名)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cust_username", referencedColumnName = "cust_username", nullable = false)
    private Customer customer;

    // 關聯手錶商品
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", referencedColumnName = "prod_id", nullable = false)
    private Product product;

    // 評分 (可選，例如 1-5 星)
    @Column(name = "rating")
    private Integer rating;

    // 評論內容
    @Column(name = "content", length = 1000, nullable = false)
    private String content;

    // 評論時間
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}