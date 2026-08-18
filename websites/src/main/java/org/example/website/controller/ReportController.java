package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.Notification;
import org.example.website.entity.Report;
import org.example.website.entity.User;
import org.example.website.repository.NotificationRepository;
import org.example.website.repository.ReportRepository;
import org.example.website.repository.UserRepository;
import org.example.website.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
@Tag(name = "舉報管理", description = "用戶舉報提交與管理員審核決策相關接口")
public class ReportController {

    private final ReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public ReportController(ReportRepository reportRepository,
                            NotificationRepository notificationRepository,
                            UserRepository userRepository) {
        this.reportRepository = reportRepository;
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

    @Operation(
            summary = "提交舉報",
            description = "用戶提交對其他用戶或評論的舉報，系統將自動發送受理通知並記錄舉報內容快照。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "舉報提交成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤、信息不完整或重複舉報"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PostMapping("/submit")
    public ResponseEntity<Result> submitReport(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "舉報請求參數，需包含: reportedUsername, targetType, category, reason, reportContent(可選), reviewId(可選)",
                    required = true
            )
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }
        String reporterUsername = authentication.getName();

        // 獲取被舉報用戶實體
        User reportedUser = userRepository.findByUsername((String) request.get("reportedUsername"))
                .orElseThrow(() -> new RuntimeException("被舉報用戶不存在"));

        Long reviewId = request.get("reviewId") != null ? Long.valueOf(request.get("reviewId").toString()) : null;
        String targetType = (String) request.get("targetType");
        String category = (String) request.get("category");
        String reason = (String) request.get("reason");

        // 【新增】獲取前端傳來的舉報內容快照
        String reportContent = request.get("reportContent") != null ? (String) request.get("reportContent") : null;

        if (category == null || reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Result.error("請填寫完整的舉報信息"));
        }

        if ("REVIEW".equals(targetType) && reviewId != null) {
            if (reportRepository.existsByReporter_UsernameAndReviewId(reporterUsername, reviewId)) {
                return ResponseEntity.badRequest().body(Result.error("您已經舉報過這條評論了，請勿重複提交！"));
            }
        }

        // 獲取舉報人實體
        User reporterUser = userRepository.findByUsername(reporterUsername)
                .orElseThrow(() -> new RuntimeException("舉報用戶不存在"));

        // ==========================================
        // 1. 構建並初步保存舉報記錄 (此時 notificationId 為 null)
        // ==========================================
        Report report = new Report();
        report.setReporter(reporterUser);
        report.setReportedUser(reportedUser);
        report.setReviewId(reviewId);
        report.setTargetType(targetType != null ? targetType : "REVIEW");
        report.setCategory(category);
        report.setReason(reason.trim());
        report.setReportContent(reportContent); // 【新增】設置舉報內容快照
        report.setStatus(Report.ReportStatus.PENDING);

        // 先保存，讓數據庫生成 reportId
        reportRepository.save(report);

        // ==========================================
        // 2. 給舉報人發送一條「受理通知」
        // ==========================================
        try {
            Notification notif = new Notification();
            notif.setRecipient(reporterUser);
            User systemUser = userRepository.findByUsername("system").orElse(null);
            notif.setSender(systemUser);
            notif.setType(Notification.NotificationType.SYSTEM);
            notif.setTitle("舉報已受理");
            notif.setContent(String.format(
                    "您提交的關於用戶 [%s] 的%s舉報已成功受理。管理員將在 24 小時內進行審核，感謝您維護社區環境！",
                    reportedUser.getUsername(),
                    "REVIEW".equals(targetType) ? "評論" : "用戶"
            ));
            notif.setRead(false);

            // 保存通知，此時數據庫會自動生成 notificationId
            Notification savedNotif = notificationRepository.save(notif);

            // ==========================================
            // 3. 【核心修復】：將生成的 notificationId 回填到 Report 記錄中並更新
            // ==========================================
            report.setNotificationId(savedNotif.getNotificationId());
            reportRepository.save(report); // 執行 UPDATE 操作，將 notificationId 寫入數據庫

        } catch (Exception e) {
            System.err.println("發送舉報受理通知失敗: " + e.getMessage());
            // 即使通知發送失敗，舉報記錄依然有效，不影響主流程
        }

        // 4. 在返回前獲取最新的未讀通知數量
        long unreadCount = notificationRepository.countByRecipient_UsernameAndIsReadFalse(reporterUsername);

        // 5. 構建返回數據
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("message", "舉報已提交，感謝您的反饋，管理員將盡快審核！");
        responseData.put("unreadCount", unreadCount);

