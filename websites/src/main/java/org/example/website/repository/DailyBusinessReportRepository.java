package org.example.website.repository;

import org.example.website.entity.DailyBusinessReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 每日業務報表數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link DailyBusinessReport} 實體的自定義查詢與原子更新方法。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface DailyBusinessReportRepository extends JpaRepository<DailyBusinessReport, Long> {

    /**
     * 檢查指定日期的業務報表記錄是否已經存在。
     * <p>
     * 通常用於在創建新報表前進行防重校驗，避免重複插入同一天的數據。
     * </p>
     *
     * @param reportDate 需要檢查的報表日期 ({@link LocalDate})
     * @return 若該日期已存在報表記錄則返回 true，否則返回 false
     */
    boolean existsByReportDate(LocalDate reportDate);

    /**
     * 原子性更新：將指定日期的「新註冊用戶數 (newUsers)」字段加 1。
     * <p>
     * 使用 JPQL 的 UPDATE 語句直接在數據庫層面執行加法操作，避免先查詢後更新帶來的併發問題。
     * 注意：調用此方法時，上層服務必須加上 {@link Transactional} 註解以確保事務正確提交。
     * </p>
     *
     * @param reportDate 需要更新的報表日期 ({@link LocalDate})
     * @return 受影響的行數（若當天已有記錄則返回 1，若無記錄則返回 0）
     */
    @Modifying
    @Query("UPDATE DailyBusinessReport d SET d.newUsers = d.newUsers + 1 WHERE d.reportDate = :reportDate")
    int incrementNewUsers(@Param("reportDate") LocalDate reportDate);

    /**
     * 根據指定日期查詢當天的業務報表詳細記錄。
     * <p>
     * 通常用於獲取當天的 GMV、訂單數、退款等統計數據，或用於判斷是否需要初始化當天的報表實體。
     * </p>
     *
     * @param reportDate 需要查詢的報表日期 ({@link LocalDate})
     * @return 包含該日期報表記錄的 {@link Optional}，若無記錄則返回 {@link Optional#empty()}
     */
    Optional<DailyBusinessReport> findByReportDate(LocalDate reportDate);
}