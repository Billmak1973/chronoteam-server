package org.example.website.dto;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class StoreTransferRequest {
    @NotNull(message = "庫存記錄ID不能為空")
    private Long inventoryId;

    @NotNull(message = "商品ID不能為空")
    private Integer productId;

    @NotNull(message = "源門店ID不能為空")
    private Long sourceStoreId;

    @NotBlank(message = "目標倉庫類型不能為空")
    private String targetType; // "ONLINE" 或 "OFFLINE"

    private Long targetStoreId; // 當 targetType 為 OFFLINE 時必填

    @NotNull(message = "調撥數量不能為空")
    @Min(value = 1, message = "調撥數量必須大於 0")
    private Integer quantity;

    @NotBlank(message = "調撥原因不能為空")
    private String reason;
}
