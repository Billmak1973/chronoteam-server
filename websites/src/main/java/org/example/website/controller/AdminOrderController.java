package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Order;
import org.example.website.entity.OrderItem;
import org.example.website.repository.OrderItemRepository;
import org.example.website.repository.OrderRepository;
import org.example.website.util.PaginationUtils;
import org.example.website.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminOrderController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public AdminOrderController(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * 1. 頁面骨架渲染 (僅返回空殼 HTML，數據交由前端 AJAX 獲取)
     */
    @GetMapping("/orders")
    public String ordersPage(Model model) {
        return "admin/admin-orders";
    }

    /**
     * 2. 【新增】標準化 API：獲取訂單列表 + 明細 (一次性返回，避免 N+1)
     */
    @GetMapping("/api/orders/list")
    @ResponseBody
    public ResponseEntity<?> getOrdersList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> ordersPage = orderRepository.findAllWithUsers(pageable);

        // 一次 SQL 查出當前頁所有訂單的明細，按 orderId 分組
        List<Long> orderIds = ordersPage.getContent().stream()
                .map(Order::getOrderId)
                .collect(Collectors.toList());

        Map<Long, List<OrderItem>> orderItemsMap;
        if (!orderIds.isEmpty()) {
            List<OrderItem> allItems = orderItemRepository.findByOrder_OrderIdIn(orderIds);
            orderItemsMap = allItems.stream()
                    .collect(Collectors.groupingBy(item -> item.getOrder().getOrderId()));
        } else {
            orderItemsMap = new HashMap<>();
        }

        // 數據清洗：將 Order + OrderItems 組裝為前端友好的 Map
        List<Map<String, Object>> cleanOrders = ordersPage.getContent().stream().map(order -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderId", order.getOrderId());
            item.put("orderNo", order.getOrderNo());
            item.put("buyerUsername", order.getUser() != null ? order.getUser().getUsername() : "未知用戶");
            item.put("courierUsername", order.getCourier() != null ? order.getCourier().getUsername() : null);
            item.put("totalAmount", order.getTotalAmount());
            item.put("shippingFee", order.getShippingFee());
            item.put("deliveryMethod", order.getDeliveryMethod());
            item.put("delivery", order.getDelivery());
            item.put("paymentMethod", order.getPaymentMethod());
            item.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
            item.put("orderStatus", order.getStatus() != null ? order.getStatus().name() : null);
            item.put("offlineStoreName", order.getOfflineStore() != null ? order.getOfflineStore().getName() : null);
            item.put("createdAt", order.getCreatedAt());
            item.put("paidAt", order.getPaidAt());
            item.put("receivedAt", order.getReceivedAt());
            item.put("deadlineAt", order.getDeadlineAt());
            item.put("isVisible", order.getIsVisible());

            // 將明細嵌入訂單對象
            List<OrderItem> items = orderItemsMap.getOrDefault(order.getOrderId(), Collections.emptyList());
            List<Map<String, Object>> cleanItems = items.stream().map(oi -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productName", oi.getProduct() != null ? oi.getProduct().getDescription() : "未知商品");
                m.put("productImage", oi.getProduct() != null ? oi.getProduct().getImage() : null);
                m.put("productCategory", oi.getProduct() != null ? oi.getProduct().getCategory() : null);
                m.put("quantity", oi.getQuantity());
                m.put("price", oi.getPrice());
                m.put("subtotal", oi.getPrice().multiply(java.math.BigDecimal.valueOf(oi.getQuantity())));
                return m;
            }).collect(Collectors.toList());
            item.put("items", cleanItems);

            return item;
        }).collect(Collectors.toList());

        // 使用 PaginationUtils 構建標準響應
        Map<String, Object> response = PaginationUtils.buildPageResponse(ordersPage, cleanOrders);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. 管理員專屬：獲取訂單明細 (保留備用)
     */
    @GetMapping("/{orderNo}/details")
    public ResponseEntity<?> getOrderDetailsForAdmin(@PathVariable String orderNo) {
        if (!SecurityUtils.isAdmin()) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權訪問，僅限管理員"));
        }
        try {
            orderRepository.findByOrderNo(orderNo)
                    .orElseThrow(() -> new RuntimeException("訂單不存在"));
            List<OrderItem> items = orderItemRepository.findByOrder_OrderNo(orderNo);
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (OrderItem oi : items) {
                Map<String, Object> map = new HashMap<>();
                map.put("quantity", oi.getQuantity());
                map.put("price", oi.getPrice());
                Map<String, Object> productMap = new HashMap<>();
                productMap.put("id", oi.getProduct().getProductId());
                productMap.put("description", oi.getProduct().getDescription());
                productMap.put("image", oi.getProduct().getImage());
                productMap.put("category", oi.getProduct().getCategory());
                map.put("product", productMap);
                resultList.add(map);
            }
            return ResponseEntity.ok(ApiResponse.okWithData("成功", resultList));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}