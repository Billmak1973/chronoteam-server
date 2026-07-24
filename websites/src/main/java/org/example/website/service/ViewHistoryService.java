package org.example.website.service;

import org.example.website.entity.Product;
import org.example.website.entity.User;
import org.example.website.entity.ViewHistory;
import org.example.website.repository.ProductRepository;
import org.example.website.repository.UserRepository;
import org.example.website.repository.ViewHistoryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ViewHistoryService {
    private final ViewHistoryRepository viewHistoryRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ViewHistoryService(ViewHistoryRepository viewHistoryRepository,
                              UserRepository userRepository,
                              ProductRepository productRepository) {
        this.viewHistoryRepository = viewHistoryRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    /**
     * 記錄瀏覽歷史（如果已存在則更新時間）
     */
    @Transactional
    public void recordView(String username, Integer productId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        ViewHistory existingHistory = viewHistoryRepository
                .findByUser_UsernameAndProduct_ProductId(username, productId);

        if (existingHistory != null) {
            existingHistory.setViewedAt(LocalDateTime.now());
            viewHistoryRepository.save(existingHistory);
        } else {
            ViewHistory history = new ViewHistory();
            history.setUser(user);
            history.setProduct(product);
            viewHistoryRepository.save(history);
        }

        // 清理超過 200 條的記錄
        List<ViewHistory> allHistories = viewHistoryRepository
                .findByUser_UsernameOrderByViewedAtDesc(username);
        if (allHistories.size() > 200) {
            List<ViewHistory> toDelete = allHistories.subList(200, allHistories.size());
            viewHistoryRepository.deleteAll(toDelete);
        }
    }

    /**
     * 獲取用戶的瀏覽歷史
     */
    public List<ViewHistory> getUserHistory(String username) {
        return viewHistoryRepository.findByUser_UsernameOrderByViewedAtDesc(username);
    }

    /**
     * 清除瀏覽歷史
     */
    @Transactional
    public void clearHistory(String username, String period) {
        // ✅ 核心修復：先根據 username 獲取 User 實體，拿到主鍵 id
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));
        Long userId = user.getId();

        LocalDateTime cutoffDate = switch (period) {
            case "1day" -> LocalDateTime.now().minusDays(1);
            case "1week" -> LocalDateTime.now().minusWeeks(1);
            case "1month" -> LocalDateTime.now().minusMonths(1);
            case "all" -> null; // 全部刪除
            default -> throw new IllegalArgumentException("無效的時間範圍: " + period);
        };

        if ("all".equals(period)) {
            viewHistoryRepository.deleteAllForUserId(userId);
        } else {
            viewHistoryRepository.deleteRecentForUserId(userId, cutoffDate);
        }
    }

    /**
     * 定時任務：每天凌晨 2 點清理半年前的記錄
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanOldHistory() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(6);
        viewHistoryRepository.deleteOlderThan(cutoffDate);
        System.out.println("✅ 已自動清理半年前的瀏覽歷史記錄");
    }

    /**
     * 批量刪除瀏覽歷史
     */
    @Transactional
    public void batchDelete(String username, List<Long> historyIds) {
        if (historyIds == null || historyIds.isEmpty()) {
            return;
        }
        // 調用 Repository 方法，確保只刪除屬於該 username 的記錄
        viewHistoryRepository.deleteByHistoryIdInAndUser_Username(historyIds, username);
    }
}