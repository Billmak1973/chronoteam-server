package org.example.website.controller;

import org.example.website.entity.Announcement;
import org.example.website.repository.AnnouncementReceiptRepository;
import org.example.website.repository.NotificationRepository;
import org.example.website.repository.UserRepository;
import org.example.website.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final AnnouncementReceiptRepository announcementReceiptRepository;
    private final UserRepository userRepository;

    public NotificationController(NotificationRepository notificationRepository,
                                  NotificationService notificationService,
                                  AnnouncementReceiptRepository announcementReceiptRepository,
                                  UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.announcementReceiptRepository = announcementReceiptRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            response.put("systemCount", 0);
            response.put("messageCount", 0);
            response.put("replyCount", 0);
            response.put("mentionCount", 0);
            response.put("likeCount", 0);
            return ResponseEntity.ok(response);
        }

        String username = authentication.getName();

        // 1. 原本的系統通知未讀數
        long oldSystemCount = notificationRepository.countByRecipient_UsernameAndIsReadFalse(username);//完全沒用

        // 2. 【新增】全新的公告未讀數
        long announcementCount = announcementReceiptRepository.countByUser_UsernameAndAnnouncement_TypeAndIsReadFalse(
                username,
                Announcement.AnnouncementType.STOCK
        );

        // 3. 總系統通知數 = 原本的 + 新的公告
        long totalSystemCount = oldSystemCount + announcementCount;

        // 4. 各類互動消息未讀數
        long replyCount = notificationService.getUnreadCount(username, NotificationService.TYPE_REVIEW_REPLY);
        long mentionCount = notificationService.getUnreadCount(username, NotificationService.TYPE_REVIEW_MENTION);
        long likeCount = notificationService.getUnreadCount(username, NotificationService.TYPE_LIKED_ME);
        long messageCount = replyCount + mentionCount + likeCount;

        // 5. 返回給前端
        response.put("systemCount", totalSystemCount);
        response.put("messageCount", messageCount);
        response.put("replyCount", replyCount);
        response.put("mentionCount", mentionCount);
        response.put("likeCount", likeCount);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body("未登入");
        }

        String username = authentication.getName();

        // 標記原本的互動消息為已讀
        notificationService.markAsRead(username, NotificationService.TYPE_REVIEW_REPLY);
        notificationService.markAsRead(username, NotificationService.TYPE_REVIEW_MENTION);
        notificationService.markAsRead(username, NotificationService.TYPE_LIKED_ME);

        // 【核心修復】：先獲取 User 實體拿到 ID，再傳給 Repository 進行 UPDATE
        // 這樣完美避開了 Hibernate 6 在 UPDATE 語句中解析 ar.user.username 的 Bug
        userRepository.findByUsername(username).ifPresent(user -> {
            announcementReceiptRepository.markAllAsReadByUserAndType(user.getId(), Announcement.AnnouncementType.STOCK);
        });

        return ResponseEntity.ok().body("標記成功");
    }
}