package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.website.dto.Result;
import org.example.website.entity.AdminPenalty;
import org.example.website.repository.UserBlockRepository;
import org.example.website.service.AdminPenaltyService;
import org.example.website.service.UserBlockService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用戶互動與禁言管理", description = "用戶間的禁言/解除禁言操作、權限檢查及黑名單管理相關接口")
public class UserBlockController {

    private final UserBlockService userBlockService;
    private final UserBlockRepository userBlockRepository;
    private final AdminPenaltyService adminPenaltyService;

    /**
     * 禁言/解除禁言按鈕（一個接口，兩種用法）
     * 【核心修復】：嚴格區分「管理員系統封禁」與「普通用戶個人禁言」
     */
    @Operation(
            summary = "禁言或封禁用戶",
            description = "普通用戶調用則為雙向禁言（雙方無法互相回覆）；管理員調用則為全局系統封禁（必須提供封禁時長和原因）。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "操作成功", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "參數錯誤（如管理員未提供時長）或已存在封禁記錄"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PostMapping("/{targetUsername}/toggle-block")
    public ResponseEntity<Result> toggleBlock(
            @Parameter(description = "目標用戶名", example = "bad_user", required = true)
            @PathVariable String targetUsername,

            @Parameter(description = "封禁時長（分鐘），僅管理員必填", example = "1440")
            @RequestParam(required = false) Integer durationMinutes,

            @Parameter(description = "封禁原因，僅管理員必填", example = "嚴重違反社區規範")
            @RequestParam(required = false) String reason,

            @Parameter(description = "引發封禁的評論ID，僅管理員選填", example = "1001")
            @RequestParam(required = false) Long reviewId,

            @Parameter(description = "評論內容快照，僅管理員選填", example = "違規言論內容...")
            @RequestParam(required = false) String reviewContent,

            @Parameter(hidden = true)
            Authentication authentication) {

        String currentUsername = authentication.getName();

        // 【核心修復點】：不再依賴 durationMinutes 是否為 null 來判斷
        // 而是直接檢查當前操作者的角色是否為 ADMIN
        boolean isAdmin = "admin".equalsIgnoreCase(currentUsername) ||
                (authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

        Map<String, Object> result;

        if (isAdmin) {
            // ================= 管理員路徑 (絕對不涉及 user_block) =================
            if (durationMinutes == null || durationMinutes <= 0) {
                return ResponseEntity.badRequest()
                        .body(Result.error("管理員封禁必須指定有效的封禁時長"));
            }

            // 調用 AdminPenaltyService，只寫入 admin_penalty 表
            result = adminPenaltyService.adminBanUser(
                    targetUsername,
                    currentUsername,
                    durationMinutes,
                    reason,
                    reviewId,
                    reviewContent
            );

        } else {
            // ================= 普通用戶路徑 (只涉及 user_block) =================
            // 普通用戶不允許使用 reviewId/reviewContent/durationMinutes 等管理員參數
            result = userBlockService.blockUser(currentUsername, targetUsername);
        }

        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(Result.ok((String) result.get("message")));
        } else {
            return ResponseEntity.badRequest()
                    .body(Result.error((String) result.get("message")));
        }
    }

    /**
     * 解除禁言
     */
    @Operation(
            summary = "解除對某用戶的禁言",
            description = "普通用戶解除對目標用戶的雙向禁言狀態，恢復互相回復的權限。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解除成功", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "操作失敗"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @DeleteMapping("/{targetUsername}/unblock")
    public ResponseEntity<Result> unblockUser(
            @Parameter(description = "目標用戶名", example = "bad_user", required = true)
            @PathVariable String targetUsername,

            @Parameter(hidden = true)
            Authentication authentication) {

        String currentUsername = authentication.getName();
        Map<String, Object> result = userBlockService.unblockUser(
                currentUsername, targetUsername
        );

        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(Result.ok((String) result.get("message")));
        } else {
            return ResponseEntity.badRequest()
                    .body(Result.error((String) result.get("message")));
        }
    }

    /**
     * 檢查能否回復某用戶（前端調用 - 基礎版）
     */
    @Operation(
            summary = "檢查能否回復某用戶 (基礎版)",
            description = "檢查當前用戶是否被目標用戶禁言，或是否被管理員全局禁言。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "檢查成功，返回權限狀態"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @GetMapping("/can-reply/{targetUsername}")
    public ResponseEntity<Map<String, Object>> canReply(
            @Parameter(description = "目標用戶名", example = "bad_user", required = true)
            @PathVariable String targetUsername,

            @Parameter(hidden = true)
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();
        String currentUsername = authentication.getName();

        boolean canInteract = userBlockService.canInteract(currentUsername, targetUsername);
        boolean isGloballyBanned = adminPenaltyService.isGloballyBanned(currentUsername);

        response.put("success", true);
        response.put("canReply", canInteract && !isGloballyBanned);
        response.put("isGloballyBanned", isGloballyBanned);

        return ResponseEntity.ok(response);
    }

    /**
     * 獲取我禁言的用戶列表
     */
    @Operation(
            summary = "獲取我禁言的用戶列表",
            description = "返回當前登入用戶已禁言的所有用戶名列表，用於前端個人中心展示。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功，返回用戶名列表"),
            @ApiResponse(responseCode = "401", description = "未登入 (返回空列表)")
    })
    @GetMapping("/blocked-list")
    public ResponseEntity<List<String>> getBlockedList(
            @Parameter(hidden = true)
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(new ArrayList<>());
        }
        String username = authentication.getName();
        List<String> blockedList = userBlockRepository.findBlockedUsernamesByBlockerUsername(username);
        return ResponseEntity.ok(blockedList);
    }

    /**
     * 檢查能否回復某用戶（前端調用 - 詳細版）
     */
    @Operation(
            summary = "檢查回復權限 (詳細版)",
            description = "全面檢查當前用戶對目標用戶的回復權限，包括雙向禁言、管理員全局禁言及永久拉黑狀態。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "檢查成功，返回詳細權限狀態及封禁結束時間"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @GetMapping("/check-reply-permission/{targetUsername}")
    public ResponseEntity<Map<String, Object>> checkReplyPermission(
            @Parameter(description = "目標用戶名", example = "bad_user", required = true)
            @PathVariable String targetUsername,

            @Parameter(hidden = true)
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();
        String currentUsername = authentication.getName();

        // 1. 檢查普通用戶雙向禁言 (A->B 或 B->A)
        boolean isMutuallyBlocked = userBlockService.isBlocked(currentUsername, targetUsername);

        // 2. 檢查管理員全局禁言 (有期限)
        boolean isGloballyBanned = adminPenaltyService.isGloballyBanned(currentUsername);
        AdminPenalty activeBan = null;
        if (isGloballyBanned) {
            activeBan = adminPenaltyService.getActiveGlobalBan(currentUsername).orElse(null);
        }

        // 3. 檢查管理員永久拉黑
        boolean isBlacklisted = adminPenaltyService.isBlacklisted(currentUsername);

        response.put("success", true);
        // 只要滿足任一條件，就不能回復
        response.put("hasPermission", !isMutuallyBlocked && !isGloballyBanned && !isBlacklisted);
        response.put("isMutuallyBlocked", isMutuallyBlocked);
        response.put("isGloballyBanned", isGloballyBanned);
        response.put("isBlacklisted", isBlacklisted);

        // 傳遞禁言結束時間給前端格式化
        if (activeBan != null && activeBan.getEndTime() != null) {
            response.put("banEndTime", activeBan.getEndTime().toString());
        }

        return ResponseEntity.ok(response);
    }
}