package org.example.website.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "用戶提交申訴的請求數據")
public class AppealRequest {

    @Schema(description = "關聯的系統通知 ID", example = "1001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "通知 ID 不能為空")
    private Long notificationId;

    @Schema(description = "申訴類型 (BAN: 禁言, BLACKLIST: 永久拉黑, DELETE_REVIEW: 評論被刪)",
            example = "BAN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "申訴類型不能為空")
    private String appealType;

    @Schema(description = "用戶填寫的申訴理由", example = "我並沒有違規，請管理員重新審核", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "申訴原因不能為空")
    private String reason;
}