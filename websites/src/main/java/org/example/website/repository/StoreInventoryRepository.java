package org.example.website.repository;

import org.example.website.entity.OfflineStore;
import org.example.website.entity.Product;
import org.example.website.entity.StoreInventory;
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

@Repository
public interface StoreInventoryRepository extends JpaRepository<StoreInventory, Long> {

    /**
     * 查詢特定門店中特定商品的庫存記錄
     */
    Optional<StoreInventory> findByStoreAndProduct(OfflineStore store, Product product);

    /**
     * 查詢某個商品在所有門店的庫存分布 (用於後台管理或前端顯示「附近門店有貨」)
     */
    List<StoreInventory> findByProduct(Product product);

    /**
     * 【核心】原子扣減門店庫存 (防止並發超賣)
     * 只有當庫存 >= 扣減數量時才執行更新，返回受影響的行數 (1 或 0)
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

    Optional<StoreInventory> findByStore_StoreIdAndProduct_ProductId(Long storeId, Integer productId);

    Page<StoreInventory> findByStore_StoreId(Long storeId, Pageable pageable);

}