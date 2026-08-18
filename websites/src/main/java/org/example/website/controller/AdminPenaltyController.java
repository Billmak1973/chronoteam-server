package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.AdminPenalty;
import org.example.website.entity.Appeal;
import org.example.website.entity.Notification;
import org.example.website.entity.User;
import org.example.website.repository.AdminPenaltyRepository;
import org.example.website.repository.AppealRepository;
import org.example.website.repository.NotificationRepository;
import org.example.website.repository.UserRepository;
import org.example.website.security.CustomUserDetails;
import org.example.website.service.AdminPenaltyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/penalty")
@Tag(name = "管理員處罰管理", description = "管理員對違規用戶進行拉黑、解除拉黑、撤銷處罰及發送通知的相關接口")
public class AdminPenaltyController {

    private final AdminPenaltyService adminPenaltyService;
    private final AdminPenaltyRepository adminPenaltyRepository;
    private final AppealRepository appealRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public AdminPenaltyController(AdminPenaltyService adminPenaltyService,
                                  AdminPenaltyRepository adminPenaltyRepository,
                                  AppealRepository appealRepository,
                                  NotificationRepository notificationRepository,
                                  UserRepository userRepository) {
        this.adminPenaltyService = adminPenaltyService;
        this.adminPenaltyRepository = adminPenaltyRepository;
        this.appealRepository = appealRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * 核心修復：權限校驗基於 user_type (Role == ADMIN)，而非用戶名是否等於 "admin"
     * CustomUserDetails 在登入時已從數據庫載入 Role 枚舉，直接判斷，零查庫開銷
     */
    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails
                && userDetails.getRole() == User.Role.ADMIN;
    }

