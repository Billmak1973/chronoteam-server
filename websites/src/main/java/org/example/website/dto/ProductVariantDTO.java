package org.example.website.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data // Lombok 自動生成 Getter/Setter
public class ProductVariantDTO {

    private Integer productId;

    private BigDecimal price;

    private Integer stock;

    // 存儲成色的字串形式，例如 "NEAR_MINT", "EXCELLENT"
    private String condition;

    private String description;  // 产品描述
    private String details;      // 详细介绍
    private Double rating;       // 评分
    private Integer totalReviewCount; // 评论总数
    private BigDecimal totalScore;    // 总评分
}