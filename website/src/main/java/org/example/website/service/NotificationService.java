package org.example.website.service;

import org.example.website.entity.Notification;
import org.example.website.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    // 🟢 增加 productId 參數，用於生成跳轉到商品詳情頁的 URL
    @Transactional
    public void createReplyNotification(String recipient, String sender, Long reviewId, String content, Integer productId) {
        Notification notification = new Notification();
        notification.setRecipientUsername(recipient);
        notification.setSenderUsername(sender);
        notification.setType(Notification.NotificationType.REPLY);

        // 截斷過長內容
        String shortContent = content.length() > 20 ? content.substring(0, 20) + "..." : content;
        notification.setContent(sender + " 回復了你的評論: " + shortContent);

        // 生成跳轉連結，帶上 reviewId 方便前端定位或高亮
        notification.setTargetUrl("/product/" + productId + "?highlightReview=" + reviewId);
        notification.setRelatedReviewId(reviewId);

        notificationRepository.save(notification);
    }

    @Transactional
    public void createMentionNotification(String recipient, String sender, Long reviewId, String content, Integer productId) {
        Notification notification = new Notification();
        notification.setRecipientUsername(recipient);
        notification.setSenderUsername(sender);
        notification.setType(Notification.NotificationType.MENTION);

        String shortContent = content.length() > 20 ? content.substring(0, 20) + "..." : content;
        notification.setContent(sender + " 在評論中提到了你: " + shortContent);

        notification.setTargetUrl("/product/" + productId + "?highlightReview=" + reviewId);
        notification.setRelatedReviewId(reviewId);

        notificationRepository.save(notification);
    }

    public long getUnreadCount(String username) {
        return notificationRepository.countByRecipientUsernameAndIsReadFalse(username);
    }
}