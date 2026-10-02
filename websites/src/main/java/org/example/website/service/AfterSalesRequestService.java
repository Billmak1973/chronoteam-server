package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.dto.ReturnRequestDTO;
import org.example.website.entity.*;
import org.example.website.repository.AfterSalesRequestRepository;
import org.example.website.repository.OrderRepository;
import org.example.website.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AfterSalesRequestService {

    private final AfterSalesRequestRepository afterSalesRequestRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

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

        request.getItems().add(requestItem);

        // 4. 保存並返回
        return afterSalesRequestRepository.save(request);
    }
}