package org.example.website.controller;

import org.example.website.entity.User;
import org.example.website.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;  // 新增导入
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserProfileController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;  // 新增

    @PutMapping("/update-profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> updates,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        // 1. 獲取當前登錄用戶
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        //  記錄舊的用戶名，用於後續精準清除 Redis 緩存
        String oldUsername = user.getUsername();

        // 2. 遍歷並校驗傳入的字段 (融合代碼2的邏輯)
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.equals("username")) {
                //  核心校驗 1：新用戶名和原用戶名一樣
                if (value.equals(oldUsername)) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "SAME_USERNAME"));
                }

                //  核心校驗 2：新用戶名已被其他用戶註冊
                if (userRepository.existsByUsername(value)) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "USERNAME_EXISTS"));
                }

                // 校驗通過，才允許修改
                user.setUsername(value);
            } else if (key.equals("address")) {
                user.setAddress(value.isEmpty() ? null : value);
            } else if (key.equals("backupAddress")) {
                user.setBackupAddress(value.isEmpty() ? null : value);
            }
        }

        // 3. 保存到數據庫
        userRepository.save(user);

        // 4. 【关键】清除 Redis 缓存 (使用 oldUsername 確保舊緩存被徹底清除)
        String cacheKey = "user:info:" + oldUsername;
        redisTemplate.delete(cacheKey);
        System.out.println("✅ 已清除用户缓存: " + cacheKey);

        // 5. 返回成功響應
        return ResponseEntity.ok(Map.of("success", true, "message", "更新成功"));
    }
}