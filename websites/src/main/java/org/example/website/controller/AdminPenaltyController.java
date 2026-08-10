package org.example.website.controller;

import org.example.website.dto.ApiResponse;
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
    @PostMapping("/blacklist/{targetUsername}")
    public ResponseEntity<ApiResponse> blacklistUser(
            @PathVariable String targetUsername,
            @RequestParam(required = false, defaultValue = "嚴重違反社區規範") String reason,
            @RequestParam(required = false) Long reviewId,
            @RequestParam(required = false) String reviewContent,
            Authentication authentication) {

        // 【修改】使用角色校驗替換用戶名校驗
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作"));
        }

        try {
            adminPenaltyService.blacklistUser(targetUsername, authentication.getName(), reason, reviewId, reviewContent);
            return ResponseEntity.ok(ApiResponse.ok("已成功永久拉黑該用戶"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== 解除拉黑 ====================
    @DeleteMapping("/blacklist/{targetUsername}")
    public ResponseEntity<ApiResponse> unblacklistUser(
            @PathVariable String targetUsername,
            Authentication authentication) {

        // 【修改】使用角色校驗替換用戶名校驗
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作"));
        }

        try {
            adminPenaltyService.unblacklistUser(targetUsername);
            return ResponseEntity.ok(ApiResponse.ok("已解除拉黑"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== 獲取申訴詳情 ====================
    @GetMapping("/appeal/{appealId}")
    @ResponseBody
    @Transactional(readOnly = true) // 關鍵：防止懶加載異常
    public ResponseEntity<ApiResponse> getAppealDetail(@PathVariable Long appealId) {
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

            return ResponseEntity.ok(ApiResponse.okWithData("獲取成功", data));

        } catch (Exception e) {
            e.printStackTrace();
            String errorMsg = e.getMessage() != null ? e.getMessage() : "未知錯誤 (請查看後端控制台日誌)";
            return ResponseEntity.status(500).body(ApiResponse.error("獲取失敗: " + errorMsg));
        }
    }

    // ==================== 撤銷處罰 ====================
    @PostMapping("/{penaltyId}/revoke")
    public ResponseEntity<ApiResponse> revokePenalty(
            @PathVariable Long penaltyId,
            Authentication authentication) {

        // 【修改】使用角色校驗替換用戶名校驗
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作"));
        }

        try {
            adminPenaltyService.revokePenalty(penaltyId);
            return ResponseEntity.ok(ApiResponse.ok("已成功解除處罰"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== 發送解除拉黑通知 ====================
    @PostMapping("/{penaltyId}/send-unblacklist-notification")
    public ResponseEntity<ApiResponse> sendUnblacklistNotification(
            @PathVariable Long penaltyId,
            Authentication authentication) {

        // 【修改】使用角色校驗替換用戶名校驗
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作"));
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

            return ResponseEntity.ok(ApiResponse.ok("通知已發送"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("發送通知失敗: " + e.getMessage()));
        }
    }
}