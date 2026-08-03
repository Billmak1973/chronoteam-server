package org.example.website.repository;

import org.example.website.entity.QuarterlySalesReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface QuarterlySalesReportRepository extends JpaRepository<QuarterlySalesReport, Long> {

    /**
     * 根據年份、季度、商品ID和單價查找現有記錄
     */
    Optional<QuarterlySalesReport> findByYearAndQuarterAndProductIdAndUnitPrice(
            Integer year,
            Integer quarter,
            Integer productId,
            BigDecimal unitPrice
    );
}