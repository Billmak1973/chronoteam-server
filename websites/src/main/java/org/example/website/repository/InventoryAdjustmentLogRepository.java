package org.example.website.repository;

import org.example.website.entity.InventoryAdjustmentLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface InventoryAdjustmentLogRepository extends JpaRepository<InventoryAdjustmentLog, Long> {



    // 1. 僅按多個調整類型查詢 (用於處理 TRANSFER)
    Page<InventoryAdjustmentLog> findByAdjustmentTypeIn(List<InventoryAdjustmentLog.AdjustmentType> types, Pageable pageable);

    // 2. 按多個調整類型 + 倉庫類型查詢
    Page<InventoryAdjustmentLog> findByAdjustmentTypeInAndWarehouseType(
            List<InventoryAdjustmentLog.AdjustmentType> types,
            InventoryAdjustmentLog.WarehouseType warehouseType,
            Pageable pageable);

    // 3. 僅按單個調整類型查詢
    Page<InventoryAdjustmentLog> findByAdjustmentType(InventoryAdjustmentLog.AdjustmentType adjustmentType, Pageable pageable);

    // 4. 按單個調整類型 + 倉庫類型查詢
    Page<InventoryAdjustmentLog> findByAdjustmentTypeAndWarehouseType(
            InventoryAdjustmentLog.AdjustmentType adjustmentType,
            InventoryAdjustmentLog.WarehouseType warehouseType,
            Pageable pageable);

    // 5. 僅按倉庫類型查詢
    Page<InventoryAdjustmentLog> findByWarehouseType(InventoryAdjustmentLog.WarehouseType warehouseType, Pageable pageable);
}