package org.example.website.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Data
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prod_id")
    private Integer id;

    @Column(name = "prod_desc", length = 200, nullable = false)
    private String description;

    //  新增：商品詳細介紹字段 (使用 TEXT 類型以支持較長文本)
    @Column(name = "prod_details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "prod_category", length = 50)
    private String category = "Watch";

    @Column(name = "prod_price", nullable = false)
    private BigDecimal price;

    @Column(name = "prod_image", length = 255)
    private String image = "watch1.png";

    @Column(name = "stock_quantity")
    private Integer stock = 100;

    @Column(name = "prod_brand", length = 50)
    private String brand;  // 新增：品牌字段

    //  核心新增：是否在瀏覽商品頁面 (browse.html) 中顯示
    // true: 顯示 (上架), false: 隱藏 (下架)
    @Column(name = "is_visible", nullable = false)
    private Boolean visible = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 總評論數
     */
    @Column(name = "total_review_count")
    private Integer totalReviewCount;

    /**
     * 總分數
     * (若您的評分系統皆為整數累加，也可將類型改為 Integer)
     */
    @Column(name = "total_score")
    private BigDecimal totalScore;
}