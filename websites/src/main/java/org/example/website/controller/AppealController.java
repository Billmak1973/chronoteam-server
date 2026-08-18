package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.website.dto.AppealRequest;
import org.example.website.dto.Result;
import org.example.website.service.AppealService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 申訴管理控制器
 * 處理用戶針對系統處罰（如禁言、拉黑、評論被刪）提交的申訴請求
 */
@RestController
@RequestMapping("/api/appeal")
@Tag(name = "申訴管理", description = "用戶對系統處罰或通知進行申訴的相關接口")
public class AppealController {

    private final AppealService appealService;

    public AppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    /**
     * 提交申訴
     *
     * @param request 申訴請求數據 (包含通知ID、申訴類型、申訴原因)
     * @param authentication 當前登入用戶的認證信息
     * @return 標準化的 API 響應結果
     */
    @Operation(
            summary = "提交申訴",
            description = "用戶針對系統通知（如禁言、拉黑、評論被刪）提交申訴理由。" +
                    "注意：每個通知在審核期間只能提交一次申訴，且永久拉黑最多只能申訴 3 次。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "申訴提交成功"),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤、已存在待處理申訴或已超過申訴次數限制"),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效")
    })
    @PostMapping("/submit")
    public ResponseEntity<Result> submitAppeal(
            @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "申訴請求詳細信息", required = true)
            @RequestBody AppealRequest request,
            Authentication authentication) {

        // 1. 權限校驗
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }

        // 2. 獲取當前用戶名
        String username = authentication.getName();

        // 3. 調用業務邏輯層處理申訴
        Map<String, Object> result = appealService.submitAppeal(username, request);

        // 4. 根據業務結果返回標準化響應
        Boolean isSuccess = (Boolean) result.get("success");
        String message = (String) result.get("message");

        if (Boolean.TRUE.equals(isSuccess)) {
            return ResponseEntity.ok(Result.ok(message));
        } else {
            return ResponseEntity.badRequest().body(Result.error(message != null ? message : "申訴提交失敗"));
        }
    }
}