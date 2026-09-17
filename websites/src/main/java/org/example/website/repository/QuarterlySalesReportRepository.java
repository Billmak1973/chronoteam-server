package org.example.website.repository;

import org.example.website.entity.QuarterlySalesReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * 季度銷售報表數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 主要用於財務維度的季度銷售數據統計與快照管理。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface QuarterlySalesReportRepository extends JpaRepository<QuarterlySalesReport, Long> {

    /**
     * 根據年份、季度、商品ID和單價查找現有的季度銷售記錄。
     * <p>
     * 【核心業務邏輯】：
     * 季度報表採用「單價快照」機制。同一商品若單價發生改變，會生成一條新的記錄，絕不合併。
     * 此方法用於判斷當前季度、該商品、該單價的記錄是否已存在，以決定是執行「累加銷量」還是「新建記錄」。
     * </p>
     *
     * @param year      統計年份 (例如: 2024)
     * @param quarter   統計季度 (1, 2, 3, 4)
     * @param productId 商品唯一 ID
     * @param unitPrice 成交單價 (精確到小數點後兩位)
     * @return 包含匹配記錄的 {@link Optional}，若無匹配記錄則返回 {@link Optional#empty()}
     */
    Optional<QuarterlySalesReport> findByYearAndQuarterAndProductIdAndUnitPrice(
            Integer year,
            Integer quarter,
            Integer productId,
            BigDecimal unitPrice
    );
}