package org.example.website.repository;

import org.example.website.entity.StoreDailyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoreDailyReportRepository extends JpaRepository<StoreDailyReport, Long> {

    /**
     * 根據門店 ID 和日期查找當日的門店報表
     */
    Optional<StoreDailyReport> findByStore_StoreIdAndReportDate(Long storeId, LocalDate reportDate);

    @Query("SELECT COALESCE(SUM(s.storeGmv),0) FROM StoreDailyReport s WHERE s.reportDate =:date")
    BigDecimal sumStoreGmvByDate(@Param("date") LocalDate date);

    /**
     * 查詢指定日期的所有門店報表，並預加載 OfflineStore 實體以獲取 name
     * 使用 LEFT JOIN FETCH 防止 N+1 查詢問題
     * LEFT JOIN FETCH s.store:在查詢 StoreDailyReport 的同時，請把與之關聯的 OfflineStore 實體也一起查出來，並直接填充到 s.store 屬性中。
     */
    @Query("SELECT s FROM StoreDailyReport s LEFT JOIN FETCH s.store WHERE s.reportDate =:date ORDER BY s.storeGmv DESC")
    List<StoreDailyReport>findByReportDateWithStore(@Param("date") LocalDate date);

    // 聚合所有門店的退款金額
    @Query("SELECT COALESCE(SUM(s.storeRefundAmount), 0) FROM StoreDailyReport s WHERE s.reportDate = :date")
    BigDecimal sumStoreRefundAmountByDate(@Param("date") LocalDate date);

    // 聚合所有門店的收購成本
    @Query("SELECT COALESCE(SUM(s.storeAcquisitionCost), 0) FROM StoreDailyReport s WHERE s.reportDate = :date")
    BigDecimal sumStoreAcquisitionCostByDate(@Param("date") LocalDate date);
}