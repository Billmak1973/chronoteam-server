package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.DailyBusinessReport;
import org.example.website.entity.Order;
import org.example.website.entity.OrderItem;
import org.example.website.repository.DailyBusinessReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyBusinessReportService {

    private final DailyBusinessReportRepository dailyBusinessReportRepository;

    /**
     * 增加當日新註冊用戶數
     * 注意：這裡不加 @Transactional，讓它跟隨調用者（UserService.register）的事務
     */
    public void incrementNewUsers() {
        LocalDate today = LocalDate.now();

        // 1. 嘗試原子更新今天的記錄
        int updatedRows = dailyBusinessReportRepository.incrementNewUsers(today);

        // 2. 如果更新行數為 0，說明今天還沒有生成報表記錄，需要新建一條
        if (updatedRows == 0) {
            DailyBusinessReport report = new DailyBusinessReport();
            report.setReportDate(today);
            report.setNewUsers(1); // 第一個註冊的用戶
            dailyBusinessReportRepository.save(report);
        }
    }

    /**
     * 更新每日業務報告（線上支付成功 或 線下取貨完成後調用）
     * 【核心修正】：DailyBusinessReport 現在只記錄「全平台總覽」和「純線上業績」。
     *              線下門店業績請參見 StoreDailyReport。
     */
    @Transactional
    public void updateDailyReport(Order order) {
        LocalDate today = LocalDate.now();

        // 查找或創建今日的報告
        DailyBusinessReport report = dailyBusinessReportRepository.findByReportDate(today)
                .orElseGet(() -> {
                    DailyBusinessReport newReport = new DailyBusinessReport();
                    newReport.setReportDate(today);
                    return newReport;
                });

        BigDecimal orderAmount = order.getTotalAmount();
        Integer totalItemsSold = calculateTotalItemsSold(order);

        // 1. 更新【全平台總計】數據 (無論線上還是線下訂單，都計入總賬)
        report.setTotalOrders(report.getTotalOrders() + 1);
        report.setTotalItemsSold(report.getTotalItemsSold() + totalItemsSold);

        // 2. 【核心分流】：只有「純線上訂單」才累加到 online_gmv / online_orders
        //    線下訂單 (OFFLINE) 不再影響 DailyBusinessReport 的 online 字段，也不再有 offline 字段可寫。
        if ("ONLINE".equals(order.getOrderSource())) {
            report.setOnlineGmv(report.getOnlineGmv().add(orderAmount));
            report.setOnlineOrders(report.getOnlineOrders() + 1);
        }
        // 注意：如果是 "OFFLINE" 來源的訂單，這裡什麼都不做，因為線下數據已由 StoreDailyReport 處理。

        // 保存報告
        dailyBusinessReportRepository.save(report);
    }

    /**
     * 計算訂單中的商品總數量
     */
    private Integer calculateTotalItemsSold(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return 0;
        }

        return order.getItems().stream()
                .map(OrderItem::getQuantity)
                .reduce(0, Integer::sum);
    }
}