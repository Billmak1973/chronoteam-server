package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.controller.AdminPurchaseController;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final OfflineStoreRepository offlineStoreRepository;
    private final DailyBusinessReportRepository dailyBusinessReportRepository;
    private final  InventoryManagementService inventoryManagementService;
    @Transactional
    public PurchaseOrder createPurchaseOrder(AdminPurchaseController.PurchaseOrderRequest request,
                                             User operator) {
        // 1. 验证商品是否存在
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("商品不存在，ID: " + request.getProductId()));

        // 2. 创建采购订单实体
        PurchaseOrder order = new PurchaseOrder();
        order.setProduct(product);
        order.setQuantity(request.getQuantity());
        order.setTotalCost(request.getTotalCost());
        order.setSupplier(request.getSupplier());
        order.setRemark(request.getRemark());
        order.setOperator(operator);

        // 3. 设置仓库类型
        try {
            order.setWarehouseType(PurchaseOrder.WarehouseType.valueOf(request.getWarehouseType()));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效的仓库类型: " + request.getWarehouseType());
        }

        // 4. 如果是线下仓库，验证门店
        if (order.getWarehouseType() == PurchaseOrder.WarehouseType.OFFLINE) {
            if (request.getStoreId() == null) {
                throw new RuntimeException("线下仓库必须指定门店ID");
            }
            OfflineStore store = offlineStoreRepository.findById(request.getStoreId())
                    .orElseThrow(() -> new RuntimeException("门店不存在，ID: " + request.getStoreId()));
            order.setStore(store);
        }

        // 5. 设置进货日期（默认为今天）
        order.setPurchaseDate(request.getPurchaseDate() != null
                ? request.getPurchaseDate()
                : LocalDate.now());

        // 6. 设置状态为待入库
        order.setStatus(PurchaseOrder.PurchaseStatus.PENDING);

        // 7. 保存到数据库
        return purchaseOrderRepository.save(order);
    }

    // 編輯採購訂單的方法
    @Transactional
    public PurchaseOrder updatePurchaseOrder(Long purchaseId, AdminPurchaseController.PurchaseOrderRequest request, User operator) {
        // 1. 查找現有訂單
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseId)
                .orElseThrow(() -> new RuntimeException("採購訂單不存在，ID: " + purchaseId));

        // 【新增 2】：核心保護！如果訂單已經是 COMPLETED，禁止修改，防止財務數據重複計算或被篡改
        if (order.getStatus() == PurchaseOrder.PurchaseStatus.COMPLETED) {
            throw new RuntimeException("已完成入庫的採購訂單不可修改。如需調整，請聯繫系統管理員或創建庫存調整日誌。");
        }

        // 記錄修改前的狀態，用於判斷是否剛剛變更為 COMPLETED
        PurchaseOrder.PurchaseStatus oldStatus = order.getStatus();

        // 2. 如果商品ID改變了，驗證新商品是否存在並更新
        if (!order.getProduct().getProductId().equals(request.getProductId())) {
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new RuntimeException("商品不存在，ID: " + request.getProductId()));
            order.setProduct(product);
        }

        // 3. 更新基本字段
        order.setQuantity(request.getQuantity());
        order.setTotalCost(request.getTotalCost());
        order.setSupplier(request.getSupplier());
        order.setRemark(request.getRemark());
        order.setPurchaseDate(request.getPurchaseDate() != null ? request.getPurchaseDate() : LocalDate.now());

        // 4. 設置倉庫類型
        try {
            order.setWarehouseType(PurchaseOrder.WarehouseType.valueOf(request.getWarehouseType()));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("無效的倉庫類型: " + request.getWarehouseType());
        }

        // 5. 如果是線下倉庫，驗證門店；如果是線上，清空門店關聯
        if (order.getWarehouseType() == PurchaseOrder.WarehouseType.OFFLINE) {
            if (request.getStoreId() == null) {
                throw new RuntimeException("線下倉庫必須指定門店ID");
            }
            OfflineStore store = offlineStoreRepository.findById(request.getStoreId())
                    .orElseThrow(() -> new RuntimeException("門店不存在，ID: " + request.getStoreId()));
            order.setStore(store);
        } else {
            order.setStore(null); // 切換回線上總倉時，清空門店關聯
        }

        // 6. 更新狀態
        PurchaseOrder.PurchaseStatus newStatus = order.getStatus(); // 默認保持原狀態
        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            try {
                newStatus = PurchaseOrder.PurchaseStatus.valueOf(request.getStatus());
                order.setStatus(newStatus);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("無效的訂單狀態: " + request.getStatus());
            }
        }

        // 【新增 3】：如果狀態從 PENDING 變更為 COMPLETED，則將成本計入當日的 DailyBusinessReport
        if (newStatus == PurchaseOrder.PurchaseStatus.COMPLETED && oldStatus != PurchaseOrder.PurchaseStatus.COMPLETED) {
            // 獲取當日日期 (財務上通常以實際操作完成日為準，若業務規定按進貨日算，可改為 order.getPurchaseDate())
            LocalDate reportDate = LocalDate.now();

            // 查找或創建當日的業務報表
            DailyBusinessReport report = dailyBusinessReportRepository.findByReportDate(reportDate)
                    .orElseGet(() -> {
                        DailyBusinessReport newReport = new DailyBusinessReport();
                        newReport.setReportDate(reportDate);
                        return newReport;
                    });

            // 累加收購成本 (防 Null 保護)
            BigDecimal currentCost = report.getAcquisitionCost() != null ? report.getAcquisitionCost() : BigDecimal.ZERO;
            BigDecimal orderCost = order.getTotalCost() != null ? order.getTotalCost() : BigDecimal.ZERO;

            report.setAcquisitionCost(currentCost.add(orderCost));

            // 保存報表更新
            dailyBusinessReportRepository.save(report);
        }

        // 7. 保存到數據庫
        return purchaseOrderRepository.save(order);
    }

    /**
     * 處理採購訂單入庫
     */
    @Transactional
    public void processStockIn(Long purchaseId, String operatorUsername) {
        // 1. 查找訂單
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseId)
                .orElseThrow(() -> new RuntimeException("採購訂單不存在，ID: " + purchaseId));

        // 2. 核心校驗：只有 COMPLETED 狀態才能入庫
        if (order.getStatus() != PurchaseOrder.PurchaseStatus.COMPLETED) {
            throw new RuntimeException("只有狀態為「已完成」的訂單才能執行入庫操作！");
        }

        // 3. 防呆校驗：防止重複入庫
        if (Boolean.TRUE.equals(order.getIsStockedIn())) {
            throw new RuntimeException("該訂單已經入庫，請勿重複操作！");
        }

        // 4. 【核心修改】：調用採購入庫方法，並接收返回的 logId
        Long logId = inventoryManagementService.purchaseInStock(order, operatorUsername);

        // 5. 將生成的日誌 ID 關聯到採購訂單
        order.setInventoryRecordId(logId);

        // 6. 標記訂單為已入庫
        order.setIsStockedIn(true);
        purchaseOrderRepository.save(order);
    }
}