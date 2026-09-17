package org.example.website.repository;

import org.example.website.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 舉報記錄數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，用於管理用戶提交的違規舉報記錄。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 檢查指定舉報人是否已經舉報過特定的評論。
     * <p>
     * 用於在提交舉報前進行防重複校驗，防止用戶惡意刷舉報。
     * </p>
     *
     * @param reporterUsername 舉報人用戶名
     * @param reviewId         被舉報的評論 ID
     * @return 若已存在該舉報記錄則返回 true，否則返回 false
     */
    boolean existsByReporter_UsernameAndReviewId(String reporterUsername, Long reviewId);

    /**
     * 支持根據舉報人和被舉報人進行動態篩選的分頁查詢。
     * <p>
     * 【動態查詢邏輯】：
     * 如果傳入的參數為 null 或空字符串，則在 JPQL 中忽略該條件，實現靈活的後台管理篩選。
     * </p>
     *
     * @param reporterUsername  舉報人用戶名 (可選)
     * @param reportedUsername  被舉報人用戶名 (可選)
     * @param pageable          Spring Data 的分頁與排序參數對象
     * @return 符合篩選條件的舉報記錄分頁結果
     */
    @Query("SELECT r FROM Report r WHERE " +
            "(:reporterUsername IS NULL OR :reporterUsername = '' OR r.reporter.username = :reporterUsername) AND " +
            "(:reportedUsername IS NULL OR :reportedUsername = '' OR r.reportedUser.username = :reportedUsername)")
    Page<Report> findByFilters(@Param("reporterUsername") String reporterUsername,
                               @Param("reportedUsername") String reportedUsername,
                               Pageable pageable);
}