package org.example.website.repository;

import org.example.website.entity.InventoryAdjustmentLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryAdjustmentLogRepository extends JpaRepository<InventoryAdjustmentLog, Long> {

    // 根據商品 ID 分頁查詢調整記錄 (用於商品詳情頁的庫存歷史)
    Page<InventoryAdjustmentLog> findByProduct_ProductId(Integer productId, Pageable pageable);

    // 根據操作人 ID 分頁查詢 (用於審計員工操作)
    Page<InventoryAdjustmentLog> findByOperator_Id(Long operatorId, Pageable pageable);
}