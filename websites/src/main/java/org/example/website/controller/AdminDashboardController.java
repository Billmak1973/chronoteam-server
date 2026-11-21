package org.example.website.controller;

import org.example.website.entity.DailyBusinessReport;
import org.example.website.entity.OfflineStore;
import org.example.website.entity.SellApplication;
import org.example.website.entity.StoreDailyReport;
import org.example.website.repository.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final SellApplicationRepository sellApplicationRepository;
    private final DailyBusinessReportRepository dailyReportRepo;
    private final StoreDailyReportRepository storeReportRepo;
    private final OrderRepository orderRepo;
    private final OfflineStoreRepository offlineStoreRepository;

    public AdminDashboardController(SellApplicationRepository sellApplicationRepository, DailyBusinessReportRepository dailyReportRepo, StoreDailyReportRepository storeReportRepo, OrderRepository orderRepo, OfflineStoreRepository offlineStoreRepository) {
        this.sellApplicationRepository = sellApplicationRepository;
        this.dailyReportRepo = dailyReportRepo;
        this.storeReportRepo = storeReportRepo;
        this.orderRepo = orderRepo;
        this.offlineStoreRepository = offlineStoreRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
        // ================= 1. 今日總營收與成本統計 =================
        // A. 線上數據 (使用 orElseGet 避免空指針，比 orElse(new Entity()) 更安全高效)
        DailyBusinessReport onlineReport = dailyReportRepo.findByReportDate(today).orElse(null);

        BigDecimal onlineGmv = (onlineReport != null && onlineReport.getOnlineGmv() != null)
                ? onlineReport.getOnlineGmv() : BigDecimal.ZERO;
        BigDecimal onlineRefund = (onlineReport != null && onlineReport.getRefundAmount() != null)
                ? onlineReport.getRefundAmount() : BigDecimal.ZERO;
        BigDecimal onlineAcqCost = (onlineReport != null && onlineReport.getAcquisitionCost() != null)
                ? onlineReport.getAcquisitionCost() : BigDecimal.ZERO;

        // B. 線下門店聚合數據 (需確保 Repository 有對應的 SUM 方法)
        BigDecimal offlineGmv = storeReportRepo.sumStoreGmvByDate(today);
        BigDecimal offlineRefund = storeReportRepo.sumStoreRefundAmountByDate(today);
        BigDecimal offlineAcqCost = storeReportRepo.sumStoreAcquisitionCostByDate(today);

        // C. 匯總計算
        BigDecimal todayTotalGmv = onlineGmv.add(offlineGmv);
        BigDecimal totalRefund = onlineRefund.add(offlineRefund);
        BigDecimal totalAcqCost = onlineAcqCost.add(offlineAcqCost);

        // 【核心】今日毛利潤 = 總營收 - 總退款 - 總收購成本
        BigDecimal todayGrossProfit = todayTotalGmv.subtract(totalRefund).subtract(totalAcqCost);
        boolean isGrossProfitNegative = todayGrossProfit != null && todayGrossProfit.compareTo(BigDecimal.ZERO) < 0;

        // ================= 2. 昨日總營收 & 漲跌幅計算 =================
        DailyBusinessReport yesterdayOnlineReport = dailyReportRepo.findByReportDate(yesterday).orElse(null);
        BigDecimal yOnlineGmv = (yesterdayOnlineReport != null && yesterdayOnlineReport.getOnlineGmv() != null)
                ? yesterdayOnlineReport.getOnlineGmv() : BigDecimal.ZERO;
        BigDecimal yOfflineGmv = storeReportRepo.sumStoreGmvByDate(yesterday);
        BigDecimal yesterdayTotalGmv = yOnlineGmv.add(yOfflineGmv);

        String gmvTrendPercent = "0.00";
        if (yesterdayTotalGmv.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = todayTotalGmv.subtract(yesterdayTotalGmv);
            BigDecimal ratio = diff.divide(yesterdayTotalGmv, 4, RoundingMode.HALF_UP);
            gmvTrendPercent = ratio.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } else if (todayTotalGmv.compareTo(BigDecimal.ZERO) > 0) {
            gmvTrendPercent = "100.00";
        }

        // ================= 3. 獲取各門店今日營收明細 =================
        List<StoreDailyReport> storeReports = storeReportRepo.findByReportDateWithStore(today);

        // ================= 4. 其他指標 =================
        long todayNewApplications = sellApplicationRepository.countByCreatedAtToday(today);
        long pendingAppraisals = sellApplicationRepository.countByStatus(SellApplication.ApplicationStatus.PENDING);

        // ================= 5. 傳遞數據到前端 =================
        model.addAttribute("todayTotalGmv", todayTotalGmv);
        model.addAttribute("todayGrossProfit", todayGrossProfit); // 【新增】傳遞毛利潤
        model.addAttribute("isGrossProfitNegative", isGrossProfitNegative);
        model.addAttribute("gmvTrendPercent", gmvTrendPercent);
        model.addAttribute("onlineGmvToday", onlineGmv);
        model.addAttribute("offlineGmvToday", offlineGmv);
        model.addAttribute("storeReports", storeReports);
        model.addAttribute("todayNewApplications", todayNewApplications);
        model.addAttribute("pendingAppraisals", pendingAppraisals);
        // 準備 Modal 所需的詳細數據
        model.addAttribute("onlineRefund", onlineRefund);
        model.addAttribute("onlineAcqCost", onlineAcqCost);
        model.addAttribute("offlineRefund", offlineRefund);
        model.addAttribute("offlineAcqCost", offlineAcqCost);

        // 判斷是否顯示詳情按鈕：只要總營收 != 毛利潤，就說明有退款或成本發生
        boolean showDetailBtn = todayTotalGmv.compareTo(todayGrossProfit) != 0;
        model.addAttribute("showProfitDetailBtn", showDetailBtn);

        long onlineOrders = orderRepo.countTodayOnlineOrders(startOfDay, endOfDay);
        long offlineOrders = orderRepo.countTodayOfflineOrders(startOfDay, endOfDay);
        long totalOrders = onlineOrders + offlineOrders;

        model.addAttribute("todayOrderCount", totalOrders);
        model.addAttribute("todayOnlineOrders", onlineOrders);
        model.addAttribute("todayStoreOrders", offlineOrders);

        List<OfflineStore> allStores = offlineStoreRepository.findAll();

        // 構建門店申請數列表
        List<Map<String, Object>> newAppsByStore = new ArrayList<>();
        long totalNewApps = 0;

        for (OfflineStore store : allStores) {
            // 統計該門店今日的新增申請數量 因爲StoreId在sellAPPlication是String 別的是Long
            long count = sellApplicationRepository.countByCreatedAtTodayAndStoreId(startOfDay, endOfDay, String.valueOf(store.getStoreId())
            );

            // 【優化】：即使數量為 0 也加入列表，讓管理員能看到哪些門店今日「掛零」
            Map<String, Object> map = new HashMap<>();
            map.put("storeId", store.getStoreId());
            map.put("storeName", store.getName());
            // 可選：標記門店狀態，方便前端顯示灰色或提示
            map.put("isActive", store.getIsActive());
            map.put("count", count);

            newAppsByStore.add(map);
            totalNewApps += count;
        }


        // 【建議】：按數量降序排列，讓有申請的門店排在最前面
        newAppsByStore.sort((a, b) -> Long.compare((long)b.get("count"), (long)a.get("count")));

        model.addAttribute("todayNewApplications", totalNewApps);
        model.addAttribute("newAppsByStore", newAppsByStore); // 傳遞給 Modal 使用

        return "admin/admin-dashboard";
    }
}
