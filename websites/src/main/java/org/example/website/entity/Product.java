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
    private Integer productId;

    @Column(name = "prod_desc", length = 200, nullable = false)
    private String description;

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
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "watch_condition", length = 20)
    private WatchCondition condition;

    /** 同款分組碼 */
    @Column(name = "group_code", length = 50)
    private String groupCode;

    @Column(name = "is_visible", nullable = false)
    private Boolean visible = true;

    // 控制成色选项是否可见
    @Column(name = "condition_visible", nullable = false)
    private Boolean conditionVisible = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "total_review_count")
    private Integer totalReviewCount;

    @Column(name = "total_score")
    private BigDecimal totalScore;
}