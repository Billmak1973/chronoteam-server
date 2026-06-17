package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Notification;
import org.example.website.repository.NotificationRepository;
import org.example.website.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationService notificationService, NotificationRepository notificationRepository) {
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    // 獲取未讀數量 (用於導航欄紅點)
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication authentication) {
        String username = authentication.getName();
        long count = notificationService.getUnreadCount(username);
        Map<String, Object> response = new HashMap<>();
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    // 獲取消息列表 (支持分頁和類型篩選)
    @GetMapping("/list")
    public ResponseEntity<?> getNotifications(
            Authentication authentication,
            @RequestParam(required = false) String type, // REPLY, MENTION
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String username = authentication.getName();
        Page<Notification> notifications;

        if (type != null && !type.isEmpty()) {
            notifications = notificationRepository.findByRecipientUsernameAndTypeOrderByCreatedAtDesc(
                    username,
                    Notification.NotificationType.valueOf(type),
                    PageRequest.of(page, size)
            );
        } else {
            notifications = notificationRepository.findByRecipientUsernameOrderByCreatedAtDesc(username, PageRequest.of(page, size));
        }

        return ResponseEntity.ok(ApiResponse.okWithData("success", notifications));
    }

    // 標記為已讀
    @PutMapping("/read/{id}")
    public ResponseEntity<?> markAsRead(@PathVariable Long id, Authentication authentication) {
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification != null && notification.getRecipientUsername().equals(authentication.getName())) {
            notification.setRead(true);
            notificationRepository.save(notification);
            return ResponseEntity.ok(ApiResponse.ok("已標記為已讀"));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("無權操作"));
    }
    // 標記當前用戶的所有消息為已讀
    @PutMapping("/read-all")
    @Transactional //  必須加此註解，否則 @Modifying 的 UPDATE 語句不會提交
    public ResponseEntity<?> markAllAsRead(Authentication authentication) {
        String username = authentication.getName();
        notificationRepository.markAllAsRead(username);
        return ResponseEntity.ok(ApiResponse.ok("已全部標記為已讀"));
    }
}