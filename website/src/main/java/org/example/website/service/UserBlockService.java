package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.Notification;
import org.example.website.entity.UserBlock;
import org.example.website.repository.NotificationRepository;
import org.example.website.repository.UserBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public Map<String, Object> blockUser(String blockerUsername, String blockedUsername) {
        Map<String, Object> response = new HashMap<>();

        // 1. 不能禁言自己
        if (blockerUsername.equals(blockedUsername)) {
            response.put("success", false);
            response.put("message", "不能禁言自己");
            return response;
        }

        // 2. 检查 A->B 是否已经存在
        Optional<UserBlock> existingBlock = userBlockRepository.findByBlockerUsernameAndBlockedUsername(
                blockerUsername, blockedUsername);

        if (existingBlock.isPresent()) {
            response.put("success", false);
            response.put("message", "你已经禁言了该用户");
            return response;
        }

        // 3. 只创建一条记录：A->B
        UserBlock block = new UserBlock();
        block.setBlockerUsername(blockerUsername);
        block.setBlockedUsername(blockedUsername);
        userBlockRepository.save(block);

        response.put("success", true);
        response.put("message", "已禁言该用户，双方将无法互相回复");
        return response;
    }

    @Transactional
    public Map<String, Object> unblockUser(String blockerUsername, String blockedUsername) {
        Map<String, Object> response = new HashMap<>();

        // 只删除 A->B 记录
        userBlockRepository.deleteByBlockerUsernameAndBlockedUsername(blockerUsername, blockedUsername);

        response.put("success", true);
        response.put("message", "已解除禁言");
        return response;
    }

    /**
     * 管理员禁言（全局禁言，有期限）
     */
    @Transactional
    public Map<String, Object> adminBanUser(String bannedUsername, String adminUsername,
                                            int durationMinutes, String reason) {
        Map<String, Object> response = new HashMap<>();

        // 1. 創建禁言記錄
        UserBlock block = new UserBlock();
        block.setBlockerUsername(adminUsername);
        block.setBlockedUsername(bannedUsername);
        block.setExpiresAt(LocalDateTime.now().plusMinutes(durationMinutes));
        userBlockRepository.save(block);

        // 2.  計算禁言時長描述 (前端傳入的必定是 1440 的倍數：天/週/月)
        long days = durationMinutes / 1440;
        String timeDesc;
        if (days >= 30 && days % 30 == 0) {
            timeDesc = (days / 30) + "個月";
        } else if (days >= 7 && days % 7 == 0) {
            timeDesc = (days / 7) + "週";
        } else {
            timeDesc = days + "天";
        }

        // 3.  創建系統通知發送給被禁言用戶 (發送到「管理通知」Tab)
        try {
            Notification notification = new Notification();
            notification.setRecipientUsername(bannedUsername);
            notification.setSenderUsername(adminUsername);
            notification.setType(Notification.NotificationType.SYSTEM); // 確保發到管理通知
            notification.setTitle("⚠️ 您的帳戶已被禁言");

            LocalDateTime unbanTime = LocalDateTime.now().plusMinutes(durationMinutes);

            String content = String.format(
                    "管理員 %s 已對您的帳戶實施禁言措施。\n" +
                            "禁言時長：%s\n" +
                            "禁言原因：%s\n" +
                            "解封時間：%s\n\n" +
                            "在此期間您將無法發表評論或回復。如有異議，您可以通過系統通知頁面提交申訴。",
                    adminUsername,
                    timeDesc,
                    reason != null ? reason : "違反社區規範",
                    unbanTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            );

            notification.setContent(content);
            // 將禁言原因存入 deleteReason 字段，方便前端判斷是否為禁言通知並顯示申訴按鈕
            notification.setDeleteReason(reason != null ? reason : "違反社區規範");
            notification.setCreatedAt(LocalDateTime.now());
            notification.setRead(false);

            notificationRepository.save(notification);
            System.out.println("📨 已向用戶 " + bannedUsername + " 發送禁言通知");

        } catch (Exception e) {
            System.err.println("❌ 發送禁言通知失敗: " + e.getMessage());
            // 通知失敗不影響禁言操作
        }

        response.put("success", true);
        response.put("message", "用戶已被全局禁言 " + timeDesc); // 返回更友好的提示
        return response;
    }

    /**
     *  检查用户是否被全局禁言（管理员禁言）
     */
    public boolean isGloballyBanned(String username) {
        return false; // 占位
    }

    /**
     * 检查两个用户是否互相禁言（双向检查）
     */
    public boolean isBlocked(String user1, String user2) {
        return userBlockRepository.existsMutualBlock(user1, user2);
    }

    public boolean canInteract(String user1, String user2) {
        // 检查 A->B 或 B->A 是否存在
        // 只要有一条记录，双方就不能互相回复
        boolean aBlockedB = userBlockRepository.findByBlockerUsernameAndBlockedUsername(user1, user2).isPresent();
        boolean bBlockedA = userBlockRepository.findByBlockerUsernameAndBlockedUsername(user2, user1).isPresent();

        // 只要有一方禁言了对方，双方都不能回复
        return !(aBlockedB || bBlockedA);
    }

    /**
     * 檢查用戶是否被管理員全局禁言（返回有效的禁言記錄）
     */
    public java.util.Optional<UserBlock> getActiveGlobalBan(String username) {
        // 查詢 blocker 為 "admin" 的記錄
        java.util.Optional<UserBlock> blockOpt = userBlockRepository.findByBlockerUsernameAndBlockedUsername("admin", username);
        if (blockOpt.isPresent()) {
            UserBlock block = blockOpt.get();
            // 如果有過期時間，且已過期，則視為未禁言
            if (block.getExpiresAt() != null && block.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
                return java.util.Optional.empty();
            }
            return blockOpt;
        }
        return java.util.Optional.empty();
    }

}