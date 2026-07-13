package org.example.website.service;

import org.example.website.dto.RegisterRequest;
import org.example.website.entity.User;
import org.example.website.repository.UserRepository;
import org.example.website.util.UidGenerator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DailyBusinessReportService dailyBusinessReportService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       RedisTemplate<String, Object> redisTemplate,
                       DailyBusinessReportService dailyBusinessReportService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.dailyBusinessReportService = dailyBusinessReportService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        // 1. 前置查重 (郵箱、手機號、用戶名)
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用戶名已存在");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("郵箱已被註冊");
        }
        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new RuntimeException("該手機號碼已被註冊");
        }

        // 2. 【關鍵修復 1】先執行可能失敗的業務邏輯（更新報表）
        // 如果這裡失敗，事務直接回滾，還沒執行 save()，絕對不會浪費 user_id！
        dailyBusinessReportService.incrementNewUsers();

        // 3. 構建 User 實體
        User user = new User();
        user.setUsername(request.getUsername());
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        if (request.getAddress() != null && !request.getAddress().isEmpty()) {
            user.setAddress(request.getAddress());
        }
        user.setRole(User.Role.CUSTOMER);

        // 4. 【關鍵修復 2】確保 UID 絕對唯一，防止 INSERT 失敗浪費 user_id
        String uid;
        int maxRetries = 10;
        do {
            uid = UidGenerator.nextUid(userRepository.count());
            maxRetries--;
        } while (userRepository.existsByUid(uid) && maxRetries > 0); //  循環校驗直到不重複

        if (userRepository.existsByUid(uid)) {
            throw new RuntimeException("系統繁忙，UID生成衝突，請稍後重試");
        }
        user.setUid(uid);

        // 5. 【最後一步】執行 INSERT 插入數據庫
        // 走到這裡，說明所有前置校驗和邏輯都已成功，INSERT 幾乎 100% 不會失敗
        User savedUser = userRepository.save(user);

        // 6. 【關鍵修復 3】Redis 緩存容錯
        // 即使 Redis 抖動失敗，也不應導致整個註冊事務回滾而浪費 user_id
        try {
            String redisKey = "user:info:" + savedUser.getUsername();
            redisTemplate.opsForValue().set(redisKey, savedUser, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            System.err.println("Redis 緩存失敗，但不影響註冊成功: " + e.getMessage());
        }

        return savedUser;
    }

    public User findByUsername(String username) {
        // 1. 先從 Redis 快取中查找
        String redisKey = "user:info:" + username;
        User cachedUser = (User) redisTemplate.opsForValue().get(redisKey);

        if (cachedUser != null) {
            System.out.println(" 從 Redis 快取中獲取用戶: " + username);
            return cachedUser;
        }

        // 2. 快取沒有，再去資料庫查找
        System.out.println(" 從資料庫獲取用戶: " + username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在，請重新登入"));

        // 3. 將資料庫查到的結果放入 Redis，設定 1 小時過期
        redisTemplate.opsForValue().set(redisKey, user, 1, TimeUnit.HOURS);

        return user;
    }
}