package org.example.website.service;

import org.example.website.entity.Customer;
import org.example.website.entity.Product;
import org.example.website.entity.ViewHistory;
import org.example.website.repository.CustomerRepository;
import org.example.website.repository.ProductRepository;
import org.example.website.repository.ViewHistoryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ViewHistoryService {
    private final ViewHistoryRepository viewHistoryRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public ViewHistoryService(ViewHistoryRepository viewHistoryRepository,
                              CustomerRepository customerRepository,
                              ProductRepository productRepository) {
        this.viewHistoryRepository = viewHistoryRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    /**
     * 記錄瀏覽歷史（如果已存在則更新時間）
     */
    @Transactional
    public void recordView(String username, Integer productId) {
        Customer customer = customerRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        //  檢查是否已存在該商品的瀏覽記錄
        ViewHistory existingHistory = viewHistoryRepository
                .findByCustomer_UsernameAndProduct_Id(username, productId);

        if (existingHistory != null) {
            //  如果已存在，只更新瀏覽時間
            existingHistory.setViewedAt(LocalDateTime.now());
            viewHistoryRepository.save(existingHistory);
        } else {
            // 如果不存在，創建新記錄
            ViewHistory history = new ViewHistory();
            history.setCustomer(customer);
            history.setProduct(product);
            viewHistoryRepository.save(history);
        }

        //  清理超過 200 條的記錄
        List<ViewHistory> allHistories = viewHistoryRepository
                .findByCustomer_UsernameOrderByViewedAtDesc(username);
        if (allHistories.size() > 200) {
            // 截取第 200 條之後的所有記錄（即最舊的記錄）
            List<ViewHistory> toDelete = allHistories.subList(200, allHistories.size());
            viewHistoryRepository.deleteAll(toDelete);
        }
    }

    /**
     * 獲取用戶的瀏覽歷史
     */
    public List<ViewHistory> getUserHistory(String username) {
        return viewHistoryRepository.findByCustomer_UsernameOrderByViewedAtDesc(username);
    }


    /**
     * 清除瀏覽歷史
     */
    @Transactional
    public void clearHistory(String username, String period) {
        LocalDateTime cutoffDate = switch (period) {
            case "1day" -> LocalDateTime.now().minusDays(1);
            case "1week" -> LocalDateTime.now().minusWeeks(1);
            case "1month" -> LocalDateTime.now().minusMonths(1);
            case "all" -> null; // 全部刪除
            default -> throw new IllegalArgumentException("無效的時間範圍: " + period);
        };

        if ("all".equals(period)) {
            viewHistoryRepository.deleteAllForUser(username);
        } else {
            //  【修改處】：調用新的方法名，刪除「最近」的記錄
            viewHistoryRepository.deleteRecentForUser(username, cutoffDate);
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
}