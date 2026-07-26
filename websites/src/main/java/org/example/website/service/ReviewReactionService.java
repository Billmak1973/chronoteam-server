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

    /**
     * 點贊功能
     */
    @Transactional
    public Map<String, Object> toggleLike(Long reviewId, String username) {
        Map<String, Object> response = new HashMap<>();

        // 🛡 核心修改 1：第一步先進行高效限流檢查！
        // 如果被限制，RateLimitService 會拋出 RuntimeException，終止後續所有數據庫操作
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
            // 2. 處理點贊/取消點贊邏輯
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