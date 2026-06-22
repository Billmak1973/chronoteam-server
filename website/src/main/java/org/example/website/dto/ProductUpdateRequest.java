package org.example.website.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data // Lombok 自動生成 Getter/Setter
public class ProductUpdateRequest {
    private BigDecimal price;
    private Integer stock;
    private String category;
    private String brand;
    private String description;
    private String details;
    private String image;
}