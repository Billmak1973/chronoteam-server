package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.dto.ReturnRequestDTO;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AfterSalesRequestService {

    private final AfterSalesRequestRepository afterSalesRequestRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    private final DailyBusinessReportRepository dailyReportRepository;
    private final StoreDailyReportRepository storeDailyReportRepository;
    private final OfflineStoreRepository offlineStoreRepository;
    private final ProductRepository productRepository; // 【新增】用於創建換貨新訂單
    private final StoreInventoryRepository storeInventoryRepository;
    private final AfterSalesRequestItemRepository afterSalesRequestItemRepository;
    private final InventoryAdjustmentLogRepository adjustmentLogRepository;

    @Transactional
    public AfterSalesRequest createAfterSalesRequest(String username, ReturnRequestDTO dto, AfterSalesRequest.RequestType reqType) {
        // 1. 校驗訂單與用戶
        Order order = orderRepository.findByOrderNoAndUser_Username(dto.getOrderNo(), username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或無權操作"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        // 2. 創建主申請記錄
        AfterSalesRequest request = new AfterSalesRequest();
        request.setRequestType(reqType); // 【核心修改】：設置動態傳入的類型
        request.setOriginalOrder(order);
        request.setUser(user);
        request.setStoreId(String.valueOf(dto.getStoreId()));
        request.setAppointmentDate(dto.getReturnDate());
        request.setAppointmentTimeSlot(dto.getReturnTimeSlot());
        request.setReason(dto.getReason());
        request.setStatus(AfterSalesRequest.RequestStatus.PENDING);

        // 3. 創建明細記錄
        AfterSalesRequestItem requestItem = new AfterSalesRequestItem();
        requestItem.setAfterSalesRequest(request);

        OrderItem targetOrderItem = order.getItems().stream()
                .filter(oi -> oi.getProduct().getProductId().equals(dto.getProductId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("訂單中找不到該商品"));

        requestItem.setOrderItem(targetOrderItem);
        requestItem.setProduct(targetOrderItem.getProduct());
        requestItem.setReturnQuantity(dto.getQuantity());
        // 设置 isStockedIn 默认值为 false
        requestItem.setIsStockedIn(false);
        request.getItems().add(requestItem);

        // 4. 保存並返回
        return afterSalesRequestRepository.save(request);
    }


    @Transactional
    public void updateAfterSalesStatus(Long requestId, AfterSalesRequest.RequestStatus newStatus,
                                       Map<Long, BigDecimal> itemRefundAmounts,
                                       Map<String, Object> exchangeData,
                                       String operatorUsername) {
        AfterSalesRequest request = afterSalesRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("售後申請不存在，ID: " + requestId));

        if (request.getStatus() == newStatus) {
            throw new RuntimeException("狀態未發生改變");
        }

        // ==========================================
        // 【核心修復】：將記錄操作員的邏輯移到 if (COMPLETED) 的外面！
        // 這樣無論是變成 COMPLETED, REJECTED 還是 CANCELLED，都會記錄當前操作員
        // ==========================================
        if (operatorUsername != null && !operatorUsername.trim().isEmpty()) {
            User operatorUser = userRepository.findByUsername(operatorUsername)
                    .orElseThrow(() -> new RuntimeException("操作員不存在: " + operatorUsername));
            request.setOperator(operatorUser);
        }

        if (newStatus == AfterSalesRequest.RequestStatus.COMPLETED) {
            BigDecimal totalRefund = BigDecimal.ZERO;

            // 1. 更新明細的最終結算金額（舊商品退款金額），並計算總退款
            for (AfterSalesRequestItem item : request.getItems()) {
                BigDecimal settleAmount = itemRefundAmounts.getOrDefault(item.getRequestItemId(), BigDecimal.ZERO);
                item.setFinalSettleAmount(settleAmount);
                totalRefund = totalRefund.add(settleAmount);
            }
            request.setTotalRefundAmount(totalRefund);
            request.setCompletedAt(LocalDateTime.now());

            // 2. 觸發原訂單狀態的全局同步 (處理原訂單的退貨狀態)
            syncOrderStatusAfterSalesCompletion(request.getOriginalOrder(),request);

            // 3. 【核心修正】：換貨 = 退貨 + 購物。分別處理財務報表與新訂單生成
            if (request.getRequestType() == AfterSalesRequest.RequestType.RETURN) {
                updateDailyReportForReturn(request);
                if (request.getStoreId() != null && !request.getStoreId().trim().isEmpty()) {
                    updateStoreDailyReportForReturn(request);
                }
            } else if (request.getRequestType() == AfterSalesRequest.RequestType.EXCHANGE) {
                // 【核心】：換貨包含「購物」流程，必須生成一筆全新的線下訂單來代表這次購買！
                Order newOrder = createExchangePurchaseOrder(request, exchangeData);
                request.setNewOrder(newOrder); // 關聯新訂單到售後申請

                // 更新財務報表：記錄「舊商品退貨」 + 「新商品購物(GMV)」
                updateDailyReportForExchange(request, newOrder);
                if (request.getStoreId() != null && !request.getStoreId().trim().isEmpty()) {
                    updateStoreDailyReportForExchange(request, newOrder);
                }
            }
        }

        request.setStatus(newStatus);
        afterSalesRequestRepository.save(request);
    }

    /**
     * 【核心新增】：為換貨創建一筆全新的「線下購物訂單」
     * 這代表顧客在店裡完成了「購物」的動作，財務上將計入線下 GMV。
     */
    private Order createExchangePurchaseOrder(AfterSalesRequest request, Map<String, Object> exchangeData) {
        if (exchangeData == null || !exchangeData.containsKey("productId")) {
            throw new RuntimeException("換貨必須提供新商品資訊 (productId, quantity, totalPrice)");
        }

        Integer newProductId = Integer.valueOf(exchangeData.get("productId").toString());
        Integer newQuantity = Integer.valueOf(exchangeData.get("quantity").toString());
        BigDecimal newTotalPrice = new BigDecimal(exchangeData.get("totalPrice").toString());

        Product newProduct = productRepository.findById(newProductId)
                .orElseThrow(() -> new RuntimeException("換貨的新商品不存在，ID: " + newProductId));

        // 1. 創建新訂單實體
        Order newOrder = new Order();
        newOrder.setOrderNo("EXCH-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        newOrder.setUser(request.getUser());

        // 【關鍵】：標記為純線下購物訂單
        newOrder.setOrderSource("OFFLINE");
        newOrder.setPaymentMethod("OFFLINE_STORE"); // 標記為線下店鋪支付 (或 EXCHANGE_PURCHASE)
        newOrder.setPaymentStatus(Order.PaymentStatus.PAID_OFFLINE); // 換貨當場結清
        newOrder.setStatus(Order.OrderStatus.COMPLETED); // 換貨購物直接視為已完成

        newOrder.setTotalAmount(newTotalPrice); // 新商品的總價
        newOrder.setShippingFee(BigDecimal.ZERO);
        newOrder.setDeliveryMethod("OFFLINE_DIRECT");
        newOrder.setDelivery(false);

        // 關聯門店 (若有)
        if (request.getStoreId() != null) {
            OfflineStore store = offlineStoreRepository.findById(Long.parseLong(request.getStoreId())).orElse(null);
            newOrder.setOfflineStore(store);
        }

        newOrder.setPaidAt(LocalDateTime.now());
        newOrder.setReceivedAt(LocalDateTime.now()); // 當場帶走
        newOrder.setIsVisible(true);

        // 2. 創建訂單明細 (OrderItem)
        OrderItem newItem = new OrderItem();
        newItem.setOrder(newOrder);
        newItem.setProduct(newProduct);
        newItem.setQuantity(newQuantity);
        newItem.setPrice(newProduct.getPrice()); // 使用商品當前單價
        newOrder.getItems().add(newItem);

        // 3. 保存新訂單 (因 Order 配置了 cascade = CascadeType.ALL，會自動級聯保存 OrderItem)
        return orderRepository.save(newOrder);
        // 注意：此新訂單的 GMV 統計將在下面的 updateDailyReportForExchange 中統一處理
    }
    private void updateDailyReportForExchange(AfterSalesRequest request, Order newOrder) {
        LocalDate reportDate = request.getCompletedAt().toLocalDate();
        DailyBusinessReport report = dailyReportRepository.findByReportDate(reportDate)
                .orElseGet(() -> {
                    DailyBusinessReport newReport = new DailyBusinessReport();
                    newReport.setReportDate(reportDate);
                    return newReport;
                });

        // A. 記錄「舊商品退貨」部分
        BigDecimal refundAmount = request.getTotalRefundAmount() != null ? request.getTotalRefundAmount() : BigDecimal.ZERO;
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentRefund = report.getRefundAmount() != null ? report.getRefundAmount() : BigDecimal.ZERO;
            report.setRefundAmount(currentRefund.add(refundAmount));

            int totalItems = request.getItems().stream().mapToInt(AfterSalesRequestItem::getReturnQuantity).sum();
            report.setRefundCount(report.getRefundCount() + totalItems);
        }

        // B. 記錄「新商品購物」部分 (透過新生成的線下訂單)
        if (newOrder != null) {
            // 1. 計入總 GMV 和總訂單數
            BigDecimal currentGmv = report.getTotalGmv() != null ? report.getTotalGmv() : BigDecimal.ZERO;
            report.setTotalGmv(currentGmv.add(newOrder.getTotalAmount()));

            Integer currentOrders = report.getTotalOrders() != null ? report.getTotalOrders() : 0;
            report.setTotalOrders(currentOrders + 1);

            // 2. 因為是線下訂單，同時累加線下專屬欄位
            BigDecimal currentOfflineGmv = report.getOfflineGmv() != null ? report.getOfflineGmv() : BigDecimal.ZERO;
            report.setOfflineGmv(currentOfflineGmv.add(newOrder.getTotalAmount()));

            Integer currentOfflineOrders = report.getOfflineOrders() != null ? report.getOfflineOrders() : 0;
            report.setOfflineOrders(currentOfflineOrders + 1);
        }

        dailyReportRepository.save(report);
    }

    /**
     * 【核心修正】：更新【門店】每日業務報表中的「換貨」財務數據
     * 邏輯：1. 記錄舊商品退回 (storeRefundAmount)  2. 記錄新商品賣出 (storeGmv)
     */
    private void updateStoreDailyReportForExchange(AfterSalesRequest request, Order newOrder) {
        try {
            Long storeId = Long.parseLong(request.getStoreId());
            LocalDate reportDate = request.getCompletedAt().toLocalDate();

            OfflineStore store = offlineStoreRepository.findById(storeId)
                    .orElseThrow(() -> new RuntimeException("關聯的門店不存在，ID: " + storeId));

            StoreDailyReport storeReport = storeDailyReportRepository.findByStore_StoreIdAndReportDate(storeId, reportDate)
                    .orElseGet(() -> {
                        StoreDailyReport newReport = new StoreDailyReport();
                        newReport.setStore(store);
                        newReport.setReportDate(reportDate);
                        return newReport;
                    });

            // A. 記錄「舊商品退貨」部分
            BigDecimal refundAmount = request.getTotalRefundAmount() != null ? request.getTotalRefundAmount() : BigDecimal.ZERO;
            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentRefund = storeReport.getStoreRefundAmount() != null ? storeReport.getStoreRefundAmount() : BigDecimal.ZERO;
                storeReport.setStoreRefundAmount(currentRefund.add(refundAmount));

                int totalReturnItems = request.getItems().stream().mapToInt(AfterSalesRequestItem::getReturnQuantity).sum();
                storeReport.setStoreRefundCount(storeReport.getStoreRefundCount() + totalReturnItems);
            }

            // B. 記錄「新商品購物」部分 (透過新生成的線下訂單)
            if (newOrder != null) {
                BigDecimal currentGmv = storeReport.getStoreGmv() != null ? storeReport.getStoreGmv() : BigDecimal.ZERO;
                storeReport.setStoreGmv(currentGmv.add(newOrder.getTotalAmount())); // 新商品總價計入門店 GMV

                Integer currentOrders = storeReport.getStoreOrders() != null ? storeReport.getStoreOrders() : 0;
                storeReport.setStoreOrders(currentOrders + 1);
            }

            storeDailyReportRepository.save(storeReport);

        } catch (NumberFormatException e) {
            System.err.println("⚠️ 無效的門店ID，無法更新門店換貨報表: " + request.getStoreId());
        } catch (Exception e) {
            System.err.println("⚠️ 更新門店換貨報表失敗: " + e.getMessage());
        }
    }

    /**
     * 【新增方法】：更新每日業務報表中的退貨數據
     */
    private void updateDailyReportForReturn(AfterSalesRequest request) {
        // 1. 確定報表日期 (使用退貨完成的日期)
        LocalDate reportDate = request.getCompletedAt().toLocalDate();

        // 2. 查找當日報表，如果不存在則創建新記錄
        DailyBusinessReport report = dailyReportRepository.findByReportDate(reportDate)
                .orElseGet(() -> {
                    DailyBusinessReport newReport = new DailyBusinessReport();
                    newReport.setReportDate(reportDate);
                    return newReport;
                });

        // 3. 計算本次退貨的總金額和總件數
        BigDecimal totalRefundAmount = BigDecimal.ZERO;
        int totalReturnItems = 0;

        for (AfterSalesRequestItem item : request.getItems()) {
            // 累加退款金額 (使用明細中的最終結算金額)
            if (item.getFinalSettleAmount() != null) {
                totalRefundAmount = totalRefundAmount.add(item.getFinalSettleAmount());
            }
            // 累加退貨件數
            totalReturnItems += item.getReturnQuantity();
        }

        // 4. 累加到報表字段中
        // 退款金額 = 原退款金額 + 本次退款金額
        report.setRefundAmount(report.getRefundAmount().add(totalRefundAmount));
        // 退貨件數 = 原退貨件數 + 本次退貨件數
        report.setRefundCount(report.getRefundCount() + totalReturnItems);

        // 5. 保存更新後的報表
        dailyReportRepository.save(report);
    }

    /**
     * 【新增 4】：更新門店每日業務報表中的退貨/換貨數據
     */
    private void updateStoreDailyReportForReturn(AfterSalesRequest request) {
        try {
            // 1. 解析門店 ID
            Long storeId = Long.parseLong(request.getStoreId());
            LocalDate reportDate = request.getCompletedAt().toLocalDate();

            // 2. 獲取門店實體 (為了滿足 JPA 的 @ManyToOne 關聯)
            OfflineStore store = offlineStoreRepository.findById(storeId)
                    .orElseThrow(() -> new RuntimeException("關聯的門店不存在，ID: " + storeId));

            // 3. 查找或創建該門店當天的報表
            StoreDailyReport storeReport = storeDailyReportRepository.findByStore_StoreIdAndReportDate(storeId, reportDate)
                    .orElseGet(() -> {
                        StoreDailyReport newReport = new StoreDailyReport();
                        newReport.setStore(store);
                        newReport.setReportDate(reportDate);
                        return newReport;
                    });

            // 4. 計算本次退貨/換貨的總金額和總件數
            BigDecimal totalRefundAmount = BigDecimal.ZERO;
            int totalReturnItems = 0;

            for (AfterSalesRequestItem item : request.getItems()) {
                if (item.getFinalSettleAmount() != null) {
                    // 注意：如果是換貨且用戶補差價，finalSettleAmount 可能是負數。
                    // 這裡使用 abs() 確保累加的是「處理的金額規模」，或者你可以根據具體財務規則調整為直接 add
                    totalRefundAmount = totalRefundAmount.add(item.getFinalSettleAmount().abs());
                }
                totalReturnItems += item.getReturnQuantity();
            }

            // 5. 累加到門店報表字段中
            storeReport.setStoreRefundAmount(storeReport.getStoreRefundAmount().add(totalRefundAmount));
            storeReport.setStoreRefundCount(storeReport.getStoreRefundCount() + totalReturnItems);

            // 6. 保存更新後的門店報表
            storeDailyReportRepository.save(storeReport);

        } catch (NumberFormatException e) {
            // 防呆處理：如果 storeId 格式錯誤，記錄日誌但不中斷主流程
            System.err.println("⚠️ 無效的門店ID，無法更新門店報表: " + request.getStoreId());
        }
    }


    /**
     * 【核心邏輯】：售後完成後，重新評估並同步原訂單的狀態
     * <p>
     * 業務場景：當一筆售後申請（退貨/換貨）被標記為「已完成」時，需要重新檢查該訂單的整體售後進度。
     * 核心規律：對比「原訂單總購買數量」與「已完成售後總數量 + 當前正在處理的售後數量」。
     * </p>
     *
     * @param order         需要重新評估狀態的原始訂單對象
     * @param currentRequest 當前正在處理並即將/剛剛完成狀態變更的售後申請
     */
    @Transactional
    public void syncOrderStatusAfterSalesCompletion(Order order, AfterSalesRequest currentRequest) {
        // ==========================================
        // 1. 獲取訂單基準數據：查詢該訂單下的所有商品明細
        // ==========================================
        List<OrderItem> orderItems = orderItemRepository.findByOrder_OrderId(order.getOrderId());
        // 防禦性編程：如果訂單沒有明細（理論上不應發生），則直接終止邏輯
        if (orderItems == null || orderItems.isEmpty()) return;

        // ==========================================
        // 2. 獲取歷史售後數據：查詢該訂單下所有狀態為「已完成 (COMPLETED)」的售後申請
        // ==========================================
        List<AfterSalesRequest> completedRequests = afterSalesRequestRepository
                .findByOriginalOrder_OrderIdAndStatus(order.getOrderId(), AfterSalesRequest.RequestStatus.COMPLETED);

        // ==========================================
        // 3. 統計每個 OrderItem 已經被售後處理的總數量
        // ==========================================
        Map<Long, Integer> processedQuantityMap = new HashMap<>();

        // 3.1 累加「歷史已經成功」的退/換貨數量 (每項物品都要找)
        for (AfterSalesRequest req : completedRequests) {
            for (AfterSalesRequestItem reqItem : req.getItems()) {
                Long orderItemId = reqItem.getOrderItem().getOrderItemId();
                int qty = reqItem.getReturnQuantity();
                processedQuantityMap.put(orderItemId, processedQuantityMap.getOrDefault(orderItemId, 0) + qty);
            }
        }

        // 3.2 【核心修復】：加上「現在退貨/換貨物品」的數量！
        // 因為當前這個 request 正在被標記為完成，它的數量必須算入總處理量中
        if (currentRequest != null && currentRequest.getItems() != null) {
            for (AfterSalesRequestItem currentItem : currentRequest.getItems()) {
                Long orderItemId = currentItem.getOrderItem().getOrderItemId();
                int qty = currentItem.getReturnQuantity();
                processedQuantityMap.put(orderItemId, processedQuantityMap.getOrDefault(orderItemId, 0) + qty);
            }
        }

        // ==========================================
        // 4. 最後才和原訂單總購買數量對比！
        // ==========================================
        boolean isFullyProcessed = true; // 預設為整單處理

        for (OrderItem orderItem : orderItems) {
            // 獲取該明細的「歷史已完成數量 + 當前處理數量」總和
            int totalProcessedQty = processedQuantityMap.getOrDefault(orderItem.getOrderItemId(), 0);

            // 核心比對：如果「總處理數量」小於「原始購買數量」，說明還有商品未被售後處理
            if (totalProcessedQty < orderItem.getQuantity()) {
                isFullyProcessed = false; // 標記為部分處理
                break; // 只要有一個明細未處理完，即可提前跳出循環，優化性能
            }
        }

        // ==========================================
        // 5. 根據判斷結果，更新訂單的最終狀態與支付狀態
        // ==========================================
        if (isFullyProcessed) {
            // 【情況 A：整單處理完畢】 -> 訂單等同於取消，支付狀態為全額退款
            order.setStatus(Order.OrderStatus.CANCELLED);
            order.setPaymentStatus(Order.PaymentStatus.REFUNDED);
        } else {
            // 【情況 B：部分處理完畢】 -> 訂單狀態保持不變，支付狀態標記為部分退款
            order.setPaymentStatus(Order.PaymentStatus.PARTIALLY_REFUNDED);
        }

        // ==========================================
        // 6. 持久化訂單狀態變更
        // ==========================================
        orderRepository.save(order);
    }

    /**
     * 確認售後商品入庫 (門店退貨入庫)
     */
    @Transactional
    public void confirmItemStockIn(Long requestItemId, String operatorUsername) {
        // 1. 查找售後明細
        AfterSalesRequestItem requestItem = afterSalesRequestItemRepository.findById(requestItemId)
                .orElseThrow(() -> new RuntimeException("售後明細不存在，ID: " + requestItemId));

        // 防呆校驗：防止重複入庫
        if (Boolean.TRUE.equals(requestItem.getIsStockedIn())) {
            throw new RuntimeException("該商品已經入庫，請勿重複操作！");
        }

        // 2. 將 is_stocked_in 設為 true 並保存
        requestItem.setIsStockedIn(true);
        afterSalesRequestItemRepository.save(requestItem);

        // 3. 獲取關聯實體信息
        AfterSalesRequest request = requestItem.getAfterSalesRequest();
        Product product = requestItem.getProduct();
        Integer returnQty = requestItem.getReturnQuantity();

        // 4. 根據 after_sales_request 的 store_id 獲取門店實體
        if (request.getStoreId() == null || request.getStoreId().trim().isEmpty()) {
            throw new RuntimeException("該售後申請未關聯門店，無法執行門店入庫！");
        }
        Long storeId = Long.parseLong(request.getStoreId());
        OfflineStore store = offlineStoreRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("關聯的門店不存在，ID: " + storeId));

        // 5. 查找或創建該門店的該商品庫存記錄
        StoreInventory storeInv = storeInventoryRepository.findByStoreAndProduct(store, product)
                .orElseGet(() -> {
                    StoreInventory newInv = new StoreInventory();
                    newInv.setStore(store);
                    newInv.setProduct(product);
                    newInv.setQuantity(0);
                    return newInv;
                });

        Integer previousQty = storeInv.getQuantity();
        Integer newQty = previousQty + returnQty;

        // 6. 更新門店庫存
        storeInv.setQuantity(newQty);
        storeInventoryRepository.save(storeInv);

        // 7. 生成庫存調整日誌 (InventoryAdjustmentLog)
        User operator = userRepository.findByUsername(operatorUsername)
                .orElseThrow(() -> new RuntimeException("操作人不存在"));

        InventoryAdjustmentLog log = new InventoryAdjustmentLog();
        log.setProduct(product);
        log.setOperator(operator);
        log.setWarehouseType(InventoryAdjustmentLog.WarehouseType.OFFLINE); // 門店庫存
        log.setStore(store); // 關聯到該門店
        log.setPreviousQuantity(previousQty);
        log.setNewQuantity(newQty);
        log.setChangeQuantity(returnQty); // 正數表示增加
        log.setReason("售後退貨入庫 (申請ID: " + request.getRequestId() + ")");
        log.setAdjustmentType(InventoryAdjustmentLog.AdjustmentType.RETURN); // 類型為 RETURN
        log.setAfterSalesRequestId(request.getRequestId());
        adjustmentLogRepository.save(log);
    }
}