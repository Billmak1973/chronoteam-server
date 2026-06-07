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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}