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
     * 更新每日業務報告（線上支付成功 或 線下取貨完成後調用）
     * 【核心升級】：根據 orderSource 精準區分線上與線下業績
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

        // 1. 更新【總計】數據 (保持向後兼容，總數始終累加)
        //    無論線上線下，【總計】數據始終累加（保證總賬準確）
        report.setTotalGmv(report.getTotalGmv().add(orderAmount));
        report.setTotalOrders(report.getTotalOrders() + 1);
        report.setTotalItemsSold(report.getTotalItemsSold() + totalItemsSold);

        // 2. 【核心新增】：根據訂單來源 (orderSource) 分流統計
        //     第二步：根據 orderSource 進行【明細】分流統計
        if ("OFFLINE".equals(order.getOrderSource())) {
            // 純線下門店開單 / POS機購買
            report.setOfflineGmv(report.getOfflineGmv().add(orderAmount));
            report.setOfflineOrders(report.getOfflineOrders() + 1);
        } else {
            // 線上商城下單 (包含線上選好去線下付款)
            report.setOnlineGmv(report.getOnlineGmv().add(orderAmount));
            report.setOnlineOrders(report.getOnlineOrders() + 1);
        }

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