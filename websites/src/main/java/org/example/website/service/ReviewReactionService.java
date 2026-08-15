package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.Review;
import org.example.website.entity.ReviewReaction;
import org.example.website.entity.User;
import org.example.website.repository.ReviewReactionRepository;
import org.example.website.repository.ReviewRepository;
import org.example.website.repository.UserRepository;
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
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final AdminPenaltyService adminPenaltyService;

    // 🛡 核心新增：注入基於 Redis + DB 的高效限流服務
    private final RateLimitService rateLimitService;
    private final NotificationPushService pushService;
    private final NotificationService notificationService;

    /**
     * 點贊功能 (包含實時推送邏輯)
     */
    @Transactional
    public Map<String, Object> toggleLike(Long reviewId, String username) {
        Map<String, Object> response = new HashMap<>();

        // 🛡 核心修改 1：第一步先進行高效限流檢查！
        try {
            rateLimitService.checkAndRecordAction(username);
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
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
            // 2. 處理點贊/取消點贊邏輯
            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new RuntimeException("評論不存在"));

            Optional<ReviewReaction> existingReaction =
                    reactionRepository.findByReviewIdAndUser_Username(reviewId, username);

            boolean isLiked = false;
            boolean isDisliked = false;
            int likeCount = review.getLikeCount() != null ? review.getLikeCount() : 0;
            int dislikeCount = review.getDislikeCount() != null ? review.getDislikeCount() : 0;

            // 【新增】標記本次操作是否為「新增點贊」，用於後續判斷是否需要推送
            boolean isNewLike = false;

            if (existingReaction.isPresent()) {
                ReviewReaction reaction = existingReaction.get();
                if ("LIKE".equals(reaction.getReactionType())) {
                    // 取消點贊
                    reactionRepository.delete(reaction);
                    likeCount--;
                    isLiked = false;
                } else {
                    // 從踩改為讚：dislike-1, like+1
                    reaction.setReactionType("LIKE");
                    reactionRepository.save(reaction);
                    likeCount++;
                    dislikeCount--;
                    isLiked = true;
                    isDisliked = false;
                    isNewLike = true; // 視為新增點贊
                }
            } else {
                User user = userRepository.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("用戶不存在"));

                // 新增點贊
                ReviewReaction newReaction = new ReviewReaction();
                newReaction.setReviewId(reviewId);
                newReaction.setUser(user);
                newReaction.setReactionType("LIKE");
                newReaction.setCreatedAt(LocalDateTime.now());
                reactionRepository.save(newReaction);
                likeCount++;
                isLiked = true;
                isNewLike = true; // 視為新增點贊
            }

            // 更新評論表的點贊數和踩數
            review.setLikeCount(likeCount);
            review.setDislikeCount(dislikeCount);
            reviewRepository.save(review);

            // ==========================================
            // 【核心新增】：實時推送邏輯
            // ==========================================
            // 只有當是「新增點贊」且「不是自己點贊自己」時，才推送通知
            if (isNewLike && review.getUser() != null) {
                String targetUsername = review.getUser().getUsername();
                if (!targetUsername.equals(username)) {
                    try {
                        // 查詢該用戶最新的「點贊我的」未讀數量
                        // 注意：因為當前事務還未提交，getUnreadCount 查到的可能是舊數據
                        // 但為了實時性，我們通常假設當前操作 +1，或者依賴數據庫讀已提交狀態
                        // 這裡為了簡單且準確，我們直接調用 Service 查詢 (需注意事務隔離級別，通常 Read Committed 即可)
                        // 如果擔心事務問題，可以簡單地將當前 count + 1 推送，或者信任 DB 查詢
                        long newCount = notificationService.getUnreadCount(targetUsername, NotificationService.TYPE_LIKED_ME);

                        // 如果因為事務隔離導致查不到剛插入的記錄，newCount 可能少 1
                        // 為了確保前端紅點正確 +1，我們可以手動 +1 (僅針對當前這次操作)
                        // 但更穩妥的方式是依賴 DB 查詢。如果發現紅點沒變，通常是事務隔離問題。
                        // 這裡我們直接使用查詢結果。如果發現有延遲，可考慮在 pushService 內部做 +1 優化。

                        // 推送給目標用戶
                        pushService.pushNotificationUpdate(targetUsername, NotificationService.TYPE_LIKED_ME, newCount);
                    } catch (Exception e) {
                        // 推送失敗不應影響主業務流程
                        System.err.println("推送點贊通知失敗: " + e.getMessage());
                    }
                }
            }
            // ==========================================

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

        // 🛡️ 核心修改 1：第一步先進行高效限流檢查！
        try {
            rateLimitService.checkAndRecordAction(username);
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("banned", true);
            return response; // 直接返回錯誤，不寫數據庫
        }

        // 檢查是否被管理員永久拉黑
        if (adminPenaltyService.isBlacklisted(username)) {
            response.put("success", false);
            response.put("message", "BLACKLISTED");
            response.put("blacklisted", true);
            return response;
        }

        try {
            // 2. 處理踩/取消踩邏輯
            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new RuntimeException("評論不存在"));

            Optional<ReviewReaction> existingReaction =
                    reactionRepository.findByReviewIdAndUser_Username(reviewId, username);

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
                    // 從讚改為踩：like-1, dislike+1
                    reaction.setReactionType("DISLIKE");
                    reactionRepository.save(reaction);
                    dislikeCount++;
                    likeCount--;
                    isDisliked = true;
                    isLiked = false;
                }
            } else {
                User user = userRepository.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("用戶不存在"));

                // 新增踩
                ReviewReaction newReaction = new ReviewReaction();
                newReaction.setReviewId(reviewId);
                newReaction.setUser(user);
                newReaction.setReactionType("DISLIKE");
                newReaction.setCreatedAt(LocalDateTime.now());
                reactionRepository.save(newReaction);
                dislikeCount++;
                isDisliked = true;
            }

            // 更新評論表的點贊數和踩數
            review.setLikeCount(likeCount);
            review.setDislikeCount(dislikeCount);
            reviewRepository.save(review);

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
}