package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Report;
import org.example.website.entity.Notification;
import org.example.website.entity.User;
import org.example.website.repository.ReportRepository;
import org.example.website.repository.NotificationRepository;
import org.example.website.repository.UserRepository;
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
    private final UserRepository userRepository;
    public ReportController(ReportRepository reportRepository, NotificationRepository notificationRepository, UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse> submitReport(@RequestBody Map<String, Object> request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        String reporterUsername = authentication.getName();

        // 獲取被舉報用戶實體
        User reportedUser = userRepository.findByUsername((String) request.get("reportedUsername"))
                .orElseThrow(() -> new RuntimeException("被舉報用戶不存在"));

        Long reviewId = request.get("reviewId") != null ? Long.valueOf(request.get("reviewId").toString()) : null;
        String targetType = (String) request.get("targetType");
        String category = (String) request.get("category");
        String reason = (String) request.get("reason");

        if (category == null || reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("請填寫完整的舉報信息"));
        }

        if ("REVIEW".equals(targetType) && reviewId != null) {
            // 如果是舉報評論，檢查是否已舉報過該評論 ID
            if (reportRepository.existsByReporter_UsernameAndReviewId(reporterUsername, reviewId)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("您已經舉報過這條評論了，請勿重複提交！"));
            }
        }

        // 獲取舉報人實體
        User reporterUser = userRepository.findByUsername(reporterUsername)
                .orElseThrow(() -> new RuntimeException("舉報用戶不存在"));

        // 1. 保存舉報記錄
        Report report = new Report();
        report.setReporter(reporterUser); // 修改：設置 User 關聯
        report.setReportedUser(reportedUser); // 修改：設置 User 關聯
        report.setReviewId(reviewId);
        report.setTargetType(targetType != null ? targetType : "REVIEW");
        report.setCategory(category);
        report.setReason(reason.trim());
        report.setStatus(Report.ReportStatus.PENDING);
        reportRepository.save(report);

        // 2. 給舉報人發送一條「受理通知」
        try {
            Notification notif = new Notification();
            notif.setRecipient(reporterUser); // 修改：設置 User 關聯

            // 查找系統用戶作為發送者，若無則為 null
            User systemUser = userRepository.findByUsername("system").orElse(null);
            notif.setSender(systemUser); // 修改：設置 User 關聯

            notif.setType(Notification.NotificationType.SYSTEM);
            notif.setTitle("舉報已受理");
            notif.setContent(String.format(
                    "您提交的關於用戶 [%s] 的評論舉報已成功受理。管理員將在 24 小時內進行審核，感謝您維護社區環境！",
                    reportedUser.getUsername()
            ));
            // createdAt 由 @CreationTimestamp 自動處理
            notif.setRead(false);
            notificationRepository.save(notif);
        } catch (Exception e) {
            System.err.println("發送舉報受理通知失敗: " + e.getMessage());
        }

        // 3. 在返回前獲取最新的未讀通知數量（包含剛剛生成的那條受理通知）
        long unreadCount = notificationRepository.countByRecipient_UsernameAndIsReadFalse(reporterUsername);

        // 4. 構建返回數據
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("message", "舉報已提交，感謝您的反饋，管理員將盡快審核！");
        responseData.put("unreadCount", unreadCount); // 將未讀數量返回給前端

        return ResponseEntity.ok(ApiResponse.okWithData("舉報已提交，感謝您的反饋，管理員將盡快審核！", responseData));
    }
}