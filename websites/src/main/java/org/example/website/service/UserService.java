package org.example.website.service;

import org.example.website.dto.RegisterRequest;
import org.example.website.entity.User;
import org.example.website.entity.UserAddress;
import org.example.website.repository.UserAddressRepository;
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
    private final UserAddressRepository userAddressRepository;
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       RedisTemplate<String, Object> redisTemplate,
                       DailyBusinessReportService dailyBusinessReportService, UserAddressRepository userAddressRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.dailyBusinessReportService = dailyBusinessReportService;
        this.userAddressRepository = userAddressRepository;
    }

    @Transactional
    public User register(RegisterRequest request) {
        // 1. 前置查重
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用戶名已存在");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("郵箱已被註冊");
        }
        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new RuntimeException("該手機號碼已被註冊");
        }

        // 2. 更新報表
        dailyBusinessReportService.incrementNewUsers();

        // 3. 構建 User 實體
        User user = new User();
        user.setUsername(request.getUsername());
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(User.Role.CUSTOMER);

        // 4. 確保 UID 絕對唯一
        String uid;
        int maxRetries = 10;
        do {
            uid = UidGenerator.nextUid(userRepository.count());
            maxRetries--;
        } while (userRepository.existsByUid(uid) && maxRetries > 0);

        if (userRepository.existsByUid(uid)) {
            throw new RuntimeException("系統繁忙，UID生成衝突，請稍後重試");
        }
        user.setUid(uid);

        // 5. 執行 INSERT 插入用戶數據庫
        User savedUser = userRepository.save(user);

        // 6. 【核心修改】處理可選的地址信息 (非強制)
        // 只有當前端傳來了有效的完整地址時，才創建 UserAddress 記錄
        if (request.getFullAddress() != null && !request.getFullAddress().trim().isEmpty()) {
            UserAddress address = new UserAddress();
            address.setUser(savedUser);
            // 默認使用註冊時的姓名和手機號作為收件信息，提升用戶體驗
            address.setReceiverName(savedUser.getName());
            address.setContactPhone(savedUser.getPhone());
            address.setFullAddress(request.getFullAddress().trim());
            address.setRanking(1); // 設為 1，作為默認/首選地址

            userAddressRepository.save(address);
        }

        // 7. Redis 緩存容錯
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