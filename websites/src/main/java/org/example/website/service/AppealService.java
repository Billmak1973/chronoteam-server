package org.example.website.service;

import org.example.website.dto.AppealRequest;
import org.example.website.entity.AdminPenalty;
import org.example.website.entity.Appeal;
import org.example.website.entity.Notification;
import org.example.website.repository.AdminPenaltyRepository;
import org.example.website.repository.AppealRepository;
import org.example.website.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.website.entity.User;
import org.example.website.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AppealService {

    private final AppealRepository appealRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AdminPenaltyRepository adminPenaltyRepository;

    public AppealService(AppealRepository appealRepository, NotificationRepository notificationRepository, UserRepository userRepository
    , AdminPenaltyRepository adminPenaltyRepository) {
        this.appealRepository = appealRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.adminPenaltyRepository = adminPenaltyRepository;
    }

    public Map<String, Object> submitAppeal(String username, AppealRequest request) {
        Map<String, Object> response = new HashMap<>();

        // 1. 校驗通知是否存在
        Notification notification = notificationRepository.findById(request.getNotificationId()).orElse(null);
        if (notification == null) {
            response.put("success", false);
            response.put("message", "關聯的通知不存在");
            return response;
        }

        // 2. 獲取該通知的所有歷史申訴記錄
        List<Appeal> existingAppeals = appealRepository.findByNotificationIdOrderByCreatedAtDesc(request.getNotificationId());

        // 3. 【核心修改】檢查是否已經有 APPROVED 的申訴 (如果已經成功解封，不能再申訴)
        boolean hasApproved = existingAppeals.stream()
                .anyMatch(a -> a.getStatus() == Appeal.AppealStatus.APPROVED);
        if (hasApproved) {
            response.put("success", false);
            response.put("message", "您的申訴已通過，帳戶已恢復，無需再次提交");
            return response;
        }

        // 4. 【核心修改】檢查是否還有 PENDING 的申訴 (審核中不能重複提交)
        boolean hasPending = existingAppeals.stream()
                .anyMatch(a -> a.getStatus() == Appeal.AppealStatus.PENDING);
        if (hasPending) {
            response.put("success", false);
            response.put("message", "您已提交過申訴，請耐心等待管理員審核");
            return response;
        }

        // 5. 【核心修改】檢查申訴次數限制 (針對永久拉黑 BLACKLIST，最多 3 次)
        if ("BLACKLIST".equals(request.getAppealType())) {
            if (existingAppeals.size() >= 3) {
                response.put("success", false);
                response.put("message", "您已達到永久拉黑的最大申訴次數 (3次)，無法再次提交申訴");
                return response;
            }
        }

        // 6. 構建新的 Appeal 實體
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("申訴用戶不存在"));

        Appeal appeal = new Appeal();
        appeal.setNotificationId(request.getNotificationId());
        appeal.setUser(user);
        appeal.setReason(request.getReason());

        try {
            appeal.setAppealType(Appeal.AppealType.valueOf(request.getAppealType()));
        } catch (IllegalArgumentException e) {
            appeal.setAppealType(Appeal.AppealType.BAN);
        }
        appeal.setStatus(Appeal.AppealStatus.PENDING);

        // 7. 保存數據庫
        Appeal savedAppeal = appealRepository.save(appeal);

        // 8. 更新 admin_penalty 表的 appeal_id 字段
        Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository.findByNotificationId(request.getNotificationId());
        if (penaltyOpt.isPresent()) {
            AdminPenalty penalty = penaltyOpt.get();
            penalty.setAppealId(savedAppeal.getAppealId());
            adminPenaltyRepository.save(penalty);
        }

        response.put("success", true);
        response.put("message", "申訴提交成功，請耐心等待管理員審核");
        return response;
    }


    @Transactional
    public void processAppeal(Long appealId, String adminResponse, String decision, Long adminId) {
        // 1. 查找申诉记录
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new RuntimeException("申诉记录不存在"));

        if (appeal.getStatus() != Appeal.AppealStatus.PENDING) {
            throw new RuntimeException("该申诉状态不是待处理，无法操作");
        }

        // 2. 更新 Appeal 表的通用字段
        appeal.setAdminResponse(adminResponse);
        appeal.setReviewedAt(LocalDateTime.now());
        appeal.setReviewedById(adminId);

        // 3. 获取关联的通知（用于发送新通知）
        Notification originalNotification = notificationRepository.findById(appeal.getNotificationId())
                .orElse(null);

        // 4. 根据决策执行不同逻辑
        if ("APPROVED".equals(decision)) {
            // === 情况：同意申诉 (是) ===
            appeal.setStatus(Appeal.AppealStatus.APPROVED);

            // 找到关联的处罚记录 (AdminPenalty)
            Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository.findByAppealId(appealId);

            if (penaltyOpt.isPresent()) {
                AdminPenalty penalty = penaltyOpt.get();
                // 将处罚状态改为 REVOKED (已撤销/解除)
                penalty.setStatus(AdminPenalty.PenaltyStatus.REVOKED);
                adminPenaltyRepository.save(penalty);
            } else {
                throw new RuntimeException("未找到关联的处罚记录，无法解除封禁");
            }

            // 5. 发送通知给用户 - 申诉成功
            if (originalNotification != null) {
                sendAppealResultNotification(
                        appeal.getUser(),
                        originalNotification,
                        "APPROVED",
                        adminResponse,
                        adminId
                );
            }

        } else if ("REJECTED".equals(decision)) {
            // === 情况：拒绝申诉 (否) ===
            appeal.setStatus(Appeal.AppealStatus.REJECTED);

            // AdminPenalty 的状态保持不变 (依然是 ACTIVE)

            // 5. 发送通知给用户 - 申诉失败
            if (originalNotification != null) {
                sendAppealResultNotification(
                        appeal.getUser(),
                        originalNotification,
                        "REJECTED",
                        adminResponse,
                        adminId
                );
            }
        }

        // 6. 保存申诉记录
        appealRepository.save(appeal);
    }

    /**
     * 发送申诉结果通知给用户
     */
    private void sendAppealResultNotification(User recipient, Notification originalNotif,
                                              String decision, String adminResponse, Long adminId) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(Notification.NotificationType.SYSTEM);

        // 获取管理员信息（如果可能）
        User adminUser = null;
        if (adminId != null) {
            adminUser = userRepository.findById(adminId).orElse(null);
        }
        notification.setSender(adminUser);

        if ("APPROVED".equals(decision)) {
            // 申诉成功通知
            notification.setTitle("✅ 您的申诉已通过");
            notification.setContent(String.format(
                    "您的申诉已审核通过！\n\n" +
                            "原处罚原因：%s\n" +
                            "管理员回复：%s\n\n" +
                            "您的账户限制已解除，可以正常使用所有功能。",
                    originalNotif.getDeleteReason() != null ? originalNotif.getDeleteReason() : "违反社区规范",
                    adminResponse != null ? adminResponse : "无"
            ));
        } else {
            // 申诉失败通知
            notification.setTitle("❌ 您的申诉未通过");
            notification.setContent(String.format(
                    "很抱歉，您的申诉未通过审核。\n\n" +
                            "原处罚原因：%s\n" +
                            "管理员回复：%s\n\n" +
                            "请遵守社区规范，继续违规可能会导致更严重的处罚。",
                    originalNotif.getDeleteReason() != null ? originalNotif.getDeleteReason() : "违反社区规范",
                    adminResponse != null ? adminResponse : "无"
            ));
        }

        // 关联原通知和申诉
        notification.setRelatedReviewId(originalNotif.getRelatedReviewId());
        // 注意：这里没有 relatedAppealId 字段，如果需要可以添加

        notification.setRead(false);
        notificationRepository.save(notification);
    }
}