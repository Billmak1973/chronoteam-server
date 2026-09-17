package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryManagementService {

    private final ProductRepository productRepository;
    private final StoreInventoryRepository storeInventoryRepository;
    private final InventoryAdjustmentLogRepository adjustmentLogRepository;
    private final UserRepository userRepository;
    private final OfflineStoreRepository offlineStoreRepository;

    /**
     * 手動調整線上總倉庫存
     * @param productId 商品ID
     * @param newQuantity 調整後的目標數量
     * @param reason 調整原因
     * @param operatorUsername 操作人用戶名 (從 SecurityContext 獲取)
     */
    @Transactional
    public void adjustOnlineStock(Integer productId, Integer newQuantity, String reason, String operatorUsername) {
        // 1. 先查出舊數據 (必須在任何更新操作之前！此時數據庫裡還是舊值)
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new RuntimeException("操作人不存在"));

        // 2. 獲取舊庫存 (此時拿到的是真正的 previousQuantity)
        Integer previousQuantity = product.getStock();
        Integer changeQuantity = newQuantity - previousQuantity;

        // 3. 記錄日誌
        InventoryAdjustmentLog log = new InventoryAdjustmentLog();
        log.setProduct(product);
        log.setOperator(operator);
        log.setWarehouseType(InventoryAdjustmentLog.WarehouseType.ONLINE);
        log.setStore(null); // 線上總倉無門店關聯
        log.setPreviousQuantity(previousQuantity);
        log.setNewQuantity(newQuantity);
        log.setChangeQuantity(changeQuantity);
        log.setReason(reason);

        // 【新增】：設置調整類型為「手動盤點調整」
        log.setAdjustmentType(InventoryAdjustmentLog.AdjustmentType.MANUAL_ADJUST);

        adjustmentLogRepository.save(log);

        // 4. 【核心修復】：在這裡真正更新庫存並保存到數據庫！
        product.setStock(newQuantity);
        productRepository.save(product);
    }

    /**
     * 手動調整線下門店庫存
     * @param storeId 門店ID
     * @param productId 商品ID
     * @param newQuantity 調整後的目標數量
     * @param reason 調整原因
     * @param operatorUsername 操作人用戶名
     */
    @Transactional
    public void adjustOfflineStock(Long storeId, Integer productId, Integer newQuantity, String reason, String operatorUsername) {
        OfflineStore store = offlineStoreRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("門店不存在"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new RuntimeException("操作人不存在"));

        // 查找該門店的該商品庫存記錄，若無則創建
        StoreInventory storeInv = storeInventoryRepository.findByStoreAndProduct(store, product)
                .orElseGet(() -> {
                    StoreInventory newInv = new StoreInventory();
                    newInv.setStore(store);
                    newInv.setProduct(product);
                    newInv.setQuantity(0);
                    return newInv;
                });

        Integer previousQuantity = storeInv.getQuantity();
        Integer changeQuantity = newQuantity - previousQuantity;

        // 1. 更新門店庫存
        storeInv.setQuantity(newQuantity);
        storeInventoryRepository.save(storeInv);

        // 2. 記錄日誌
        InventoryAdjustmentLog log = new InventoryAdjustmentLog();
        log.setProduct(product);
        log.setOperator(operator);
        log.setWarehouseType(InventoryAdjustmentLog.WarehouseType.OFFLINE);
        log.setStore(store);
        log.setPreviousQuantity(previousQuantity);
        log.setNewQuantity(newQuantity);
        log.setChangeQuantity(changeQuantity);
        log.setReason(reason);

        // 【新增】：設置調整類型為「手動盤點調整」
        log.setAdjustmentType(InventoryAdjustmentLog.AdjustmentType.MANUAL_ADJUST);

        adjustmentLogRepository.save(log);
    }

    /**
     * 線上總倉轉移至線下門店
     */
    @Transactional
    public void transferToStore(Integer productId, Long storeId, Integer quantity, String reason, String operatorUsername) {
        if (quantity == null || quantity <= 0) {
            throw new RuntimeException("轉移數量必須大於0");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        OfflineStore store = offlineStoreRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("門店不存在"));
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new RuntimeException("操作人不存在"));

        // 1. 檢查線上總倉庫存是否充足 (防超賣)
        if (product.getStock() < quantity) {
            throw new RuntimeException("線上總倉庫存不足，當前庫存: " + product.getStock());
        }

        // 2. 【核心解答】查找或創建門店庫存記錄
        // 如果門店已有該商品 -> 返回現有記錄 (後續執行 UPDATE)
        // 如果門店沒有該商品 -> 執行 orElseGet 內的邏輯，創建新記錄 (後續執行 INSERT)
        StoreInventory storeInv = storeInventoryRepository.findByStoreAndProduct(store, product)
                .orElseGet(() -> {
                    StoreInventory newInv = new StoreInventory();
                    newInv.setStore(store);
                    newInv.setProduct(product);
                    newInv.setQuantity(0); // 初始為 0
                    return newInv;
                });

        Integer previousOnlineStock = product.getStock();
        Integer previousStoreStock = storeInv.getQuantity();

        // 3. 執行庫存變更計算
        Integer newOnlineStock = previousOnlineStock - quantity;
        Integer newStoreStock = previousStoreStock + quantity;

        // 4. 保存變更
        product.setStock(newOnlineStock);
        productRepository.save(product);

        storeInv.setQuantity(newStoreStock);
        storeInventoryRepository.save(storeInv);

        // 5. 記錄審計日誌 (使用同一個 batchId 關聯，方便日後對賬)
        String batchId = java.util.UUID.randomUUID().toString();
        String finalReason = (reason != null && !reason.trim().isEmpty()) ? reason : "線上總倉調撥至門店";

        // 5.1 記錄線上總倉出庫日誌
        InventoryAdjustmentLog outLog = new InventoryAdjustmentLog();
        outLog.setProduct(product);
        outLog.setOperator(operator);
        outLog.setWarehouseType(InventoryAdjustmentLog.WarehouseType.ONLINE);
        outLog.setStore(null);
        outLog.setPreviousQuantity(previousOnlineStock);
        outLog.setNewQuantity(newOnlineStock);
        outLog.setChangeQuantity(-quantity); // 負數
        outLog.setReason(finalReason + " (出庫)");
        outLog.setAdjustmentType(InventoryAdjustmentLog.AdjustmentType.TRANSFER_OUT);
        outLog.setTransferBatchId(batchId);
        adjustmentLogRepository.save(outLog);

        // 5.2 記錄線下門店入庫日誌
        InventoryAdjustmentLog inLog = new InventoryAdjustmentLog();
        inLog.setProduct(product);
        inLog.setOperator(operator);
        inLog.setWarehouseType(InventoryAdjustmentLog.WarehouseType.OFFLINE); // 注意：這裡應使用你實體類中定義的枚舉，如 InventoryAdjustmentLog.WarehouseType.OFFLINE
        inLog.setStore(store);
        inLog.setPreviousQuantity(previousStoreStock);
        inLog.setNewQuantity(newStoreStock);
        inLog.setChangeQuantity(quantity); // 正數
        inLog.setReason(finalReason + " (入庫)");
        inLog.setAdjustmentType(InventoryAdjustmentLog.AdjustmentType.TRANSFER_IN);
        inLog.setTransferBatchId(batchId);
        adjustmentLogRepository.save(inLog);
    }

    /**
     * 門店庫存調撥轉移 (門店 -> 其他門店 或 門店 -> 線上總倉)
     */
    @Transactional
    public void transferFromStore(Long sourceInventoryId, Integer productId, Long sourceStoreId,
                                  String targetType, Long targetStoreId, Integer quantity, String reason, String operatorUsername) {
        if (quantity == null || quantity <= 0) {
            throw new RuntimeException("調撥數量必須大於 0");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        OfflineStore sourceStore = offlineStoreRepository.findById(sourceStoreId)
                .orElseThrow(() -> new RuntimeException("源門店不存在"));
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new RuntimeException("操作人不存在"));

        // 1. 獲取並校驗源門店庫存
        StoreInventory sourceInv = storeInventoryRepository.findById(sourceInventoryId)
                .orElseThrow(() -> new RuntimeException("源門店庫存記錄不存在"));

        if (sourceInv.getQuantity() < quantity) {
            throw new RuntimeException("源門店庫存不足，當前庫存: " + sourceInv.getQuantity());
        }

        // 2. 生成唯一的調撥批次號 (用於關聯出庫與入庫日誌)
        String batchId = UUID.randomUUID().toString();
        String finalReason = (reason != null && !reason.trim().isEmpty()) ? reason : "門店庫存調撥";

        Integer previousSourceQty = sourceInv.getQuantity();
        Integer newSourceQty = previousSourceQty - quantity;

        // 3. 扣減源門店庫存並記錄 TRANSFER_OUT 日誌
        sourceInv.setQuantity(newSourceQty);
        storeInventoryRepository.save(sourceInv);

        InventoryAdjustmentLog outLog = new InventoryAdjustmentLog();
        outLog.setProduct(product);
        outLog.setOperator(operator);
        outLog.setWarehouseType(InventoryAdjustmentLog.WarehouseType.OFFLINE);
        outLog.setStore(sourceStore);
        outLog.setPreviousQuantity(previousSourceQty);
        outLog.setNewQuantity(newSourceQty);
        outLog.setChangeQuantity(-quantity); // 負數表示出庫
        outLog.setReason(finalReason + " (門店調撥出庫)");
        outLog.setAdjustmentType(InventoryAdjustmentLog.AdjustmentType.TRANSFER_OUT);
        outLog.setTransferBatchId(batchId);
        adjustmentLogRepository.save(outLog);

        // 4. 根據目標類型，增加目標倉庫庫存並記錄 TRANSFER_IN 日誌
        if ("ONLINE".equals(targetType)) {
            // 目標為線上總倉
            Integer previousOnlineQty = product.getStock() != null ? product.getStock() : 0;
            Integer newOnlineQty = previousOnlineQty + quantity;

            product.setStock(newOnlineQty);
            productRepository.save(product);

            InventoryAdjustmentLog inLog = new InventoryAdjustmentLog();
            inLog.setProduct(product);
            inLog.setOperator(operator);
            inLog.setWarehouseType(InventoryAdjustmentLog.WarehouseType.ONLINE);
            inLog.setStore(null); // 線上總倉無門店關聯
            inLog.setPreviousQuantity(previousOnlineQty);
            inLog.setNewQuantity(newOnlineQty);
            inLog.setChangeQuantity(quantity); // 正數表示入庫
            inLog.setReason(finalReason + " (調撥入庫至線上總倉)");
            inLog.setAdjustmentType(InventoryAdjustmentLog.AdjustmentType.TRANSFER_IN);
            inLog.setTransferBatchId(batchId);
            adjustmentLogRepository.save(inLog);

        } else if ("OFFLINE".equals(targetType)) {
            // 目標為其他線下門店
            if (targetStoreId == null) {
                throw new RuntimeException("目標倉庫為線下門店時，必須指定目標門店ID");
            }
            if (targetStoreId.equals(sourceStoreId)) {
                throw new RuntimeException("不能將庫存調撥至同一個門店");
            }

            OfflineStore targetStore = offlineStoreRepository.findById(targetStoreId)
                    .orElseThrow(() -> new RuntimeException("目標門店不存在"));

            // 查找或創建目標門店的庫存記錄
            StoreInventory targetInv = storeInventoryRepository.findByStoreAndProduct(targetStore, product)
                    .orElseGet(() -> {
                        StoreInventory newInv = new StoreInventory();
                        newInv.setStore(targetStore);
                        newInv.setProduct(product);
                        newInv.setQuantity(0);
                        return newInv;
                    });

            Integer previousTargetQty = targetInv.getQuantity();
            Integer newTargetQty = previousTargetQty + quantity;

            targetInv.setQuantity(newTargetQty);
            storeInventoryRepository.save(targetInv);

            InventoryAdjustmentLog inLog = new InventoryAdjustmentLog();
            inLog.setProduct(product);
            inLog.setOperator(operator);
            inLog.setWarehouseType(InventoryAdjustmentLog.WarehouseType.OFFLINE);
            inLog.setStore(targetStore);
            inLog.setPreviousQuantity(previousTargetQty);
            inLog.setNewQuantity(newTargetQty);
            inLog.setChangeQuantity(quantity); // 正數表示入庫
            inLog.setReason(finalReason + " (調撥入庫至門店: " + targetStore.getName() + ")");
            inLog.setAdjustmentType(InventoryAdjustmentLog.AdjustmentType.TRANSFER_IN);
            inLog.setTransferBatchId(batchId);
            adjustmentLogRepository.save(inLog);

        } else {
            throw new RuntimeException("無效的目標倉庫類型: " + targetType);
        }
}
}