    // ==================== 拉黑用戶 ====================
    @Operation(
            summary = "永久拉黑用戶",
            description = "管理員將指定用戶永久拉黑，並可選關聯違規評論快照以便後續審計。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "拉黑成功"),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤或用戶已被拉黑"),
            @ApiResponse(responseCode = "403", description = "無權操作（非管理員）")
    })
    @PostMapping("/blacklist/{targetUsername}")
    public ResponseEntity<Result> blacklistUser(
            @Parameter(description = "目標用戶名", example = "bad_user_123", required = true)
            @PathVariable String targetUsername,

            @Parameter(description = "拉黑原因", example = "嚴重違反社區規範")
            @RequestParam(required = false, defaultValue = "嚴重違反社區規範") String reason,

            @Parameter(description = "引發處罰的評論ID (可選)", example = "1001")
            @RequestParam(required = false) Long reviewId,

            @Parameter(description = "評論內容快照 (可選)", example = "這條評論包含違規內容")
            @RequestParam(required = false) String reviewContent,

            Authentication authentication) {

        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Result.error("無權操作"));
        }

        try {
            adminPenaltyService.blacklistUser(targetUsername, authentication.getName(), reason, reviewId, reviewContent);
            return ResponseEntity.ok(Result.ok("已成功永久拉黑該用戶"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    // ==================== 解除拉黑 ====================
    @Operation(
            summary = "解除用戶拉黑",
            description = "管理員解除指定用戶的永久拉黑狀態，恢復其互動權限。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解除拉黑成功"),
            @ApiResponse(responseCode = "400", description = "用戶不存在或未被拉黑"),
            @ApiResponse(responseCode = "403", description = "無權操作（非管理員）")
    })
    @DeleteMapping("/blacklist/{targetUsername}")
    public ResponseEntity<Result> unblacklistUser(
            @Parameter(description = "目標用戶名", example = "bad_user_123", required = true)
            @PathVariable String targetUsername,
            Authentication authentication) {

        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Result.error("無權操作"));
        }

        try {
            adminPenaltyService.unblacklistUser(targetUsername);
            return ResponseEntity.ok(Result.ok("已解除拉黑"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    // ==================== 獲取申訴詳情 ====================
    @Operation(
            summary = "獲取申訴詳情",
            description = "根據申訴 ID 獲取申訴的詳細信息，包含用戶信息和申訴內容。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功"),
            @ApiResponse(responseCode = "404", description = "申訴記錄不存在"),
            @ApiResponse(responseCode = "500", description = "服務器內部錯誤")
    })
    @GetMapping("/appeal/{appealId}")
    @Transactional(readOnly = true) // 關鍵：防止懶加載異常
    public ResponseEntity<Result> getAppealDetail(
            @Parameter(description = "申訴記錄的唯一 ID", example = "1001", required = true)
            @PathVariable Long appealId) {
        try {
            Appeal appeal = appealRepository.findById(appealId)
                    .orElseThrow(() -> new RuntimeException("申訴記錄不存在"));

            // 構建返回數據
            Map<String, Object> data = new HashMap<>();
            data.put("appealId", appeal.getAppealId());
            data.put("reason", appeal.getReason());
            data.put("appealType", appeal.getAppealType() != null ? appeal.getAppealType().name() : "UNKNOWN");
            data.put("status", appeal.getStatus() != null ? appeal.getStatus().name() : "UNKNOWN");
            data.put("createdAt", appeal.getCreatedAt());
            data.put("adminResponse", appeal.getAdminResponse());

            // 包含用戶信息 (強制在事務內初始化，避免 LazyInitializationException)
            if (appeal.getUser() != null) {
                Map<String, String> userInfo = new HashMap<>();
                userInfo.put("username", appeal.getUser().getUsername());
                userInfo.put("userId", appeal.getUser().getId().toString());
                data.put("user", userInfo);
            } else {
                data.put("user", null);
            }

            return ResponseEntity.ok(Result.okWithData("獲取成功", data));

        } catch (Exception e) {
            e.printStackTrace();
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知錯誤 (請查看後端控制台日誌)";
            return ResponseEntity.status(500).body(Result.error("獲取失敗: " + errorMsg));
        }
    }

    // ==================== 撤銷處罰 ====================
    @Operation(
            summary = "撤銷處罰",
            description = "管理員手動撤銷指定的處罰記錄（例如：提前解除封禁）。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "撤銷成功"),
            @ApiResponse(responseCode = "400", description = "處罰記錄不存在或已撤銷"),
            @ApiResponse(responseCode = "403", description = "無權操作（非管理員）")
    })
    @PostMapping("/{penaltyId}/revoke")
    public ResponseEntity<Result> revokePenalty(
            @Parameter(description = "處罰記錄的唯一 ID", example = "5001", required = true)
            @PathVariable Long penaltyId,
            Authentication authentication) {

        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Result.error("無權操作"));
        }

        try {
            adminPenaltyService.revokePenalty(penaltyId);
            return ResponseEntity.ok(Result.ok("已成功解除處罰"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    // ==================== 發送解除拉黑通知 ====================
    @Operation(
            summary = "發送解除拉黑通知",
            description = "管理員手動觸發，向被解除拉黑的用戶發送系統通知。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "通知發送成功"),
            @ApiResponse(responseCode = "400", description = "處罰記錄不存在"),
            @ApiResponse(responseCode = "403", description = "無權操作（非管理員）")
    })
    @PostMapping("/{penaltyId}/send-unblacklist-notification")
    public ResponseEntity<Result> sendUnblacklistNotification(
            @Parameter(description = "處罰記錄的唯一 ID", example = "5001", required = true)
            @PathVariable Long penaltyId,
            Authentication authentication) {

        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Result.error("無權操作"));
        }

        try {
            // 1. 獲取處罰記錄
            AdminPenalty penalty = adminPenaltyRepository.findById(penaltyId)
                    .orElseThrow(() -> new RuntimeException("處罰記錄不存在"));

            // 2. 獲取被解除拉黑的用戶
            User targetUser = penalty.getTargetUser();

            // 3. 獲取當前操作的管理員實體作為 sender
            User adminSender = userRepository.findByUsername(authentication.getName()).orElse(null);

            // 4. 創建系統通知
            Notification notification = new Notification();
            notification.setRecipient(targetUser);
            notification.setSender(adminSender);
            notification.setType(Notification.NotificationType.SYSTEM);
            notification.setTitle("🎉 您的帳戶已解除永久拉黑");
            notification.setContent(
                    "您好，\n\n" +
                            "您的帳戶已被管理員解除永久拉黑處罰。\n" +
                            "您現在可以恢復所有互動功能，包括：\n" +
                            "• 發表評論和回復\n" +
                            "• 點贊/踩\n" +
                            "• 參與社區互動\n\n" +
                            "請遵守社區規範，共同維護良好的交流環境。\n\n" +
                            "ChronoTeam 管理團隊"
            );
            notification.setRead(false);

            // 5. 保存通知
            notificationRepository.save(notification);

            return ResponseEntity.ok(Result.ok("通知已發送"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("發送通知失敗: " + e.getMessage()));
        }
    }
}