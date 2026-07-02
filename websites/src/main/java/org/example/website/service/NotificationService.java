package org.example.website.service;

import org.example.website.entity.UserInteractionStats;
import org.example.website.repository.ReviewRepository;
import org.example.website.repository.UserInteractionStatsRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class NotificationService {

    private final UserInteractionStatsRepository statsRepository;
    private final ReviewRepository reviewRepository;

    public NotificationService(UserInteractionStatsRepository statsRepository,
                               ReviewRepository reviewRepository) {
        this.statsRepository = statsRepository;
        this.reviewRepository = reviewRepository;
    }

    public static final String TYPE_REVIEW_REPLY = "REVIEW_REPLY";
    public static final String TYPE_REVIEW_MENTION = "REVIEW_MENTION";

    /**
     *  核心：獲取未讀消息數量 (僅需 2 次 DB 交互：1次查時間，1次查數量)
     */
    public long getUnreadCount(String username, String type) {
        // 1. 獲取該用戶該類型的最後查看時間 (只需查一次)
        Optional<UserInteractionStats> statsOpt = statsRepository.findByUser_UsernameAndType(username, type);

        LocalDateTime lastViewedAt;
        if (statsOpt.isPresent()) {
            lastViewedAt = statsOpt.get().getLastViewedAt();
        } else {
            // 如果從沒看過，設為很久以前，這樣所有歷史消息都算未讀
            lastViewedAt = LocalDateTime.now().minusYears(10);
        }

        // 2. 根據類型調用 Repository 進行一次性統計 (只需查一次)
        if (TYPE_REVIEW_REPLY.equals(type)) {
            return reviewRepository.countUnreadReplies(username, lastViewedAt);
        } else if (TYPE_REVIEW_MENTION.equals(type)) {
            return reviewRepository.countUnreadMentions("@" + username, username, lastViewedAt);
        }

        return 0;
    }

    /**
     * 標記已讀 (更新基準時間)
     */
    public void markAsRead(String username, String type) {
        LocalDateTime now = LocalDateTime.now();
        int updatedRows = statsRepository.updateLastViewedAt(username, type, now);
        if (updatedRows == 0) {
            statsRepository.insertInitialRecord(username, type, now);
        }
    }

    public long getTotalUnreadCount(String username) {
        // 這裡只會執行 2 次 SQL 查詢 (一次 Reply, 一次 Mention)，無論有多少條消息，都是 O(1) 複雜度
        return getUnreadCount(username, TYPE_REVIEW_REPLY) +
                getUnreadCount(username, TYPE_REVIEW_MENTION);
    }
}