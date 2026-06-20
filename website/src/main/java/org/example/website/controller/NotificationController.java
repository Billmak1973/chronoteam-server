package org.example.website.controller;

import org.example.website.repository.NotificationRepository;
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

    // 構造函數注入
    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * 獲取當前用戶的未讀系統通知數量
     * 用於導航欄紅點顯示
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            response.put("count", 0);
            return ResponseEntity.ok(response);
        }

        String username = authentication.getName();
        long count = notificationRepository.countByRecipientUsernameAndIsReadFalse(username);

        response.put("count", count);
        return ResponseEntity.ok(response);
    }
}