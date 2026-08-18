package org.example.website.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "管理員審核申訴的請求參數")
public class AppealReviewRequest {

    @Schema(description = "管理員的回覆/備註意見", example = "經核查，您的行為確實違反了社區規範第3條，維持原判。", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "管理員回覆不能為空")
    private String adminResponse;

    @Schema(description = "審核決策", allowableValues = {"APPROVED", "REJECTED"}, example = "APPROVED", requiredMode = Schema.RequiredMode.REQUIRED)
    @Pattern(regexp = "^(APPROVED|REJECTED)$", message = "決策類型必須是 APPROVED 或 REJECTED")
    private String decision;
}