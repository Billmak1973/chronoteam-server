package org.example.website.repository;

import org.example.website.entity.SellApplication;
import org.example.website.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 出售申請數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link SellApplication} 實體的自定義查詢方法。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface SellApplicationRepository extends JpaRepository<SellApplication, Long> {

    /**
     * 根據用戶名查詢該用戶的所有出售申請記錄，並按創建時間降序排列。
     * <p>
     * 通常用於用戶個人中心的「我的出售申請」列表展示。
     * </p>
     *
     * @param username 用戶名
     * @return 按創建時間倒序排列的出售申請列表
     */
    List<SellApplication> findByUser_UsernameOrderByCreatedAtDesc(String username);


    List<SellApplication> findByUserAndStatus(User user, SellApplication.ApplicationStatus status);


    /**
     * 獲取指定用戶所有不重複的 storeId (用於填充篩選下拉框)
     */
    @Query("SELECT DISTINCT s.storeId FROM SellApplication s WHERE s.user.username = :username AND s.storeId IS NOT NULL AND s.storeId != ''")
    List<String> findDistinctStoreIdsByUser_Username(@Param("username") String username);

    /**
     * 支持動態篩選的查詢方法 (新增日期與價格篩選)
     */
    @Query("SELECT s FROM SellApplication s WHERE s.user.username = :username " +
            "AND s.transactionMode = :mode " +
            "AND (:storeId IS NULL OR s.storeId = :storeId) " +
            "AND (:status IS NULL OR s.status = :status) " +
            "AND (:completedAtStart IS NULL OR s.completedAt >= :completedAtStart) " +
            "AND (:completedAtEnd IS NULL OR s.completedAt <= :completedAtEnd) " +
            "AND (:finalPriceMode IS NULL OR " +
            "   (:finalPriceMode = 'gt' AND s.finalPrice > :finalPriceVal1) OR " +
            "   (:finalPriceMode = 'lt' AND s.finalPrice < :finalPriceVal1) OR " +
            "   (:finalPriceMode = 'eq' AND s.finalPrice = :finalPriceVal1) OR " +
            "   (:finalPriceMode = 'between' AND s.finalPrice >= :finalPriceVal1 AND s.finalPrice <= :finalPriceVal2)" +
            ") " +
            "ORDER BY s.createdAt DESC")
    Page<SellApplication> findByFilters(
            @Param("username") String username,
            @Param("mode") SellApplication.TransactionMode mode,
            @Param("storeId") String storeId,
            @Param("status") SellApplication.ApplicationStatus status,
            @Param("completedAtStart") java.time.LocalDateTime completedAtStart,
            @Param("completedAtEnd") java.time.LocalDateTime completedAtEnd,
            @Param("finalPriceMode") String finalPriceMode,
            @Param("finalPriceVal1") java.math.BigDecimal finalPriceVal1,
            @Param("finalPriceVal2") java.math.BigDecimal finalPriceVal2,
            org.springframework.data.domain.Pageable pageable
    );

    /**
     * 計算符合當前篩選條件 且 狀態為 COMPLETED 的最終成交總額
     */
    @Query("SELECT COALESCE(SUM(s.finalPrice), 0) FROM SellApplication s WHERE s.user.username = :username " +
            "AND s.transactionMode = :mode " +
            "AND s.status = 'COMPLETED' " +
            "AND (:storeId IS NULL OR s.storeId = :storeId) " +
            "AND (:completedAtStart IS NULL OR s.completedAt >= :completedAtStart) " +
            "AND (:completedAtEnd IS NULL OR s.completedAt <= :completedAtEnd) " +
            "AND (:finalPriceMode IS NULL OR " +
            "   (:finalPriceMode = 'gt' AND s.finalPrice > :finalPriceVal1) OR " +
            "   (:finalPriceMode = 'lt' AND s.finalPrice < :finalPriceVal1) OR " +
            "   (:finalPriceMode = 'eq' AND s.finalPrice = :finalPriceVal1) OR " +
            "   (:finalPriceMode = 'between' AND s.finalPrice >= :finalPriceVal1 AND s.finalPrice <= :finalPriceVal2)" +
            ")")
    java.math.BigDecimal sumCompletedFinalPriceByFilters(
            @Param("username") String username,
            @Param("mode") SellApplication.TransactionMode mode,
            @Param("storeId") String storeId,
            @Param("completedAtStart") java.time.LocalDateTime completedAtStart,
            @Param("completedAtEnd") java.time.LocalDateTime completedAtEnd,
            @Param("finalPriceMode") String finalPriceMode,
            @Param("finalPriceVal1") java.math.BigDecimal finalPriceVal1,
            @Param("finalPriceVal2") java.math.BigDecimal finalPriceVal2
    );

    @Query("SELECT COALESCE(SUM(s.finalPrice), 0) FROM SellApplication s " +
            "WHERE s.user.username = :username " +
            "AND s.transactionMode = :mode " +
            "AND s.status = 'COMPLETED'")
    java.math.BigDecimal sumCompletedFinalPriceByUserAndStatus(
            @Param("username") String username,
            @Param("mode") SellApplication.TransactionMode mode
    );
}