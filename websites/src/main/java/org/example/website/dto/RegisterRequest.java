package org.example.website.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "用戶註冊請求參數")
public class RegisterRequest {
    @Schema(description = "用戶名 (3-50位字母數字組合)", example = "newuser123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用戶名不能為空")
    @Size(min = 3, max = 50, message = "用戶名長度3-50位")
    private String username;

    @Schema(description = "真實姓名", example = "張三", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "姓名不能為空")
    @Size(max = 100, message = "姓名不能超過100字符")
    private String name;

    @Schema(description = "電子郵件地址", example = "test@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "郵箱不能為空")
    @Email(message = "郵箱格式不正確")
    private String email;

    @Schema(description = "登入密碼 (至少6位)", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密碼不能為空")
    @Size(min = 6, message = "密碼至少6位")
    private String password;

    @Schema(description = "手機號碼", example = "+852 12345678", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "手機號不能為空")
    private String phone;

    // ================= 新增：可選的地址字段 =================
    @Schema(description = "區域 (可選)", example = "九龍")
    private String region;

    @Schema(description = "行政區 (可選)", example = "油尖旺區")
    private String district;

    @Schema(description = "詳細地址 (可選)", example = "彌敦道 100 號")
    private String detailAddress;

    @Schema(description = "完整地址 (可選，前端已組裝好)", example = "九龍 油尖旺區 彌敦道 100 號")
    private String fullAddress;
}