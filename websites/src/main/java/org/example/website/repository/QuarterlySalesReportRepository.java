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
     * 根據年份、季度、商品ID、單價、銷售渠道及門店ID查找現有的季度銷售記錄。
     * <p>
     * 【核心業務邏輯】：
     * 1. <b>單價快照機制</b>：同一商品若單價發生改變，會生成一條新的記錄，絕不合併。
     * 2. <b>渠道與門店隔離</b>：線上銷售 (ONLINE/null) 與線下銷售 (OFFLINE/具體門店) 即使單價相同，也會視為不同的統計維度，分別記錄。
     * <br>
     * 此方法用於判斷當前季度、該商品、該單價、該渠道及該門店的記錄是否已存在，
     * 以決定是執行「累加銷量」還是「新建記錄」。
     * </p>
     *
     * @param year          統計年份 (例如: 2024)
     * @param quarter       統計季度 (1, 2, 3, 4)
     * @param productId     商品唯一 ID
     * @param unitPrice     成交單價 (精確到小數點後兩位)
     * @param salesChannel  銷售渠道 ("ONLINE" 或 "OFFLINE")
     * @param storeId       門店 ID (線上訂單通常為 null，線下訂單為具體門店 ID)
     * @return 包含匹配記錄的 {@link Optional}，若無匹配記錄則返回 {@link Optional#empty()}
     */
    Optional<QuarterlySalesReport> findByYearAndQuarterAndProductIdAndUnitPriceAndSalesChannelAndStoreId(
            Integer year,
            Integer quarter,
            Integer productId,
            BigDecimal unitPrice,
            String salesChannel,
            Long storeId
    );
}