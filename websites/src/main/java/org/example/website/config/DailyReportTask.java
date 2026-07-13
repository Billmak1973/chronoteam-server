package org.example.website.config;

import lombok.extern.slf4j.Slf4j;
import org.example.website.entity.DailyBusinessReport;
import org.example.website.repository.DailyBusinessReportRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component
public class DailyReportTask implements CommandLineRunner {

    private final DailyBusinessReportRepository reportRepository;

    public DailyReportTask(DailyBusinessReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * 觸發點 1：網站啟動時自動執行
     * 實現 CommandLineRunner 接口，Spring Boot 啟動完成後會自動調用 run 方法
     */
    @Override
    public void run(String... args) {
        log.info(" [系統啟動] 正在檢查並初始化今日的每日業務報表...");
        ensureReportExists(LocalDate.now());
    }

    /**
     * 觸發點 2：每天凌晨 00:00:00 自動執行
     * cron 表達式：秒 分 時 日 月 星期
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void scheduledDailyReport() {
        log.info(" [定時任務] 跨日觸發，正在創建今日的每日業務報表...");
        ensureReportExists(LocalDate.now());
    }

    /**
     * 核心邏輯：先檢查，後插入，並加入並發兜底機制
     */
    @Transactional
    public void ensureReportExists(LocalDate date) {
        // 1. 【先檢查】：查詢數據庫中是否已存在該日期的記錄
        if (reportRepository.existsByReportDate(date)) {
            log.info(" [每日報表] 日期為 [{}] 的報表記錄已存在，安全跳過創建。", date);
            return;
        }

        try {
            // 2. 【後插入】：確認不存在後，創建新記錄
            DailyBusinessReport report = new DailyBusinessReport();
            report.setReportDate(date);
            // 實體類中的其他統計字段已經設置了默認值 (0 或 BigDecimal.ZERO)，無需手動賦值
            reportRepository.save(report);
            log.info(" [每日報表] 成功創建日期為 [{}] 的業務報表記錄。", date);

        } catch (DataIntegrityViolationException e) {
            // 3. 【並發兜底】：防止極端情況（如啟動檢查與定時任務同時觸發）導致的唯一索引衝突
            log.warn("⚠ [每日報表] 日期 [{}] 的記錄在並發插入時發生衝突，數據庫已存在該記錄，安全跳過。", date);
        } catch (Exception e) {
            // 4. 【未知錯誤捕獲】：防止數據庫連接異常等問題導致整個 Spring Boot 啟動失敗或定時任務中斷
            log.error(" [每日報表] 創建日期 [{}] 的報表記錄時發生未知錯誤: {}", date, e.getMessage(), e);
        }
    }
}