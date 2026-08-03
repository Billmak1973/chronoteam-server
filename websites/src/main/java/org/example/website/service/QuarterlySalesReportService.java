package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.QuarterlySalesReport;
import org.example.website.repository.QuarterlySalesReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class QuarterlySalesReportService {

    private final QuarterlySalesReportRepository reportRepository;

    /**
     * 記錄銷售數據到季度報表中
     *
     * @param productId   商品ID
     * @param productName 商品名稱快照
     * @param brand       品牌快照
     * @param unitPrice   成交單價
     * @param quantity    本次銷售數量
     * @param totalAmount 本次成交總額 (unitPrice * quantity)
     */
    @Transactional
    public void recordSale(Integer productId, String productName, String brand,
                           BigDecimal unitPrice, Integer quantity, BigDecimal totalAmount) {

        // 1. 獲取當前時間並計算年份和季度
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        // 核心邏輯：1-3月 -> Q1, 4-6月 -> Q2, 7-9月 -> Q3, 10-12月 -> Q4
        int quarter = (month - 1) / 3 + 1;

        // 2. 嘗試查找是否已存在完全一致的記錄 (年份 + 季度 + 商品ID + 單價)
        Optional<QuarterlySalesReport> existingReportOpt = reportRepository
                .findByYearAndQuarterAndProductIdAndUnitPrice(year, quarter, productId, unitPrice);

        if (existingReportOpt.isPresent()) {
            // 3. 【條件一致】：累加數量和總金額
            QuarterlySalesReport report = existingReportOpt.get();
            report.setQuantity(report.getQuantity() + quantity);
            report.setTotalAmount(report.getTotalAmount().add(totalAmount));
            reportRepository.save(report);
        } else {
            // 4. 【條件不一致/不存在】：新建一條記錄
            QuarterlySalesReport newReport = new QuarterlySalesReport();
            newReport.setYear(year);
            newReport.setQuarter(quarter);
            newReport.setProductId(productId);
            newReport.setProductName(productName);
            newReport.setBrand(brand);
            newReport.setUnitPrice(unitPrice);
            newReport.setQuantity(quantity);
            newReport.setTotalAmount(totalAmount);
            // refundQuantity 和 refundAmount 實體類中已有默認值 0，無需手動設置

            reportRepository.save(newReport);
        }
    }
}