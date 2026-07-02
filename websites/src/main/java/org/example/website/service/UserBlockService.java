package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.UserBlock;
import org.example.website.entity.User;
import org.example.website.repository.UserBlockRepository;
import org.example.website.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
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
        Optional<UserBlock> existingBlock = userBlockRepository.findByBlocker_UsernameAndBlockedUser_Username(
                blockerUsername, blockedUsername);

        if (existingBlock.isPresent()) {
            response.put("success", false);
            response.put("message", "你已经禁言了该用户");
            return response;
        }

        //  3. 核心修改：先透過 username 查出 User 實體
        User blocker = userRepository.findByUsername(blockerUsername)
                .orElseThrow(() -> new RuntimeException("發起禁言的用戶不存在"));
        User blockedUser = userRepository.findByUsername(blockedUsername)
                .orElseThrow(() -> new RuntimeException("被禁言的用戶不存在"));

        // 4. 创建一条记录：A->B
        UserBlock block = new UserBlock();
        block.setBlocker(blocker);         //  設置 User 關聯，不再是 setBlockerUsername
        block.setBlockedUser(blockedUser); //  設置 User 關聯，不再是 setBlockedUsername
        userBlockRepository.save(block);

        response.put("success", true);
        response.put("message", "已禁言该用户，双方将无法互相回复");
        return response;
    }

    @Transactional
    public Map<String, Object> unblockUser(String blockerUsername, String blockedUsername) {
        Map<String, Object> response = new HashMap<>();

        // 只删除 A->B 记录
        userBlockRepository.deleteByBlocker_UsernameAndBlockedUser_Username(blockerUsername, blockedUsername);

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
        boolean aBlockedB = userBlockRepository.findByBlocker_UsernameAndBlockedUser_Username(user1, user2).isPresent();
        boolean bBlockedA = userBlockRepository.findByBlocker_UsernameAndBlockedUser_Username(user2, user1).isPresent();

        // 只要有一方禁言了对方，双方都不能回复
        return !(aBlockedB || bBlockedA);
    }

}