        return ResponseEntity.ok(Result.okWithData("舉報已提交，感謝您的反饋，管理員將盡快審核！", responseData));
    }

    @Operation(
            summary = "管理員處理舉報決策",
            description = "管理員審核舉報記錄，決定舉報是否成立 (successful: true/false)，並發送相應通知給舉報人。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "決策處理成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤或舉報已被處理"),
            @ApiResponse(responseCode = "401", description = "未登入"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員"),
            @ApiResponse(responseCode = "500", description = "服務器內部錯誤")
    })
    @PostMapping("/{reportId}/decision")
    @Transactional
    public ResponseEntity<Result> handleReportDecision(
            @Parameter(description = "舉報記錄的唯一 ID", example = "1")
            @PathVariable Long reportId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "決策參數，需包含: {\"successful\": true 或 false}",
                    required = true
            )
            @RequestBody Map<String, Boolean> request,
            Authentication authentication) {

        try {
            // 1. 驗證管理員權限 (嚴格檢查 Role 枚舉)
            if (!isAdmin(authentication)) {
                return ResponseEntity.status(403).body(Result.error("無權操作，僅限管理員"));
            }

            // 2. 獲取舉報記錄
            Report report = reportRepository.findById(reportId)
                    .orElseThrow(() -> new RuntimeException("舉報記錄不存在"));

            // 3. 檢查狀態
            if (report.getStatus() != Report.ReportStatus.PENDING) {
                return ResponseEntity.badRequest().body(Result.error("該舉報已被處理，無法再次操作"));
            }

            Boolean isSuccessful = request.get("successful");
            if (isSuccessful == null) {
                return ResponseEntity.badRequest().body(Result.error("缺少決策參數"));
            }

            // 4. 獲取舉報人
            User reporter = report.getReporter();
            if (reporter == null) {
                return ResponseEntity.badRequest().body(Result.error("舉報人信息不存在"));
            }
            String reporterUsername = reporter.getUsername();

            // 5. 根據決策處理
            if (isSuccessful) {
                // === 舉報成立 (是) ===
                report.setStatus(Report.ReportStatus.RESOLVED);
                reportRepository.save(report);

                // 發送成功通知
                sendSuccessNotification(reporterUsername, report);

                return ResponseEntity.ok(Result.ok("舉報處理成功！已對被舉報用戶進行處罰，並通知舉報人。"));
            } else {
                // === 舉報不成立 (否) ===
                report.setStatus(Report.ReportStatus.DISMISSED);
                reportRepository.save(report);

                // 發送溫和的駁回通知
                sendDismissNotification(reporterUsername, report);

                return ResponseEntity.ok(Result.ok("舉報已處理。該用戶已被標記關注，感謝你的反饋與支持！"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Result.error("處理失敗: " + e.getMessage()));
        }
    }

    // ==========================================
    // 輔助方法：發送舉報成功通知
    // ==========================================
    private void sendSuccessNotification(String reporterUsername, Report report) {
        try {
            User reporter = userRepository.findByUsername(reporterUsername).orElse(null);
            if (reporter == null) return;

            Notification notification = new Notification();
            notification.setRecipient(reporter);
            notification.setSender(null); // 系統通知
            notification.setType(Notification.NotificationType.SYSTEM);
            notification.setTitle("🎉 舉報處理成功");
            notification.setContent(
                    "親愛的用戶，\n\n" +
                            "感謝你維護社區環境！你提交的舉報已成功處理。\n\n" +
                            "📋 舉報詳情：\n" +
                            "• 被舉報用戶：" + (report.getReportedUser() != null ? report.getReportedUser().getUsername() : "未知") + "\n" +
                            "• 舉報類別：" + report.getCategory() + "\n" +
                            "• 處理結果：被舉報用戶已接受相應處罰\n\n" +
                            "你的舉報幫助我們營造了更好的社區氛圍，感謝你的支持！\n\n" +
                            "ChronoTeam 管理團隊"
            );
            notification.setDeletedContent(report.getReason());
            notification.setDeleteReason("舉報成立，已處罰");
            notification.setRelatedReviewId(report.getReviewId());
            notification.setRead(false);

            notificationRepository.save(notification);
        } catch (Exception e) {
            System.err.println("發送舉報成功通知失敗: " + e.getMessage());
        }
    }

    // ==========================================
    // 輔助方法：發送舉報不成立通知 (語氣更好聽)
    // ==========================================
    private void sendDismissNotification(String reporterUsername, Report report) {
        try {
            User reporter = userRepository.findByUsername(reporterUsername).orElse(null);
            if (reporter == null) return;

            Notification notification = new Notification();
            notification.setRecipient(reporter);
            notification.setSender(null); // 系統通知
            notification.setType(Notification.NotificationType.SYSTEM);
            notification.setTitle("📌 舉報處理反饋");
            notification.setContent(
                    "親愛的用戶，\n\n" +
                            "感謝你提交舉報，幫助我們維護社區秩序。\n\n" +
                            "📋 舉報詳情：\n" +
                            "• 被舉報用戶：" + (report.getReportedUser() != null ? report.getReportedUser().getUsername() : "未知") + "\n" +
                            "• 舉報類別：" + report.getCategory() + "\n" +
                            "• 處理結果：經核查，暫未發現違規行為\n\n" +
                            "💡 說明：\n" +
                            "我們已將該用戶列入重點關注名單，會持續監控其行為。\n" +
                            "如發現更多違規證據，歡迎再次舉報。\n\n" +
                            "感謝你的理解與支持！\n\n" +
                            "ChronoTeam 管理團隊"
            );
            notification.setDeletedContent(report.getReason());
            notification.setDeleteReason("舉報不成立，已關注");
            notification.setRelatedReviewId(report.getReviewId());
            notification.setRead(false);

            notificationRepository.save(notification);
        } catch (Exception e) {
            System.err.println("發送舉報不成立通知失敗: " + e.getMessage());
        }
    }
}