package org.example.website.repository;

import org.example.website.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporter_UsernameAndReviewId(String reporterUsername, Long reviewId);

    /**
     * 【新增】支持根據舉報人和被舉報人進行動態篩選的分頁查詢
     * 如果傳入的參數為 null 或空字符串，則忽略該條件
     */
    @Query("SELECT r FROM Report r WHERE " +
            "(:reporterUsername IS NULL OR :reporterUsername = '' OR r.reporter.username = :reporterUsername) AND " +
            "(:reportedUsername IS NULL OR :reportedUsername = '' OR r.reportedUser.username = :reportedUsername)")
    Page<Report> findByFilters(@Param("reporterUsername") String reporterUsername,
                               @Param("reportedUsername") String reportedUsername,
                               Pageable pageable);

}