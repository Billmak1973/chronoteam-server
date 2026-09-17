package org.example.website.repository;

import org.example.website.entity.SellApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /**
     * 根據用戶名和交易模式分頁查詢出售申請記錄，並按創建時間降序排列。
     * <p>
     * 通常用於用戶個人中心篩選特定交易模式（如：平台直接買斷 BUYOUT）的申請記錄。
     * </p>
     *
     * @param username       用戶名
     * @param transactionMode 交易模式枚舉 (例如: {@link SellApplication.TransactionMode#BUYOUT})
     * @param pageable       Spring Data 的分頁與排序參數對象
     * @return 符合條件的出售申請分頁結果
     */
    Page<SellApplication> findByUser_UsernameAndTransactionModeOrderByCreatedAtDesc(
            String username,
            SellApplication.TransactionMode transactionMode,
            Pageable pageable
    );
}