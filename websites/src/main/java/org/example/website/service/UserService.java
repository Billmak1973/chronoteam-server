package org.example.website.service;

import org.example.website.dto.RegisterRequest;
import org.example.website.entity.User;
import org.example.website.repository.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class UserService { //  建議直接改名為 UserService，配合你的 User 實體遷移
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       RedisTemplate<String, Object> redisTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public User register(RegisterRequest request) {
        //  User 的主鍵是 Long id，所以查重必須用 existsByUsername (不再是 existsById)
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用戶名已存在");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("郵箱已被註冊");
        }
        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new RuntimeException("該手機號碼已被註冊，請使用其他號碼或嘗試登入");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());

        //  核心修改 2：新的 User 實體中沒有 creditLimit 字段，已移除此行
        // user.setCreditLimit(5000.00);

        //  核心修改 3：設置默認角色為顧客 (對應 User.Role 枚舉)
        user.setRole(User.Role.CUSTOMER);

        User savedUser = userRepository.save(user);

        // 註冊成功後，將用戶資訊存入 Redis，設定 1 小時過期
        String redisKey = "user:info:" + savedUser.getUsername();
        redisTemplate.opsForValue().set(redisKey, savedUser, 1, TimeUnit.HOURS);

        return savedUser;
    }

    public User findByUsername(String username) {
        // 1. 先從 Redis 快取中查找
        String redisKey = "user:info:" + username;
        User cachedUser = (User) redisTemplate.opsForValue().get(redisKey);

        if (cachedUser != null) {
            System.out.println("🚀 從 Redis 快取中獲取用戶: " + username);
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