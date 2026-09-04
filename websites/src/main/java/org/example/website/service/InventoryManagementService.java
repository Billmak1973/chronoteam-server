package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new RuntimeException("操作人不存在"));

        Integer previousQuantity = product.getStock();
        Integer changeQuantity = newQuantity - previousQuantity;

        // 1. 記錄日誌
        InventoryAdjustmentLog log = new InventoryAdjustmentLog();
        log.setProduct(product);
        log.setOperator(operator);
        log.setWarehouseType(InventoryAdjustmentLog.WarehouseType.ONLINE);
        log.setStore(null); // 線上總倉無門店關聯
        log.setPreviousQuantity(previousQuantity);
        log.setNewQuantity(newQuantity);
        log.setChangeQuantity(changeQuantity);
        log.setReason(reason);

        adjustmentLogRepository.save(log);
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

        adjustmentLogRepository.save(log);
    }
}