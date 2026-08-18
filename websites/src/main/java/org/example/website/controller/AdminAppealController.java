package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.website.dto.AppealReviewRequest;
import org.example.website.dto.Result;
import org.example.website.security.CustomUserDetails;
import org.example.website.service.AppealService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/appeal")
@Tag(name = "管理員申訴管理", description = "管理員處理用戶針對處罰(禁言/拉黑/刪除評論)所提交申訴的相關接口")
public class AdminAppealController {

    private final AppealService appealService;

    public AdminAppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    /**
     * 處理申訴 (同意/拒絕)
     */
    @Operation(
            summary = "審核並處理用戶申訴",
            description = "管理員查看用戶的申訴理由後，選擇同意(APPROVED)或拒絕(REJECTED)，並填寫回覆意見。" +
                    "同意後將自動解除對應的處罰狀態，並發送系統通知給用戶。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "申訴處理成功",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "請求參數錯誤 (例如：回覆為空、決策類型無效、或申訴狀態已變更)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "未登入或認證失效"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "權限不足 (僅限 ROLE_ADMIN 角色可訪問)"
            )
    })
    @PostMapping("/{appealId}/review")
    public ResponseEntity<Result> reviewAppeal(
            @Parameter(description = "待處理的申訴記錄唯一ID", example = "1001", required = true)
            @PathVariable Long appealId,

            @Parameter(description = "審核結果與回覆內容", required = true)
            @Valid @RequestBody AppealReviewRequest request,

            @Parameter(hidden = true) // 隱藏 Authentication，因為它由 Spring Security 自動注入，不需要前端傳遞
            Authentication authentication) {

        // 1. 獲取當前管理員 ID (依賴 CustomUserDetails)
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long adminId = userDetails.getId();

        try {
            // 2. 調用 Service 處理業務
            appealService.processAppeal(appealId, request.getAdminResponse(), request.getDecision(), adminId);

            // 3. 返回標準化成功響應
            return ResponseEntity.ok(Result.ok("申訴處理成功"));

        } catch (IllegalArgumentException e) {
            // 捕獲業務邏輯中的參數或狀態異常 (例如：該申訴已被處理過)
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            // 捕獲其他未知異常
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤，處理失敗: " + e.getMessage()));
        }
    }
}