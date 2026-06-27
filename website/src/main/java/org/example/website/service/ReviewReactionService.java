
package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.Notification;
import org.example.website.entity.RateLimitLog;
import org.example.website.entity.Review;
import org.example.website.entity.ReviewReaction;
import org.example.website.repository.NotificationRepository;
import org.example.website.repository.RateLimitLogRepository;
import org.example.website.repository.ReviewReactionRepository;
import org.example.website.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReviewReactionService {

    private final ReviewReactionRepository reactionRepository;
    private final RateLimitLogRepository rateLimitRepository;
    private final ReviewRepository reviewRepository;
    private final RateLimitCleanupService cleanupService;  // 用於安排延遲任務
    private final NotificationRepository notificationRepository;
    private final AdminPenaltyService adminPenaltyService;
    // 常量
    private static final int WARNING_THRESHOLD = 2;   // 超过2次开始记录
    private static final int BAN_THRESHOLD = 6;        // 超过等于6次封禁
    private static final int BAN_DURATION_MINUTES = 10; // 封禁10分钟

    /**
     * 点赞功能
     */
    @Transactional
    public Map<String, Object> toggleLike(Long reviewId, String username) {
        Map<String, Object> response = new HashMap<>();

        // 1. 检查是否被封禁（不区分操作类型）
        RateLimitCheckResult checkResult = checkRateLimit(username);
        if (checkResult.isBanned()) {
            response.put("success", false);
            response.put("message", "點贊已頻繁！請" + checkResult.getRemainingMinutes() + "分鐘後再試！");
            response.put("banned", true);
            return response;
        }

        // 檢查是否被管理員永久拉黑
        if (adminPenaltyService.isBlacklisted(username)) {
            response.put("success", false);
            response.put("message", "BLACKLISTED");
            response.put("blacklisted", true);
            return response;
        }


        try {
            // 2. 处理点赞/取消点赞逻辑
            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new RuntimeException("評論不存在"));

            Optional<ReviewReaction> existingReaction =
                    reactionRepository.findByReviewIdAndUsername(reviewId, username);

            boolean isLiked = false;
            boolean isDisliked = false;
            int likeCount = review.getLikeCount() != null ? review.getLikeCount() : 0;
            int dislikeCount = review.getDislikeCount() != null ? review.getDislikeCount() : 0;

            if (existingReaction.isPresent()) {
                ReviewReaction reaction = existingReaction.get();
                if ("LIKE".equals(reaction.getReactionType())) {
                    // 取消点赞
                    reactionRepository.delete(reaction);
                    likeCount--;
                    isLiked = false;
                } else {
                    // 从踩改为赞：dislike-1, like+1
                    reaction.setReactionType("LIKE");
                    reactionRepository.save(reaction);
                    likeCount++;
                    dislikeCount--;
                    isLiked = true;
                    isDisliked = false;
                }
            } else {
                // 新增点赞
                ReviewReaction newReaction = new ReviewReaction();
                newReaction.setReviewId(reviewId);
                newReaction.setUsername(username);
                newReaction.setReactionType("LIKE");
                newReaction.setCreatedAt(LocalDateTime.now());
                reactionRepository.save(newReaction);
                likeCount++;
                isLiked = true;
            }

            // 更新评论表的点赞数和踩数
            review.setLikeCount(likeCount);
            review.setDislikeCount(dislikeCount);
            reviewRepository.save(review);

            // 3. 更新速率限制记录（每次操作都算一次，不区分类型）
            updateRateLimitLog(username);

            response.put("success", true);
            response.put("liked", isLiked);
            response.put("disliked", isDisliked);
            response.put("likeCount", likeCount);
            response.put("dislikeCount", dislikeCount);
            response.put("message", "操作成功");

            return response;

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "操作失敗：" + e.getMessage());
            return response;
        }
    }

    /**
     * 踩功能
     */
    @Transactional
    public Map<String, Object> toggleDislike(Long reviewId, String username) {
        Map<String, Object> response = new HashMap<>();

        // 1. 检查是否被封禁（不区分操作类型）
        RateLimitCheckResult checkResult = checkRateLimit(username);
        if (checkResult.isBanned()) {
            response.put("success", false);
            response.put("message", "點贊已頻繁！請" + checkResult.getRemainingMinutes() + "分鐘後再試！");
            response.put("banned", true);
            return response;
        }

        // 檢查是否被管理員永久拉黑
        if (adminPenaltyService.isBlacklisted(username)) {
            response.put("success", false);
            response.put("message", "BLACKLISTED");
            response.put("blacklisted", true);
            return response;
        }

        try {
            // 2. 处理踩/取消踩逻辑
            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new RuntimeException("評論不存在"));

            Optional<ReviewReaction> existingReaction =
                    reactionRepository.findByReviewIdAndUsername(reviewId, username);

            boolean isLiked = false;
            boolean isDisliked = false;
            int likeCount = review.getLikeCount() != null ? review.getLikeCount() : 0;
            int dislikeCount = review.getDislikeCount() != null ? review.getDislikeCount() : 0;

            if (existingReaction.isPresent()) {
                ReviewReaction reaction = existingReaction.get();
                if ("DISLIKE".equals(reaction.getReactionType())) {
                    // 取消踩
                    reactionRepository.delete(reaction);
                    dislikeCount--;
                    isDisliked = false;
                } else {
                    // 从赞改为踩：like-1, dislike+1
                    reaction.setReactionType("DISLIKE");
                    reactionRepository.save(reaction);
                    dislikeCount++;
                    likeCount--;
                    isDisliked = true;
                    isLiked = false;
                }
            } else {
                // 新增踩
                ReviewReaction newReaction = new ReviewReaction();
                newReaction.setReviewId(reviewId);
                newReaction.setUsername(username);
                newReaction.setReactionType("DISLIKE");
                newReaction.setCreatedAt(LocalDateTime.now());
                reactionRepository.save(newReaction);
                dislikeCount++;
                isDisliked = true;
            }

            // 更新评论表的点赞数和踩数
            review.setLikeCount(likeCount);
            review.setDislikeCount(dislikeCount);
            reviewRepository.save(review);

            // 3. 更新速率限制记录（每次操作都算一次，不区分类型）
            updateRateLimitLog(username);

            response.put("success", true);
            response.put("liked", isLiked);
            response.put("disliked", isDisliked);
            response.put("likeCount", likeCount);
            response.put("dislikeCount", dislikeCount);
            response.put("message", "操作成功");

            return response;

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "操作失敗：" + e.getMessage());
            return response;
        }
    }

    /**
     * 检查速率限制（不区分操作类型）
     */
    private RateLimitCheckResult checkRateLimit(String username) {
        LocalDateTime now = LocalDateTime.now();

        // 检查是否被封禁
        Optional<RateLimitLog> bannedRecord = rateLimitRepository.findBannedAction(username, now);
        if (bannedRecord.isPresent()) {
            RateLimitLog log = bannedRecord.get();
            long remainingMinutes = java.time.Duration.between(now, log.getBannedUntil()).toMinutes();
            return new RateLimitCheckResult(true, remainingMinutes > 0 ? remainingMinutes : 1);
        }

        return new RateLimitCheckResult(false, 0);
    }

    /**
     * 更新速率限制日志（不区分操作类型）
     */
    @Transactional
    private void updateRateLimitLog(String username) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneMinuteAgo = now.minusMinutes(1);

        // 查找1分钟内的记录（不区分操作类型）
        Optional<RateLimitLog> existingLog = rateLimitRepository.findRecentAction(username, oneMinuteAgo);

        if (existingLog.isPresent()) {
            // 记录已存在，更新次数
            RateLimitLog log = existingLog.get();
            int newTimes = log.getTimes() + 1;
            log.setTimes(newTimes);
            log.setUpdatedAt(now);

            if (newTimes >= BAN_THRESHOLD && log.getBannedUntil() == null) {
                LocalDateTime bannedUntil = now.plusMinutes(BAN_DURATION_MINUTES);
                log.setBannedUntil(bannedUntil);
                rateLimitRepository.save(log);

                // 安排延遲任務，封禁結束時自動轉移記錄到歷史表
                cleanupService.scheduleUnbanTask(log.getId(), bannedUntil);
                System.out.println("⚠️ 用戶 " + username + " 因頻繁操作被封禁10分鐘，將在 " + bannedUntil + " 解封");

                // ========================================
                //  新增：自動發送系統通知，告知用戶封禁起止時間
                // ========================================
                try {
                    Notification banNotification = new Notification();
                    banNotification.setRecipientUsername(username);
                    banNotification.setSenderUsername("system");
                    banNotification.setType(Notification.NotificationType.SYSTEM);
                    banNotification.setTitle("⚠️ 點贊/踩功能已暫時鎖定");

                    // 格式化時間，讓用戶一眼看懂
                    java.time.format.DateTimeFormatter formatter =
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String startTime = now.format(formatter);
                    String endTime   = bannedUntil.format(formatter);

                    banNotification.setContent(
                            "系統檢測到您在短時間內頻繁操作（1 分鐘內連續點贊/踩達 "
                                    + newTimes + " 次），為防止濫用，您的點贊與踩功能已被暫時鎖定。\n\n"
                                    + "🔒 鎖定開始時間：" + startTime + "\n"
                                    + "🔓 鎖定結束時間：" + endTime + "\n\n"
                                    + "在此期間您將無法對任何評論進行點贊或踩操作。"
                                    + "鎖定期滿後將自動恢復，請勿重複頻繁操作以免再次觸發封禁。"
                    );

                    banNotification.setCreatedAt(now);
                    notificationRepository.save(banNotification);

                    System.out.println("📨 已向用戶 " + username + " 發送封禁系統通知");
                } catch (Exception notifyEx) {
                    // 通知發送失敗不應影響封禁邏輯本身
                    System.err.println("❌ 發送封禁通知失敗: " + notifyEx.getMessage());
                }
            }
            else {
                rateLimitRepository.save(log);
            }

            // 如果超过2次，记录日志
            if (newTimes >= WARNING_THRESHOLD) {
                System.out.println("⚡ 用戶 " + username + " 1分鐘內已操作 " + newTimes + " 次");
            }

        } else {
            // 第一次操作，创建新记录（不设置 actionType）
            RateLimitLog newLog = new RateLimitLog();
            newLog.setUsername(username);
            newLog.setActionTime(now);
            newLog.setTimes(1);
            newLog.setUpdatedAt(now);
            newLog.setBannedUntil(null);

            rateLimitRepository.save(newLog);
        }

        // 清理旧记录（超过10分钟的）
        rateLimitRepository.deleteOldRecords(now.minusMinutes(BAN_DURATION_MINUTES + 1));
    }

    // 内部类：速率限制检查结果
    private static class RateLimitCheckResult {
        private boolean banned;
        private long remainingMinutes;

        public RateLimitCheckResult(boolean banned, long remainingMinutes) {
            this.banned = banned;
            this.remainingMinutes = remainingMinutes;
        }

        public boolean isBanned() { return banned; }
        public long getRemainingMinutes() { return remainingMinutes; }
    }
}