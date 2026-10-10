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


    /**
     * 【核心新增】：更新售後申請狀態
     */
    @Transactional
    public void updateAfterSalesStatus(Long requestId, AfterSalesRequest.RequestStatus newStatus,
                                       Map<Long, BigDecimal> itemRefundAmounts, String operatorUsername) {
        AfterSalesRequest request = afterSalesRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("售後申請不存在，ID: " + requestId));

        if (request.getStatus() == newStatus) {
            throw new RuntimeException("狀態未發生改變");
        }

        // 如果變更為 COMPLETED，處理退款金額及後續業務邏輯
        if (newStatus == AfterSalesRequest.RequestStatus.COMPLETED) {
            BigDecimal totalRefund = BigDecimal.ZERO;

            // 1. 更新明細的最終結算金額，並計算總退款
            for (AfterSalesRequestItem item : request.getItems()) {
                BigDecimal refundAmount = itemRefundAmounts.getOrDefault(item.getRequestItemId(), BigDecimal.ZERO);
                item.setFinalSettleAmount(refundAmount);
                totalRefund = totalRefund.add(refundAmount);
            }

            request.setTotalRefundAmount(totalRefund);
            request.setCompletedAt(LocalDateTime.now());

            // 2. 觸發訂單狀態的全局同步 (判斷是否整單退貨/換貨)
            syncOrderStatusAfterSalesCompletion(request.getOriginalOrder());

            // 3. 如果是「退貨」，更新每日財務報表
            if (request.getRequestType() == AfterSalesRequest.RequestType.RETURN) {
                updateDailyReportForReturn(request);
            }
        }

        // 【修復語法錯誤】：移除多餘的 "this"
        request.setStatus(newStatus);
        afterSalesRequestRepository.save(request);
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
     * 【核心邏輯】：售後完成後，重新評估並同步原訂單的狀態
     */
    @Transactional
    public void syncOrderStatusAfterSalesCompletion(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrder_OrderId(order.getOrderId());
        if (orderItems == null || orderItems.isEmpty()) return;

        // 獲取該訂單下，所有狀態為 COMPLETED 的售後申請
        List<AfterSalesRequest> completedRequests = afterSalesRequestRepository
                .findByOriginalOrder_OrderIdAndStatus(order.getOrderId(), AfterSalesRequest.RequestStatus.COMPLETED);

        // 統計每個 OrderItem 已經被售后處理的總數量
        Map<Long, Integer> processedQuantityMap = new HashMap<>();
        for (AfterSalesRequest req : completedRequests) {
            for (AfterSalesRequestItem reqItem : req.getItems()) {
                Long orderItemId = reqItem.getOrderItem().getOrderItemId();
                int qty = reqItem.getReturnQuantity();
                processedQuantityMap.put(orderItemId, processedQuantityMap.getOrDefault(orderItemId, 0) + qty);
            }
        }

        // 判斷是否為「整單處理」
        boolean isFullyProcessed = true;
        for (OrderItem orderItem : orderItems) {
            int processedQty = processedQuantityMap.getOrDefault(orderItem.getOrderItemId(), 0);
            if (processedQty < orderItem.getQuantity()) {
                isFullyProcessed = false;
                break;
            }
        }

        // 根據判斷結果更新訂單狀態
        if (isFullyProcessed) {
            order.setStatus(Order.OrderStatus.CANCELLED);
            order.setPaymentStatus(Order.PaymentStatus.REFUNDED);
        } else {
            if (order.getPaymentStatus() != Order.PaymentStatus.REFUNDED) {
                order.setPaymentStatus(Order.PaymentStatus.PARTIALLY_REFUNDED);
            }
        }
        orderRepository.save(order);
    }
}