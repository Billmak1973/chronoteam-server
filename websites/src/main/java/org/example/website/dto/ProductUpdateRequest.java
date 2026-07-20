package org.example.website.dto;

import lombok.Data;
import org.example.website.entity.WatchCondition;

import java.math.BigDecimal;

@Data
public class ProductUpdateRequest {
    private BigDecimal price;
    private Integer stock;
    private String category;
    private String brand;
    private String description;
    private String details;
    private String image;
    private String groupCode;

    // 確保這兩個欄位存在，且類型為 Boolean
    private Boolean visible;
    private Boolean conditionVisible;
    private WatchCondition condition;
    private Integer homeDisplayOrder;
}