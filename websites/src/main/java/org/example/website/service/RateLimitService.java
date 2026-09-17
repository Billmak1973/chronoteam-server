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

    // 全局操作限制：1分鐘內最多 20 次
    private static final int MAX_GLOBAL_ACTIONS_PER_MINUTE = 20;
    // 單條評論操作限制：1分鐘內最多 10 次
    private static final int MAX_REVIEW_ACTIONS_PER_MINUTE = 10;

    private static final int BAN_DURATION_MINUTES = 10;   // 封禁時長：10分鐘
    private static final int WINDOW_SECONDS = 60;         // 滑動窗口大小：60秒

    private static final String REDIS_REVIEW_WINDOW_PREFIX = "rate_limit:review:"; // 單條評論窗口
    private static final String REDIS_GLOBAL_WINDOW_PREFIX = "rate_limit:global:"; // 全局窗口
    private static final String REDIS_BAN_KEY_PREFIX = "rate_limit:ban:";

    /**
     * 檢查並記錄用戶操作 (點贊/踩) - 使用 ZSet 滑動窗口
     * @param username 用戶名
     * @param reviewId 評論 ID
     * @throws RuntimeException 如果被限流或封禁，將拋出異常攔截操作
     */
    public void checkAndRecordAction(String username, Long reviewId) {
        // 1. 【高性能攔截】先檢查 Redis 中是否處於封禁狀態
        String banKey = REDIS_BAN_KEY_PREFIX + username;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(banKey))) {
            Long ttl = redisTemplate.getExpire(banKey, TimeUnit.SECONDS);
            long minutesLeft = (ttl != null && ttl > 0) ? (ttl / 60 + 1) : 1;
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

        long now = System.currentTimeMillis();
        long windowStart = now - (WINDOW_SECONDS * 1000L);
        // 使用 時間戳 + 納米時間 確保極端併發下 member 唯一
        String member = now + ":" + System.nanoTime();

        // 3. 【滑動窗口計數 A】檢查單條評論限制 (10次/分鐘)
        String reviewKey = REDIS_REVIEW_WINDOW_PREFIX + username + ":" + reviewId;
        if (isLimitExceeded(reviewKey, now, windowStart, member, MAX_REVIEW_ACTIONS_PER_MINUTE)) {
            triggerBan(user, MAX_REVIEW_ACTIONS_PER_MINUTE, "對同一條評論1分鐘內頻繁操作");
            setBanRedis(username);
            throw new RuntimeException("對同一條評論1分鐘內操作達到 " + MAX_REVIEW_ACTIONS_PER_MINUTE + " 次，已被限制操作 " + BAN_DURATION_MINUTES + " 分鐘。");
        }

        // 4. 【滑動窗口計數 B】檢查全局操作限制 (20次/分鐘)
        String globalKey = REDIS_GLOBAL_WINDOW_PREFIX + username;
        if (isLimitExceeded(globalKey, now, windowStart, member, MAX_GLOBAL_ACTIONS_PER_MINUTE)) {
            triggerBan(user, MAX_GLOBAL_ACTIONS_PER_MINUTE, "1分鐘內全局頻繁操作");
            setBanRedis(username);
            throw new RuntimeException("1分鐘內全局操作達到 " + MAX_GLOBAL_ACTIONS_PER_MINUTE + " 次，已被限制操作 " + BAN_DURATION_MINUTES + " 分鐘。");
        }
    }

    /**
     * 核心滑動窗口邏輯：清理過期數據 -> 加入新數據 -> 檢查總數
     */
    private boolean isLimitExceeded(String key, long now, long windowStart, String member, int maxLimit) {
        // 3.1 移除 60 秒窗口之外的舊記錄
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        // 3.2 將當前操作加入 ZSet
        redisTemplate.opsForZSet().add(key, member, (double) now);
        // 3.3 設置 ZSet 的過期時間 (比窗口時間稍長 5 秒，防止內存洩漏)
        redisTemplate.expire(key, WINDOW_SECONDS + 5, TimeUnit.SECONDS);
        // 3.4 統計當前 60 秒窗口內的實際操作次數
        Long currentCount = redisTemplate.opsForZSet().zCard(key);

        return currentCount != null && currentCount >= maxLimit;
    }

    /**
     * 在 Redis 中設置封禁標記
     */
    private void setBanRedis(String username) {
        String banKey = REDIS_BAN_KEY_PREFIX + username;
        redisTemplate.opsForValue().set(banKey, "1", BAN_DURATION_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 觸發封禁邏輯 (僅在達到閾值時調用，極大減少數據庫寫入)
     */
    @Transactional
    public void triggerBan(User user, int triggerTimes, String banReason) {
        Optional<RateLimitLog> existingLog = rateLimitLogRepository.findTopByUserOrderByActionTimeDesc(user);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime bannedUntil = now.plusMinutes(BAN_DURATION_MINUTES);

        if (existingLog.isPresent() && existingLog.get().getStatus() == RateLimitLog.LimitStatus.BANNED) {
            // 如果已經在封禁中，延長封禁時間並更新次數
            RateLimitLog log = existingLog.get();
            log.setBannedUntil(bannedUntil);
            log.setTimes(triggerTimes);
            log.setUpdatedAt(now);
            log.setBanReason(banReason); // 更新原因
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
            newLog.setBanReason(banReason);
            newLog.setStatus(RateLimitLog.LimitStatus.BANNED);
            rateLimitLogRepository.save(newLog);
        }
    }

    /**
     * 批量更新已過期的封禁記錄為 EXPIRED 狀態
     */
    @Transactional
    public void updateExpiredBans() {
        rateLimitLogRepository.updateExpiredBans(
                RateLimitLog.LimitStatus.EXPIRED,
                RateLimitLog.LimitStatus.BANNED
        );
    }
}