package org.example.website.repository;

import org.example.website.entity.OfflineStore;
import org.example.website.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    // 1. 獲取所有不重複的供應商
    @Query("SELECT DISTINCT p.supplier FROM PurchaseOrder p WHERE p.supplier IS NOT NULL AND p.supplier <> ''")
    List<String> findDistinctSuppliers();

    // 2. 獲取所有曾有進貨記錄的門店
    @Query("SELECT DISTINCT p.store FROM PurchaseOrder p WHERE p.store IS NOT NULL")
    List<OfflineStore> findDistinctStoresInPurchases();

    // 3. 獲取所有操作員用戶名
    @Query("SELECT DISTINCT p.operator.username FROM PurchaseOrder p WHERE p.operator IS NOT NULL")
    List<String> findDistinctOperatorUsernames();

    // 4. 核心：多條件動態篩選查詢
    @Query("SELECT p FROM PurchaseOrder p LEFT JOIN p.operator o " +
            "WHERE (:supplier IS NULL OR p.supplier = :supplier) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:warehouseType IS NULL OR p.warehouseType = :warehouseType) " +
            "AND (:storeId IS NULL OR (p.store IS NOT NULL AND p.store.storeId = :storeId)) " +
            "AND (:isStockedIn IS NULL OR p.isStockedIn = :isStockedIn) " +
            "AND (:operator IS NULL OR o.username = :operator) " +
            "AND (:createStart IS NULL OR p.createdAt >= :createStart) " +
            "AND (:createEnd IS NULL OR p.createdAt <= :createEnd) " +
            "AND (:updateStart IS NULL OR p.updatedAt >= :updateStart) " +
            "AND (:updateEnd IS NULL OR p.updatedAt <= :updateEnd)")
    Page<PurchaseOrder> findWithFilters(
            @Param("supplier") String supplier,
            @Param("status") PurchaseOrder.PurchaseStatus status,
            @Param("warehouseType") PurchaseOrder.WarehouseType warehouseType,
            @Param("storeId") Long storeId,
            @Param("isStockedIn") Boolean isStockedIn,
            @Param("operator") String operator,
            @Param("createStart") LocalDateTime createStart,
            @Param("createEnd") LocalDateTime createEnd,
            @Param("updateStart") LocalDateTime updateStart,
            @Param("updateEnd") LocalDateTime updateEnd,
            Pageable pageable
    );

    // 【核心修復】：使用 CONCAT 函數拼接 %，解決 LIKE 語法錯誤
    @Query("SELECT DISTINCT p.supplier FROM PurchaseOrder p WHERE p.supplier LIKE CONCAT('%', :keyword, '%') ORDER BY p.supplier ASC")
    List<String> findSuppliersByKeyword(@Param("keyword") String keyword);
}