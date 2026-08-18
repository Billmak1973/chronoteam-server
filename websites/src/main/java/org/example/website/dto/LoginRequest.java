package org.example.website.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "用戶登入請求參數")
public class LoginRequest {

    @Schema(description = "用戶登入名稱", example = "testuser", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用戶名不能為空")
    private String username;

    @Schema(description = "用戶登入密碼", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密碼不能為空")
    private String password;
}