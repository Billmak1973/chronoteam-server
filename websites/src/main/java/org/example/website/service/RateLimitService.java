package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.RateLimitLog;
import org.example.website.entity.User;
import org.example.website.repository.RateLimitLogRepository;
import org.example.website.repository.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RateLimitLogRepository rateLimitLogRepository;
    private final UserRepository userRepository;

    private static final int MAX_ACTIONS_PER_MINUTE = 10; // 1分鐘內最大次數
    private static final int BAN_DURATION_MINUTES = 10;   // 封禁時長：10分鐘

    private static final String REDIS_COUNTER_KEY_PREFIX = "rate_limit:action:";
    private static final String REDIS_BAN_KEY_PREFIX = "rate_limit:ban:";

    /**
     * 檢查並記錄用戶操作 (點贊/踩)
     * @param username 用戶名
     * @throws RuntimeException 如果被限流或封禁，將拋出異常攔截操作
     */
    public void checkAndRecordAction(String username) {
        // 1. 【高性能攔截】先檢查 Redis 中是否處於封禁狀態
        String banKey = REDIS_BAN_KEY_PREFIX + username;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(banKey))) {
            Long ttl = redisTemplate.getExpire(banKey, TimeUnit.SECONDS);
            long minutesLeft = (ttl != null && ttl >0) ? (ttl / 60 + 1) : 1;
            throw new RuntimeException("操作過於頻繁，已被暫時限制，請 " + minutesLeft + " 分鐘後再試。");
        }

        // 2. 【雙重保險】檢查數據庫中是否有未過期的封禁記錄 (防止 Redis 數據丟失)
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        Optional<RateLimitLog> activeBan = rateLimitLogRepository.findTopByUserAndStatusAndBannedUntilAfter(
                user, RateLimitLog.LimitStatus.BANNED, LocalDateTime.now()
        );

        if (activeBan.isPresent()) {
            RateLimitLog log = activeBan.get();
            long minutesLeft = java.time.Duration.between(LocalDateTime.now(), log.getBannedUntil()).toMinutes() + 1;
            // 同步到 Redis，避免下次再查庫
            redisTemplate.opsForValue().set(banKey, "1", minutesLeft, TimeUnit.MINUTES);
            throw new RuntimeException("操作過於頻繁，已被暫時限制，請 " + minutesLeft + " 分鐘後再試。");
        }

        // 3. 【高頻計數】日常點贊只在 Redis 中 +1，不寫數據庫！
        String counterKey = REDIS_COUNTER_KEY_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(counterKey);

        // 如果是這 1 分鐘內的第一次操作，設置 60 秒過期
        if (count != null && count == 1) {
            redisTemplate.expire(counterKey, 60, TimeUnit.SECONDS);
        }

        // 4. 【觸發閾值】如果達到 30 次，寫入數據庫封禁記錄
        if (count != null && count >= MAX_ACTIONS_PER_MINUTE) {
            triggerBan(user, count.intValue());

            // 在 Redis 中設置封禁標記，10分鐘過期
            redisTemplate.opsForValue().set(banKey, "1", BAN_DURATION_MINUTES, TimeUnit.MINUTES);

            throw new RuntimeException("1分鐘內操作達到 " + MAX_ACTIONS_PER_MINUTE + " 次，已被限制操作 " + BAN_DURATION_MINUTES + " 分鐘。");
        }
    }

    /**
     * 觸發封禁邏輯 (僅在達到閾值時調用，極大減少數據庫寫入)
     */
    @Transactional
    public void triggerBan(User user, int triggerTimes) {
        Optional<RateLimitLog> existingLog = rateLimitLogRepository.findTopByUserOrderByActionTimeDesc(user);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime bannedUntil = now.plusMinutes(BAN_DURATION_MINUTES);

        if (existingLog.isPresent() && existingLog.get().getStatus() == RateLimitLog.LimitStatus.BANNED) {
            // 如果已經在封禁中，延長封禁時間並更新次數
            RateLimitLog log = existingLog.get();
            log.setBannedUntil(bannedUntil);
            log.setTimes(triggerTimes);
            log.setUpdatedAt(now);
            rateLimitLogRepository.save(log);
        } else {
            // 創建新的封禁記錄
            RateLimitLog newLog = new RateLimitLog();
            newLog.setUser(user);
            newLog.setActionTime(now);
            newLog.setTimes(triggerTimes);
            newLog.setUpdatedAt(now);
            newLog.setBannedUntil(bannedUntil);
            newLog.setBannedBy("SYSTEM");
            newLog.setBanReason("1分鐘內頻繁操作達到 " + triggerTimes + " 次");
            newLog.setStatus(RateLimitLog.LimitStatus.BANNED);
            rateLimitLogRepository.save(newLog);
        }
    }

    /**
     * 批量更新已過期的封禁記錄為 EXPIRED 狀態
     */
    @Transactional
    public void updateExpiredBans() {
        // 傳入枚舉值，讓 Hibernate 安全地處理類型轉換
        rateLimitLogRepository.updateExpiredBans(
                RateLimitLog.LimitStatus.EXPIRED,
                RateLimitLog.LimitStatus.BANNED
        );
    }
}