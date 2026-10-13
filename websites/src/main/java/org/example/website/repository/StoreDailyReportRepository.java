package org.example.website.repository;

import org.example.website.entity.StoreDailyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface StoreDailyReportRepository extends JpaRepository<StoreDailyReport, Long> {

    /**
     * 根據門店 ID 和日期查找當日的門店報表
     */
    Optional<StoreDailyReport> findByStore_StoreIdAndReportDate(Long storeId, LocalDate reportDate);
}