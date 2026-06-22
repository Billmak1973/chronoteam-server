package org.example.website.controller;

import org.example.website.repository.NotificationRepository;
import org.example.website.service.NotificationService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    // 修改構造函數，注入 NotificationService
    public NotificationController(NotificationRepository notificationRepository, NotificationService notificationService) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            response.put("systemCount", 0);
            response.put("messageCount", 0);
            return ResponseEntity.ok(response);
        }

        String username = authentication.getName();

        //  1. 系統通知未讀數 (來自 Notification 表，例如：管理員刪除評論)
        long systemCount = notificationRepository.countByRecipientUsernameAndIsReadFalse(username);

        //  2. 消息通知未讀數 (來自 Review 互動，例如：回覆我的、@我的)
        long messageCount = notificationService.getTotalUnreadCount(username);

        response.put("systemCount", systemCount);
        response.put("messageCount", messageCount);
        return ResponseEntity.ok(response);
    }

    //  新增：標記所有互動消息為已讀 (解決紅點不消失問題)
    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body("未登入");
        }

        String username = authentication.getName();

        // 核心：更新「回復我的」和「提到我的」最後查看時間
        // 這樣後端計算未讀數量時，就會把這些視為已讀
        notificationService.markAsRead(username, NotificationService.TYPE_REVIEW_REPLY);
        notificationService.markAsRead(username, NotificationService.TYPE_REVIEW_MENTION);

        return ResponseEntity.ok().body("標記成功");
    }
}