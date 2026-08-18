package org.example.website.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "修改密碼請求參數")
public class ChangePasswordRequest {

    @Schema(description = "驗證方式", allowableValues = {"password", "question"}, example = "password", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "驗證方式不能為空")
    private String verificationMethod;

    @Schema(description = "新密碼 (至少6位)", example = "NewPassword123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密碼不能為空")
    private String newPassword;

    @Schema(description = "當前密碼 (當 verificationMethod 為 'password' 時必填)", example = "OldPassword123")
    private String currentPassword;

    @Schema(description = "安全問題答案列表 (當 verificationMethod 為 'question' 時必填)")
    @Valid
    private List<QuestionAnswerDTO> questionAnswers;

    /**
     * 內部類：用於描述安全問題的答案結構
     */
    @Data
    @Schema(description = "安全問題答案對象")
    public static class QuestionAnswerDTO {

        @Schema(description = "安全問題的唯一 ID", example = "1")
        @NotNull(message = "問題ID不能為空")
        private Long questionId;

        @Schema(description = "用戶填寫的答案 (不區分大小寫)", example = "台北")
        @NotBlank(message = "答案不能為空")
        private String answer;
    }
}