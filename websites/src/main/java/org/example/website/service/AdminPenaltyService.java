package org.example.website.service;

import org.example.website.entity.AdminPenalty;
import org.example.website.entity.AdminPenalty.PenaltyStatus;
import org.example.website.entity.AdminPenalty.PenaltyType;
import org.example.website.entity.Notification;
import org.example.website.entity.User;
import org.example.website.repository.AdminPenaltyRepository;
import org.example.website.repository.NotificationRepository;
import org.example.website.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AdminPenaltyService {

    private final AdminPenaltyRepository adminPenaltyRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    public AdminPenaltyService(AdminPenaltyRepository adminPenaltyRepository,
                               NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.adminPenaltyRepository = adminPenaltyRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // ==========================================
    //  1. 懶檢查機制 (查詢時順便修正狀態，徹底告別定時任務)
    // ==========================================

    /**
     * 檢查用戶是否被「永久拉黑」 (BLACKLIST)
     * 邏輯：只要最新的一條 BLACKLIST 記錄是 ACTIVE，就是被拉黑。
     */
    public boolean isBlacklisted(String username) {
        Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository
                .findTopByTargetUser_UsernameAndTypeOrderByStartTimeDesc(username, PenaltyType.BLACKLIST);

        if (penaltyOpt.isEmpty()) return false;

        AdminPenalty penalty = penaltyOpt.get();
        // 如果管理員提前原諒了 (UNBANNED)，則返回 false
        if (penalty.getStatus() == PenaltyStatus.REVOKED) {
            return false;
        }
        return penalty.getStatus() == PenaltyStatus.ACTIVE;
    }

    /**
     * 檢查用戶是否被「有期封禁」 (BAN)
     * 邏輯：核心懶檢查！如果已過期，順便把資料庫狀態刷成 EXPIRED。
     */
    @Transactional
    public boolean isUserBanned(String username) {
        Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository
                .findTopByTargetUser_UsernameAndTypeOrderByStartTimeDesc(username, PenaltyType.BAN);

        if (penaltyOpt.isEmpty()) return false;

        AdminPenalty penalty = penaltyOpt.get();

        // 1. 已手動解除 (管理員提前解禁) -> 永遠返回 false
        if (penalty.getStatus() == PenaltyStatus.REVOKED) {
            return false;
        }

        // 2. 檢查是否已過期 (自然到期) ->  懶檢查核心
        if (penalty.getStatus() == PenaltyStatus.ACTIVE &&
                penalty.getEndTime() != null &&
                LocalDateTime.now().isAfter(penalty.getEndTime())) {

            // 順便修正資料庫狀態為 EXPIRED (寫入資料庫，下次查詢就不會再判斷時間了)
            penalty.setStatus(PenaltyStatus.EXPIRED);
            adminPenaltyRepository.save(penalty);
            return false;
        }

        // 3. 仍在 ACTIVE 狀態且未過期 -> 確實被禁言
        return penalty.getStatus() == PenaltyStatus.ACTIVE;
    }

    // ==========================================
    //  2. 管理員操作：處罰與解除
    // ==========================================

    /**
     * 管理員拉黑用戶 (永久)
     */
    @Transactional
    public void blacklistUser(String targetUsername, String adminUsername, String reason) {
        if (isBlacklisted(targetUsername)) {
            throw new RuntimeException("該用戶已被拉黑，無需重複操作");
        }

        // 創建處罰記錄
        createPenalty(targetUsername, adminUsername, PenaltyType.BLACKLIST, reason, null);

        // 發送系統通知
        sendBlacklistNotification(targetUsername, adminUsername, reason);
    }


    /**
     * 管理員解除拉黑
     */
    @Transactional
    public void unblacklistUser(String targetUsername) {
        updatePenaltyStatus(targetUsername, PenaltyType.BLACKLIST, PenaltyStatus.REVOKED);
    }

    /**
     * 管理員提前解除封禁
     */
    @Transactional
    public void unbanUser(String targetUsername) {
        updatePenaltyStatus(targetUsername, PenaltyType.BAN, PenaltyStatus.REVOKED);
    }

    // ==========================================
    //  3. 底層輔助方法
    // ==========================================

    /**
     * 統一更新處罰狀態 (用於解除禁言/拉黑)
     */
    private void updatePenaltyStatus(String username, PenaltyType type, PenaltyStatus newStatus) {
        Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository
                .findTopByTargetUser_UsernameAndTypeOrderByStartTimeDesc(username, type);

        if (penaltyOpt.isPresent()) {
            AdminPenalty penalty = penaltyOpt.get();
            // 只有 ACTIVE 狀態才能被解除
            if (penalty.getStatus() == PenaltyStatus.ACTIVE) {
                penalty.setStatus(newStatus);
                adminPenaltyRepository.save(penalty);
            }
        } else {
            throw new RuntimeException("找不到該用戶的" + type + "處罰記錄");
        }
    }

    /**
     * 創建新的處罰記錄 (通用底層方法)
     */
    private AdminPenalty createPenalty(String targetUsername, String adminUsername,
                                       PenaltyType type, String reason, LocalDateTime endTime) {

        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("被處罰的用戶不存在: " + targetUsername));

        User adminUser = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new RuntimeException("執行處罰的管理員不存在: " + adminUsername));

        AdminPenalty penalty = new AdminPenalty();

        penalty.setTargetUser(targetUser);
        penalty.setAdminUser(adminUser);

        penalty.setType(type);
        penalty.setReason(reason != null ? reason : "違反社區規範");
        penalty.setStartTime(LocalDateTime.now());
        penalty.setEndTime(endTime); // 拉黑為 null，封禁為具體時間
        penalty.setStatus(PenaltyStatus.ACTIVE);

        return adminPenaltyRepository.save(penalty);
    }

    // ==========================================
    //  4. 發送系統通知 (整合自版本 1)
    // ==========================================

    private void sendBlacklistNotification(String targetUsername, String adminUsername, String reason) {
        try {
            //  1. 透過 username 查出被拉黑用戶的 User 實體
            User targetUser = userRepository.findByUsername(targetUsername)
                    .orElseThrow(() -> new RuntimeException("目標用戶不存在"));

            //  2. 查出管理員的 User 實體
            // 注意：如果 adminUsername 是 "system" 且數據庫中沒有這個用戶，orElse(null) 會將其設為 null。
            // 因為你的 Notification 實體中 sender 字段沒有設置 nullable=false，所以允許為 null，不會報外鍵錯誤。
            User adminUser = userRepository.findByUsername(adminUsername).orElse(null);

            Notification notif = new Notification();

            //  3. 設置 User 關聯，取代原本的 setUsername
            notif.setRecipient(targetUser);
            notif.setSender(adminUser);

            notif.setType(Notification.NotificationType.SYSTEM);
            notif.setTitle("🚫 您的帳戶已被永久拉黑");
            notif.setContent(String.format(
                    "由於您嚴重違反社區規範，管理員已將您的帳戶永久拉黑。\n原因：%s\n您將無法再進行任何互動操作。",
                    reason
            ));
            notif.setDeleteReason(reason);
            // notif.setCreatedAt(LocalDateTime.now()); // 實體類已有 @CreationTimestamp，此行可省略
            notif.setRead(false);

            notificationRepository.save(notif);
        } catch (Exception e) {
            System.err.println("發送拉黑通知失敗: " + e.getMessage());
        }
    }

    /**
     * 管理員禁言（全局禁言，有期限）
     * 存入 admin_penalty 表，並精確綁定對應的系統通知 ID
     */
    @Transactional
    public Map<String, Object> adminBanUser(String bannedUsername, String adminUsername,
                                            int durationMinutes, String reason, Long reviewId, String reviewContent) {
        Map<String, Object> response = new HashMap<>();

        //  核心修改 1：先查出對應的 User 實體
        User bannedUser = userRepository.findByUsername(bannedUsername)
                .orElseThrow(() -> new RuntimeException("被禁言的用戶不存在"));
        User adminUser = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new RuntimeException("執行禁言的管理員不存在"));

        // 1. 計算禁言時長描述 (保持不變)
        long days = durationMinutes / 1440;
        String timeDesc;
        if (days >= 30 && days % 30 == 0) {
            timeDesc = (days / 30) + "個月";
        } else if (days >= 7 && days % 7 == 0) {
            timeDesc = (days / 7) + "週";
        } else {
            timeDesc = days + "天";
        }

        Long savedNotificationId = null;

        // 2. 核心修改 2：先創建並保存系統通知，獲取其 ID
        try {
            Notification notification = new Notification();

            //  設置 User 關聯，取代原本的 setUsername
            notification.setRecipient(bannedUser);
            notification.setSender(adminUser);

            notification.setType(Notification.NotificationType.SYSTEM);
            notification.setTitle("⚠️ 您的帳戶已被禁言");
            LocalDateTime unbanTime = LocalDateTime.now().plusMinutes(durationMinutes);

            String content = String.format(
                    "管理員 %s 已對您的帳戶實施禁言措施。\n" +
                            "禁言時長：%s\n" +
                            "禁言原因：%s\n" +
                            "解封時間：%s\n" +
                            "在此期間您將無法發表評論或回復。如有異議，您可以通過系統通知頁面提交申訴。",
                    adminUsername, timeDesc,
                    reason != null ? reason : "違反社區規範",
                    unbanTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            );
            notification.setContent(content);
            notification.setDeleteReason(reason != null ? reason : "違反社區規範");
            // notification.setCreatedAt(LocalDateTime.now()); // 實體類已有 @CreationTimestamp，此行可省略
            notification.setRead(false);

            // 關鍵：先保存通知，讓數據庫生成並返回 ID
            Notification savedNotification = notificationRepository.save(notification);

            //  核心修改 3：主鍵 getter 從 getId() 改為 getNotificationId()
            savedNotificationId = savedNotification.getNotificationId();

        } catch (Exception e) {
            System.err.println("❌ 發送禁言通知失敗: " + e.getMessage());
        }

        // 3. 創建 AdminPenalty 記錄並綁定通知 ID
        AdminPenalty penalty = new AdminPenalty();

        //  設置 User 關聯，取代原本的 setUsername
        penalty.setTargetUser(bannedUser);
        penalty.setAdminUser(adminUser);

        penalty.setType(AdminPenalty.PenaltyType.BAN);
        penalty.setReason(reason != null ? reason : "違反社區規範");
        // penalty.setStartTime(LocalDateTime.now()); // 實體類已有 @CreationTimestamp，此行可省略
        penalty.setEndTime(LocalDateTime.now().plusMinutes(durationMinutes));
        penalty.setStatus(AdminPenalty.PenaltyStatus.ACTIVE);
        penalty.setReviewId(reviewId);
        penalty.setReviewContent(reviewContent);

        // 核心修改：將剛才獲取的通知 ID 綁定到處罰記錄中
        if (savedNotificationId != null) {
            penalty.setNotificationId(savedNotificationId);
        }

        // 保存處罰記錄
        adminPenaltyRepository.save(penalty);

        response.put("success", true);
        response.put("message", "用戶已被全局禁言 " + timeDesc);
        return response;
    }

    /**
     * 檢查用戶是否被管理員全局禁言 (從 admin_penalty 表查詢)
     */
    public boolean isGloballyBanned(String username) {
        return getActiveGlobalBan(username).isPresent();
    }

    /**
     * 返回 AdminPenalty 對象
     */
    public Optional<AdminPenalty> getActiveGlobalBan(String username) {
        // 查詢狀態為 ACTIVE 的 BAN 記錄
        Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository
                .findTopByTargetUser_UsernameAndTypeAndStatusOrderByStartTimeDesc(
                        username,
                        AdminPenalty.PenaltyType.BAN,
                        AdminPenalty.PenaltyStatus.ACTIVE
                );

        // 過濾掉已經過期的記錄 (防止定時任務還沒來得及更新狀態時出錯)
        if (penaltyOpt.isPresent()) {
            AdminPenalty penalty = penaltyOpt.get();
            if (penalty.getEndTime() != null && penalty.getEndTime().isBefore(LocalDateTime.now())) {
                penalty.setStatus(AdminPenalty.PenaltyStatus.EXPIRED);
                adminPenaltyRepository.save(penalty); // 同步更新資料庫
                return Optional.empty(); // 已過期，視為未禁言
            }
            return penaltyOpt;
        }
        return Optional.empty();
    }

    @Transactional
    public void checkAndUpdatePenaltyStatus(Long penaltyId) {
        Optional<AdminPenalty> opt = adminPenaltyRepository.findById(penaltyId);
        if (opt.isPresent()) {
            AdminPenalty penalty = opt.get();
            // 如果是 ACTIVE 且已過期，順手改為 EXPIRED
            if (penalty.getStatus() == PenaltyStatus.ACTIVE &&
                    penalty.getEndTime() != null &&
                    LocalDateTime.now().isAfter(penalty.getEndTime())) {
                penalty.setStatus(PenaltyStatus.EXPIRED);
                adminPenaltyRepository.save(penalty);
            }
        }
    }
}