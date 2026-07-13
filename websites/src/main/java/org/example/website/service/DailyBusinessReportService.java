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
            // 其他字段（如 totalGmv, totalOrders 等）在實體類中已有默認值 0
            dailyBusinessReportRepository.save(report);
        }
    }

    /**
     * 更新每日業務報告（線上支付成功後調用）
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

        // 1. 更新總營業額/GMV（訂單總金額）
        BigDecimal currentGmv = report.getTotalGmv() != null ? report.getTotalGmv() : BigDecimal.ZERO;
        report.setTotalGmv(currentGmv.add(order.getTotalAmount()));

        // 2. 更新總訂單數
        Integer currentOrders = report.getTotalOrders() != null ? report.getTotalOrders() : 0;
        report.setTotalOrders(currentOrders + 1);

        // 3. 更新總售出件數（統計訂單中所有商品的總數量）
        Integer totalItemsSold = calculateTotalItemsSold(order);
        Integer currentItemsSold = report.getTotalItemsSold() != null ? report.getTotalItemsSold() : 0;
        report.setTotalItemsSold(currentItemsSold + totalItemsSold);

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