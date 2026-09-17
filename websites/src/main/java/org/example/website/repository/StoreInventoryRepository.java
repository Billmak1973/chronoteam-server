package org.example.website.repository;

import org.example.website.entity.OfflineStore;
import org.example.website.entity.Product;
import org.example.website.entity.StoreInventory;
import org.example.website.entity.WatchCondition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 門店庫存數據訪問層 (Repository) 介面
 * <p>
 * 繼承 {@link JpaRepository}，用於管理線下實體門店的商品庫存分配與調整。
 * </p>
 *
 * @author ChronoTeam
 */
@Repository
public interface StoreInventoryRepository extends JpaRepository<StoreInventory, Long> {

    /**
     * 查詢特定門店中特定商品的庫存記錄。
     *
     * @param store   門店實體
     * @param product 商品實體
     * @return 包含庫存記錄的 {@link Optional}，若無記錄則返回 {@link Optional#empty()}
     */
    Optional<StoreInventory> findByStoreAndProduct(OfflineStore store, Product product);

    /**
     * 【核心優化】原子扣減門店庫存，防止並發超賣。
     * <p>
     * 使用 JPQL 的 UPDATE 語句直接在數據庫層面執行減法操作。
     * 樂觀鎖核心條件：`AND si.quantity >= :deductQty`，確保只有在庫存充足時才執行更新，
     * 並返回受影響的行數 (1 表示扣減成功，0 表示庫存不足扣減失敗)。
     * </p>
     *
     * @param storeId   門店 ID
     * @param productId 商品 ID
     * @param deductQty 需要扣減的數量
     * @return 受影響的行數 (1 或 0)
     */
    @Modifying
    @Transactional
    @Query("UPDATE StoreInventory si SET si.quantity = si.quantity - :deductQty " +
            "WHERE si.store.storeId = :storeId " +
            "AND si.product.productId = :productId " +
            "AND si.quantity >= :deductQty")
    int deductStoreStock(@Param("storeId") Long storeId,
                         @Param("productId") Integer productId,
                         @Param("deductQty") Integer deductQty);

    /**
     * 根據門店 ID 和商品 ID 查詢庫存記錄。
     *
     * @param storeId   門店 ID
     * @param productId 商品 ID
     * @return 包含庫存記錄的 {@link Optional}
     */
    Optional<StoreInventory> findByStore_StoreIdAndProduct_ProductId(Long storeId, Integer productId);

    /**
     * 根據多條件動態篩選門店庫存，支持分頁查詢。
     * <p>
     * 通常用於後台管理系統的「門店庫存管理」頁面，允許管理員按門店、品牌、分類和成色進行組合篩選。
     * </p>
     *
     * @param storeId   門店 ID (可選，為 null 時忽略此條件)
     * @param brand     品牌名稱 (可選，為 null 時忽略此條件)
     * @param category  商品分類 (可選，為 null 時忽略此條件)
     * @param condition 手錶成色枚舉 (可選，為 null 時忽略此條件)
     * @param pageable  Spring Data 的分頁與排序參數對象
     * @return 符合篩選條件的門店庫存分頁結果
     */
    @Query("SELECT si FROM StoreInventory si " +
            "JOIN si.store s " +
            "JOIN si.product p " +
            "WHERE (:storeId IS NULL OR s.storeId = :storeId) " +
            "AND (:brand IS NULL OR p.brand = :brand) " +
            "AND (:category IS NULL OR p.category = :category) " +
            "AND (:condition IS NULL OR p.condition = :condition)")
    Page<StoreInventory> findWithFilters(
            @Param("storeId") Long storeId,
            @Param("brand") String brand,
            @Param("category") String category,
            @Param("condition") WatchCondition condition,
            Pageable pageable
    );
}