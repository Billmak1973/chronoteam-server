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
     * @param productId      商品ID
     * @param productName    商品名稱快照
     * @param brand          品牌快照
     * @param unitPrice      成交單價
     * @param quantity       本次銷售數量
     * @param totalAmount    本次成交總額
     * @param salesChannel   銷售渠道 ("ONLINE" 或 "OFFLINE")
     * @param storeId        門店ID (線上通常為 null)
     */
    @Transactional
    public void recordSale(Integer productId, String productName, String brand,
                           BigDecimal unitPrice, Integer quantity, BigDecimal totalAmount,
                           String salesChannel, Long storeId) {

        // 1. 獲取當前時間並計算年份和季度
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        int quarter = (month - 1) / 3 + 1;

        // 2. 嘗試查找是否已存在完全一致的記錄
        // 【關鍵】：現在必須匹配渠道和門店
        Optional<QuarterlySalesReport> existingReportOpt = reportRepository
                .findByYearAndQuarterAndProductIdAndUnitPriceAndSalesChannelAndStoreId(
                        year, quarter, productId, unitPrice, salesChannel, storeId);

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

            // 【新增字段賦值】
            newReport.setSalesChannel(salesChannel);
            newReport.setStoreId(storeId);

            newReport.setUnitPrice(unitPrice);
            newReport.setQuantity(quantity);
            newReport.setTotalAmount(totalAmount);

            reportRepository.save(newReport);
        }
    }

    /**
     * 【新增】記錄退貨數據到季度報表中
     * 邏輯：查找匹配的記錄，累加 refund_quantity 和 refund_amount
     */
    @Transactional
    public void recordReturn(Integer productId, String productName, String brand,
                             BigDecimal unitPrice, Integer returnQuantity, BigDecimal returnAmount,
                             String salesChannel, Long storeId) {

        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        int quarter = (month - 1) / 3 + 1;

        // 查找匹配的销售记录 (同一季度、商品、單價、渠道、門店)
        Optional<QuarterlySalesReport> existingReportOpt = reportRepository
                .findByYearAndQuarterAndProductIdAndUnitPriceAndSalesChannelAndStoreId(
                        year, quarter, productId, unitPrice, salesChannel, storeId);

        if (existingReportOpt.isPresent()) {
            QuarterlySalesReport report = existingReportOpt.get();

            // 累加退貨數量
            Integer currentRefundQty = report.getRefundQuantity() != null ? report.getRefundQuantity() : 0;
            report.setRefundQuantity(currentRefundQty + returnQuantity);

            // 累加退貨金額
            BigDecimal currentRefundAmt = report.getRefundAmount() != null ? report.getRefundAmount() : BigDecimal.ZERO;
            report.setRefundAmount(currentRefundAmt.add(returnAmount));

            reportRepository.save(report);
        } else {
            // 【邊界情況】：如果找不到對應的銷售記錄（例如數據異常或跨季度退貨），
            // 理論上應該創建一條新的記錄，但 quantity 為 0，只有 refund 數據。
            // 或者根據業務規則拋出異常。這裡選擇創建一條僅含退貨數據的記錄以保證財務平衡。
            QuarterlySalesReport newReport = new QuarterlySalesReport();
            newReport.setYear(year);
            newReport.setQuarter(quarter);
            newReport.setProductId(productId);
            newReport.setProductName(productName);
            newReport.setBrand(brand);
            newReport.setSalesChannel(salesChannel);
            newReport.setStoreId(storeId);
            newReport.setUnitPrice(unitPrice);
            newReport.setQuantity(0); // 銷售數量為 0
            newReport.setTotalAmount(BigDecimal.ZERO); // 銷售總額為 0
            newReport.setRefundQuantity(returnQuantity);
            newReport.setRefundAmount(returnAmount);
            reportRepository.save(newReport);
        }
    }
}