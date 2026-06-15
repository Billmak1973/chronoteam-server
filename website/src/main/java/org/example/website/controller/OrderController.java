package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Order;
import org.example.website.entity.OrderItem;
import org.example.website.repository.OrderItemRepository;
import org.example.website.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    // 構造函數注入
    public OrderController(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * 獲取訂單商品明細 (供前端展開詳情時調用)
     */
    @GetMapping("/{orderNo}/details")
    public ResponseEntity<?> getOrderDetails(@PathVariable String orderNo, Authentication authentication) {
        try {
            String username = authentication.getName();

            // 1. 校驗訂單是否存在且屬於當前用戶（防越權攻擊）
            Order order = orderRepository.findByOrderNoAndCustomer_Username(orderNo, username)
                    .orElseThrow(() -> new RuntimeException("訂單不存在或無權訪問"));

            // 2. 獲取訂單商品明細
            List<OrderItem> items = orderItemRepository.findByOrder_OrderNo(orderNo);

            // 3. 🟢 關鍵：手動提取需要的數據，避免 Hibernate 關聯導致的 JSON 循環引用 (StackOverflow)
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (OrderItem item : items) {
                Map<String, Object> map = new HashMap<>();
                map.put("quantity", item.getQuantity());
                map.put("price", item.getPrice());

                // 提取 Product 信息 (與前端 JS 渲染的 item.product.xxx 完全對應)
                Map<String, Object> productMap = new HashMap<>();
                productMap.put("id", item.getProduct().getId());
                productMap.put("description", item.getProduct().getDescription());
                productMap.put("image", item.getProduct().getImage());
                productMap.put("category", item.getProduct().getCategory());
                map.put("product", productMap);

                resultList.add(map);
            }

            // 4. 返回訂單商品列表
            return ResponseEntity.ok(ApiResponse.okWithData("成功", resultList));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}