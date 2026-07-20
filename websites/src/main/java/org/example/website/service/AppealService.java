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
import java.util.HashMap;
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

    @Transactional
    public Map<String, Object> submitAppeal(String username, AppealRequest request) {
        Map<String, Object> response = new HashMap<>();

        // 1. 校驗通知是否存在
        Notification notification = notificationRepository.findById(request.getNotificationId())
                .orElse(null);
        if (notification == null) {
            response.put("success", false);
            response.put("message", "關聯的通知不存在");
            return response;
        }

        // 2. 校驗是否已經提交過待處理的申訴 (防止重複提交)
        if (appealRepository.findByNotificationIdAndStatus(request.getNotificationId(), Appeal.AppealStatus.PENDING).isPresent()) {
            response.put("success", false);
            response.put("message", "您已提交過申訴，請耐心等待管理員審核");
            return response;
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("申訴用戶不存在"));
        // 3. 構建 Appeal 實體
        Appeal appeal = new Appeal();
        appeal.setNotificationId(request.getNotificationId());
        appeal.setUser(user);
        appeal.setReason(request.getReason());

        // 解析枚舉類型 (前端傳 BAN，這裡轉為枚舉)
        try {
            appeal.setAppealType(Appeal.AppealType.valueOf(request.getAppealType()));
        } catch (IllegalArgumentException e) {
            appeal.setAppealType(Appeal.AppealType.BAN); // 默認 fallback 到 BAN
        }

        appeal.setStatus(Appeal.AppealStatus.PENDING);

        // 4. 保存數據庫
        Appeal savedAppeal = appealRepository.save(appeal);

        // 5. 更新 admin_penalty 表的 appeal_id 字段
        Optional<AdminPenalty>penaltyOpt= adminPenaltyRepository.findByNotificationId(request.getNotificationId());
        if (penaltyOpt.isPresent()) {
            AdminPenalty penalty = penaltyOpt.get();
            // 设置 appeal_id
            penalty.setAppealId(savedAppeal.getAppealId());
            adminPenaltyRepository.save(penalty);
        }
        response.put("success", true);
        response.put("message", "申訴提交成功，請耐心等待管理員審核");
        return response;
    }
}