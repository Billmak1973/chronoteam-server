package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Order;
import org.example.website.entity.OrderItem;
import org.example.website.repository.OrderItemRepository;
import org.example.website.repository.OrderRepository;
import org.example.website.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * 1. 渲染訂單管理列表頁面 (包含完整的 17 個字段)
     * 【核心修復】：後端直接用 order_item.order_id 匹配 orders.order_id，
     * 一次性把當前頁所有訂單的商品明細全部查出來傳給前端，
     * 前端 Thymeleaf 直接渲染，徹底告別 AJAX 和「加載中」！
     */
    @GetMapping("/orders")
    public String listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Model model) {

        // 按創建時間倒序排列
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> ordersPage = orderRepository.findAllWithUsers(pageable);

        // ==========================================
        // 【核心修復】：orders.order_id 直接匹配 order_item.order_id
        // 一次 SQL 查出當前頁所有訂單的明細，再按訂單 ID 分組 (避免 N+1)
        // ==========================================
        List<Long> orderIds = ordersPage.getContent().stream()
                .map(Order::getOrderId)
                .collect(Collectors.toList());

        Map<Long, List<OrderItem>> orderItemsMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<OrderItem> allItems = orderItemRepository.findByOrder_OrderIdIn(orderIds);
            orderItemsMap = allItems.stream()
                    .collect(Collectors.groupingBy(item -> item.getOrder().getOrderId()));
        }

        model.addAttribute("orders", ordersPage);
        model.addAttribute("orderItemsMap", orderItemsMap); // 訂單明細 Map，前端直接渲染
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", ordersPage.getTotalPages());

        return "admin/admin-orders";
    }

    /**
     * 2. API: 獲取訂單詳情 (OrderItems) - 保留備用
     */
    @GetMapping("/api/order/{orderId}/items")
    @ResponseBody
    public Map<String, Object> getOrderItems(@PathVariable Long orderId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<OrderItem> items = orderItemRepository.findByOrder_OrderId(orderId);
            List<Map<String, Object>> itemList = items.stream().map(item -> {
                Map<String, Object> map = new HashMap<>();
                map.put("productName", item.getProduct().getDescription());
                map.put("productImage", item.getProduct().getImage());
                map.put("quantity", item.getQuantity());
                map.put("price", item.getPrice());
                map.put("subtotal", item.getPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())));
                return map;
            }).collect(Collectors.toList());

            response.put("success", true);
            response.put("data", itemList);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    /**
     * 管理員專屬：獲取訂單明細 (不校驗用戶歸屬，僅校驗管理員 Role) - 保留備用
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
            for (OrderItem item : items) {
                Map<String, Object> map = new HashMap<>();
                map.put("quantity", item.getQuantity());
                map.put("price", item.getPrice());

                Map<String, Object> productMap = new HashMap<>();
                productMap.put("id", item.getProduct().getProductId());
                productMap.put("description", item.getProduct().getDescription());
                productMap.put("image", item.getProduct().getImage());
                productMap.put("category", item.getProduct().getCategory());
                map.put("product", productMap);

                resultList.add(map);
            }

            return ResponseEntity.ok(ApiResponse.okWithData("成功", resultList));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}