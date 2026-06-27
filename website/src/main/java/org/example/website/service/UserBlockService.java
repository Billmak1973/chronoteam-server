package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.Notification;
import org.example.website.entity.UserBlock;
import org.example.website.entity.AdminPenalty;
import org.example.website.repository.NotificationRepository;
import org.example.website.repository.UserBlockRepository;
import org.example.website.repository.AdminPenaltyRepository;
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
    private final AdminPenaltyRepository adminPenaltyRepository;

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



}