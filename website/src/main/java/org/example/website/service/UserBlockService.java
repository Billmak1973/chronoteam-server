package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.UserBlock;
import org.example.website.repository.UserBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;

    /**
     *  普通用户禁言（双向禁言）
     * 用户A禁言用户B → 创建 A→B 的禁言记录
     */
    @Transactional
    public Map<String, Object> blockUser(String blockerUsername, String blockedUsername) {
        Map<String, Object> response = new HashMap<>();

        // 不能禁言自己
        if (blockerUsername.equals(blockedUsername)) {
            response.put("success", false);
            response.put("message", "不能禁言自己");
            return response;
        }

        // 检查是否已经禁言
        if (userBlockRepository.findByBlockerUsernameAndBlockedUsername(
                blockerUsername, blockedUsername).isPresent()) {
            response.put("success", false);
            response.put("message", "您已经禁言了该用户");
            return response;
        }

        // 创建禁言记录
        UserBlock block = new UserBlock();
        block.setBlockerUsername(blockerUsername);
        block.setBlockedUsername(blockedUsername);
        userBlockRepository.save(block);

        response.put("success", true);
        response.put("message", "已禁言该用户，双方将无法互相回复");
        return response;
    }

    /**
     *  普通用户解除禁言
     * 用户A解除禁言用户B → 删除 A→B 的禁言记录
     */
    @Transactional
    public Map<String, Object> unblockUser(String blockerUsername, String blockedUsername) {
        Map<String, Object> response = new HashMap<>();

        userBlockRepository.deleteByBlockerUsernameAndBlockedUsername(
                blockerUsername, blockedUsername);

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

        // 这里可以调用现有的 RateLimitLog 或创建新的 AdminBan 表
        // 简化示例：使用 UserBlock 标记（实际应该用专门的表）
        UserBlock block = new UserBlock();
        block.setBlockerUsername("SYSTEM_ADMIN_" + adminUsername);  // 标记是管理员操作
        block.setBlockedUsername(bannedUsername);
        block.setExpiresAt(LocalDateTime.now().plusMinutes(durationMinutes));
        userBlockRepository.save(block);

        response.put("success", true);
        response.put("message", "用户已被全局禁言 " + durationMinutes + " 分钟");
        return response;
    }

    /**
     *  检查两个用户能否互相回复
     * 规则：只要有一方禁言了另一方，双方都不能回复
     */
    public boolean canInteract(String user1, String user2) {
        return !userBlockRepository.existsMutualBlock(user1, user2);
    }

    /**
     *  检查用户是否被全局禁言（管理员禁言）
     */
    public boolean isGloballyBanned(String username) {
        // 检查是否存在管理员发起的禁言（blocker 以 SYSTEM_ADMIN_ 开头）
        // 实际应该查询专门的 AdminBan 表
        return false; // 占位
    }
}