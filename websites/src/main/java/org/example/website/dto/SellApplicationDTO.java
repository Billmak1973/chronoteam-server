package org.example.website.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Data
public class SellApplicationDTO {

    @NotBlank(message = "品牌不能為空")
    private String brand;

    @NotBlank(message = "系列不能為空")
    private String series;

    private String model;

    private Integer purchaseYear;

    private Integer quantity;

    private List<String> accessories;

    private String condition;

    private String functionStatus;

    private String notes;

    // 圖片文件（前端上傳）
    private MultipartFile imgDial;        // 錶面
    private MultipartFile imgCaseback;    // 錶背/機芯
    private MultipartFile imgCard;        // 保卡/發票
    private MultipartFile imgFlaws;       // 瑕疵特寫
    private MultipartFile imgExtra;       // 其他照片

    @NotNull(message = "請選擇交易模式")
    private String transactionMode;  // "BUYOUT" 或 "CONSIGNMENT"

    // ==========================================
    // 線下預約交接資訊欄位
    // ==========================================
    private String storeId;
    private LocalDate appointmentDate;
    private String appointmentTimeSlot;
}