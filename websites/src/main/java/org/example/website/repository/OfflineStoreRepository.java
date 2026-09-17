package org.example.website.repository;

import org.example.website.entity.OfflineStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 線下門店數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository} 以獲得基礎的 CRUD 操作能力，
 * 並提供針對 {@link OfflineStore} 實體的自定義查詢方法。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface OfflineStoreRepository extends JpaRepository<OfflineStore, Long> {

    /**
     * 檢查指定店鋪代碼 (Store Code) 是否已經存在於數據庫中。
     * <p>
     * 通常用於在新增或修改店鋪時進行唯一性校驗，避免重複的店鋪標識。
     * </p>
     *
     * @param storeCode 店鋪的唯一代碼
     * @return 若存在則返回 true，否則返回 false
     */
    boolean existsByStoreCode(String storeCode);

    /**
     * 獲取所有當前處於「顯示中」(isActive = true) 狀態的線下門店列表。
     * <p>
     * 通常用於前台結帳頁面，讓顧客可以選擇可用的自取/線下付款門店。
     * </p>
     *
     * @return 所有活躍門店的列表
     */
    List<OfflineStore> findByIsActiveTrue();
}