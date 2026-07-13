package org.example.website.repository;

import org.example.website.entity.DailyBusinessReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DailyBusinessReportRepository extends JpaRepository<DailyBusinessReport, Long> {

    /**
     * 檢查指定日期的報表是否已經存在
     */
    boolean existsByReportDate(LocalDate reportDate);

    /**
     * 原子更新：將指定日期的 newUsers 字段 +1
     * 返回受影響的行數（如果當天沒有記錄，則返回 0）
     */
    @Modifying
    @Query("UPDATE DailyBusinessReport d SET d.newUsers = d.newUsers + 1 WHERE d.reportDate = :reportDate")
    int incrementNewUsers(@Param("reportDate") LocalDate reportDate);

    Optional<DailyBusinessReport> findByReportDate(LocalDate reportDate);

}