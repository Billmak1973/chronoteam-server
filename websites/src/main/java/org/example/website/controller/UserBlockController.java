package org.example.website.controller;

import lombok.RequiredArgsConstructor;
import org.example.website.dto.ApiResponse;
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
public class UserBlockController {

    private final UserBlockService userBlockService;
    private final UserBlockRepository userBlockRepository;
    private final AdminPenaltyService adminPenaltyService;

    /**
     *  禁言/解除禁言按钮（一个接口，两种用法）
     */
    @PostMapping("/{targetUsername}/toggle-block")
    public ResponseEntity<ApiResponse> toggleBlock(
            @PathVariable String targetUsername,
            @RequestParam(required = false) Integer durationMinutes,  // 管理员专用：禁言时长
            @RequestParam(required = false) String reason,             // 管理员专用：禁言原因
            @RequestParam(required = false) Long reviewId,             // 【新增】管理員專用：關聯的評論 ID
            @RequestParam(required = false) String reviewContent,      // 【新增】管理員專用：關聯的評論內容快照
            Authentication authentication) {

        String currentUsername = authentication.getName();
        boolean isAdmin = "admin".equals(currentUsername);

        Map<String, Object> result;

        if (isAdmin && durationMinutes != null) {
            // 管理员禁言（全局禁言）
            result = adminPenaltyService.adminBanUser(
                    targetUsername, currentUsername, durationMinutes, reason,reviewId,reviewContent
            );
        } else {
            // 普通用户禁言（双向禁言）
            result = userBlockService.blockUser(currentUsername, targetUsername);
        }

        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(ApiResponse.ok((String) result.get("message")));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error((String) result.get("message")));
        }
    }

    /**
     *  解除禁言
     */
    @DeleteMapping("/{targetUsername}/unblock")
    public ResponseEntity<ApiResponse> unblockUser(
            @PathVariable String targetUsername,
            Authentication authentication) {

        String currentUsername = authentication.getName();
        Map<String, Object> result = userBlockService.unblockUser(
                currentUsername, targetUsername
        );

        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(ApiResponse.ok((String) result.get("message")));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error((String) result.get("message")));
        }
    }

    /**
     *  检查能否回复某用户（前端调用）（先檢查後端，防止回復框出現后才告訴禁言）
     */
    @GetMapping("/can-reply/{targetUsername}")
    public ResponseEntity<Map<String, Object>> canReply(
            @PathVariable String targetUsername,
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

    // 获取我禁言的用户列表
    @GetMapping("/blocked-list")
    public ResponseEntity<List<String>> getBlockedList(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.ok(new ArrayList<>());
        }
        String username = authentication.getName();
        List<String> blockedList = userBlockRepository.findBlockedUsernamesByBlockerUsername(username);
        return ResponseEntity.ok(blockedList);
    }

    /**
     * 檢查能否回復某用戶（前端調用）
     */
    @GetMapping("/check-reply-permission/{targetUsername}")
    public ResponseEntity<Map<String, Object>> checkReplyPermission(
            @PathVariable String targetUsername,
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