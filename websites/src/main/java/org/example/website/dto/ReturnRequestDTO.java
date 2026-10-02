package org.example.website.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ReturnRequestDTO {
    private String orderNo;
    private Integer productId;
    private Integer quantity;       // 對應前端的 quantity
    private Long storeId;           // 前端傳來的 storeId (選填或必填視業務而定)
    private LocalDate returnDate;   // 對應前端的 returnDate
    private String returnTimeSlot;  // 對應前端的 returnTimeSlot
    private String reason;          // 對應前端的 reason
    private String requestType = "RETURN";
}