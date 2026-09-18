package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.controller.PurchaseOrderController;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final OfflineStoreRepository offlineStoreRepository;

    @Transactional
    public PurchaseOrder createPurchaseOrder(PurchaseOrderController.PurchaseOrderRequest request,
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
}