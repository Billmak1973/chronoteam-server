package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Report;
import org.example.website.entity.Notification;
import org.example.website.repository.ReportRepository;
import org.example.website.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportRepository reportRepository;
    private final NotificationRepository notificationRepository;

    public ReportController(ReportRepository reportRepository, NotificationRepository notificationRepository) {
        this.reportRepository = reportRepository;
        this.notificationRepository = notificationRepository;
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse> submitReport(@RequestBody Map<String, Object> request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        String reporterUsername = authentication.getName();
        String reportedUsername = (String) request.get("reportedUsername");
        Long reviewId = request.get("reviewId") != null ? Long.valueOf(request.get("reviewId").toString()) : null;
        String targetType = (String) request.get("targetType");
        String category = (String) request.get("category");
        String reason = (String) request.get("reason");

        if (reportedUsername == null || category == null || reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("請填寫完整的舉報信息"));
        }

        if ("REVIEW".equals(targetType) && reviewId != null) {
            // 如果是舉報評論，檢查是否已舉報過該評論 ID
            if (reportRepository.existsByReporterUsernameAndReviewId(reporterUsername, reviewId)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("您已經舉報過這條評論了，請勿重複提交！"));
            }
        }

        // 1. 保存舉報記錄
        Report report = new Report();
        report.setReporterUsername(reporterUsername);
        report.setReportedUsername(reportedUsername);
        report.setReviewId(reviewId);
        report.setTargetType(targetType != null ? targetType : "REVIEW");
        report.setCategory(category);
        report.setReason(reason.trim());
        report.setStatus(Report.ReportStatus.PENDING);
        reportRepository.save(report);

        // 2. 給舉報人發送一條「受理通知」
        try {
            Notification notif = new Notification();
            notif.setRecipientUsername(reporterUsername); // 接收者是舉報人自己
            notif.setSenderUsername("system");
            notif.setType(Notification.NotificationType.SYSTEM);
            notif.setTitle(" 舉報已受理");
            notif.setContent(String.format(
                    "您提交的關於用戶 [%s] 的評論舉報已成功受理。管理員將在 24 小時內進行審核，感謝您維護社區環境！",
                    reportedUsername
            ));
            notif.setCreatedAt(LocalDateTime.now());
            notif.setRead(false);
            notificationRepository.save(notif);
        } catch (Exception e) {
            System.err.println("發送舉報受理通知失敗: " + e.getMessage());
        }

        //  3. 在返回前獲取最新的未讀通知數量（包含剛剛生成的那條受理通知）
        long unreadCount = notificationRepository.countByRecipientUsernameAndIsReadFalse(reporterUsername);

        //  4. 構建返回數據
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("message", "舉報已提交，感謝您的反饋，管理員將盡快審核！");
        responseData.put("unreadCount", unreadCount); // 將未讀數量返回給前端

        return ResponseEntity.ok(ApiResponse.okWithData("舉報已提交，感謝您的反饋，管理員將盡快審核！", responseData));
    }
}