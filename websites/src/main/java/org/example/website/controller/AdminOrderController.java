package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.Notification;
import org.example.website.entity.Order;
import org.example.website.entity.OrderItem;
import org.example.website.entity.User;
import org.example.website.repository.NotificationRepository;
import org.example.website.repository.OrderItemRepository;
import org.example.website.repository.OrderRepository;
import org.example.website.repository.UserRepository;
import org.example.website.service.NotificationService;
import org.example.website.service.OrderService;
import org.example.website.service.SystemConfigService;
import org.example.website.util.PaginationUtils;
import org.example.website.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@Tag(name = "後台訂單管理", description = "管理員專屬的訂單查詢與明細獲取接口")
public class AdminOrderController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SystemConfigService systemConfigService; // 新增注入
    private final OrderService orderService;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public AdminOrderController(OrderRepository orderRepository, OrderItemRepository orderItemRepository, SystemConfigService systemConfigService, OrderService orderService, NotificationRepository notificationRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.systemConfigService = systemConfigService;
        this.orderService = orderService;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * 1. 頁面骨架渲染 (僅返回空殼 HTML，數據交由前端 AJAX 獲取)
     * 使用 @Hidden 隱藏此接口，因為 Swagger 專注於 REST API，不需要展示 Thymeleaf 頁面渲染接口
     */
    @GetMapping("/orders")
    @Hidden
    public String ordersPage(Model model) {
        return "admin/admin-orders";
    }

    /**
     * 2. 標準化 API：獲取訂單列表 + 明細 (一次性返回，避免 N+1)
     */
    @GetMapping("/api/orders/list")
    @ResponseBody
    @Operation(
            summary = "獲取後台訂單分頁列表與明細",
            description = "分頁獲取系統內所有訂單，並一次性關聯查詢訂單內的商品明細，避免 N+1 查詢性能問題。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "401", description = "未登入或無權限"),
            @ApiResponse(responseCode = "500", description = "服務器內部錯誤")
    })
    public ResponseEntity<?> getOrdersList(
            @Parameter(description = "當前頁碼 (1-based，從 1 開始)", example = "1")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "每頁顯示數量", example = "25")
            @RequestParam(defaultValue = "25") int size) {

        // 將 1-based 轉換為 0-based 供 Spring Data 使用
        int pageIndex = Math.max(0, page - 1);

        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));
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

            // 【修改處】：移除 deadlineAt，新增預計送達與預約到店日期
            item.put("estimatedDeliveryDate", order.getEstimatedDeliveryDate());
            item.put("appointmentDate", order.getAppointmentDate());

            item.put("isVisible", order.getIsVisible());
            // 新增返回提醒次數
            item.put("pickupReminderCount", order.getPickupReminderCount() != null ? order.getPickupReminderCount() : 0);

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

        // 關鍵！覆蓋 currentPage，將 0-based 轉回 1-based 返回給前端
        response.put("currentPage", page);

        response.put("onlineOrderRetentionDays", systemConfigService.getOnlineOrderRetentionDays());
        response.put("offlinePaymentDays", systemConfigService.getOfflinePaymentDays());
        return ResponseEntity.ok(response);
    }

    /**
     * 3. 管理員專屬：獲取訂單明細 (保留備用)
     */
    @GetMapping("/{orderNo}/details")
    @ResponseBody
    @Operation(
            summary = "獲取特定訂單的商品明細",
            description = "根據訂單編號獲取該訂單下所有商品的詳細信息（數量、單價、商品名稱等）。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功"),
            @ApiResponse(responseCode = "403", description = "無權訪問，僅限管理員"),
            @ApiResponse(responseCode = "404", description = "訂單不存在")
    })
    public ResponseEntity<?> getOrderDetailsForAdmin(
            @Parameter(description = "訂單編號 (Order No)", example = "ORD-123456789")
            @PathVariable String orderNo) {

        if (!SecurityUtils.isAdmin()) {
            return ResponseEntity.status(403).body(Result.error("無權訪問，僅限管理員"));
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
            return ResponseEntity.ok(Result.okWithData("成功", resultList));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @PostMapping("/api/order/{orderNo}/cancel-expired")
    @ResponseBody
    public ResponseEntity<?> cancelExpiredOrder(
            @PathVariable String orderNo,
            @RequestBody Map<String, String> request, // 接收前端傳來的 customMessage
            Authentication authentication) {
        try {
            // 獲取自定義訊息，若前端沒傳則給個預設值
            String customMessage = request.getOrDefault("customMessage", "您的訂單已因超時取消。");

            // 調用 Service，傳入自定義訊息
            orderService.cancelExpiredOrder(orderNo, customMessage);
            return ResponseEntity.ok(Result.ok("訂單已取消並通知用戶"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤"));
        }
    }

    @PostMapping("/api/order/{orderNo}/remind-pickup")
    @ResponseBody
    public ResponseEntity<?> remindPickup(@PathVariable String orderNo,
                                          @RequestBody Map<String, String> payload,
                                          Authentication authentication) {
        String customMessage = payload.get("customMessage");
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("訂單不存在"));

        // 1. 更新訂單的提醒次數與最後提醒時間
        order.setPickupReminderCount(order.getPickupReminderCount() == null ? 1 : order.getPickupReminderCount() + 1);
        order.setLastPickupReminderAt(LocalDateTime.now());
        orderRepository.save(order);

        // 2. 創建並發送系統通知給買家 (站內信)
        Notification notification = new Notification();
        notification.setRecipient(order.getUser()); // 接收者為訂單的買家
        notification.setSender(null); // null 代表系統自動發送 (若需顯示管理員名稱，可改為 authentication.getName() 對應的 User)
        notification.setType(Notification.NotificationType.SYSTEM);

        notification.setTitle("🔔 門店取貨提醒");
        // 如果管理員沒填寫自定義訊息，則使用預設溫馨提示
        notification.setContent(customMessage != null && !customMessage.trim().isEmpty()
                ? customMessage
                : "溫馨提醒：您的訂單已超過預約取貨時間，請盡快前往預約的門店完成取貨，或聯繫客服協助處理。");

        notification.setTargetUrl("/account/orders"); // 引導用戶點擊通知後回到訂單列表
        notification.setRead(false);

        // 3. 保存通知到數據庫
        notificationRepository.save(notification);

        return ResponseEntity.ok(Result.ok("提醒已成功發送！累計已提醒 " + order.getPickupReminderCount() + " 次。"));
    }

    /**
     * 獲取所有快遞員列表 (供下拉選單使用)
     */
    @GetMapping("/api/couriers/list")
    @ResponseBody
    public ResponseEntity<?> getCouriersList() {
        // 查詢 role 為 COURIER 的用戶
        List<User> couriers = userRepository.findByRole(User.Role.COURIER);
        List<Map<String, Object>> courierList = couriers.stream().map(user -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Result.okWithData("獲取成功", courierList));
    }

    /**
     * 為訂單分配快遞員
     */
    @PostMapping("/api/orders/{orderNo}/assign-courier")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> assignCourier(
            @PathVariable String orderNo,
            @RequestBody Map<String, Long> request) {

        Long courierId = request.get("courierId");
        if (courierId == null) {
            return ResponseEntity.badRequest().body(Result.error("快遞員 ID 不能為空"));
        }

        // 1. 查找訂單
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("訂單不存在"));

        // 2. 校驗是否為快遞訂單且尚未分配
        if (order.getDelivery() == null || !order.getDelivery()) {
            return ResponseEntity.badRequest().body(Result.error("該訂單不需要快遞配送"));
        }
        if (order.getCourier() != null) {
            return ResponseEntity.badRequest().body(Result.error("該訂單已分配快遞員，如需更換請先聯繫系統管理員"));
        }

        // 3. 查找快遞員並校驗身份
        User courier = userRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("快遞員不存在"));

        if (courier.getRole() != User.Role.COURIER) {
            return ResponseEntity.badRequest().body(Result.error("選擇的用戶不是快遞員"));
        }

        // 4. 分配並保存
        order.setCourier(courier);
        // 可選：如果業務邏輯允許，分配快遞員時可自動將訂單狀態改為「已發貨」
//         if (order.getStatus() == Order.OrderStatus.PAID) {
//             order.setStatus(Order.OrderStatus.SHIPPED);
//         }
        orderRepository.save(order);

        return ResponseEntity.ok(Result.ok("成功分配快遞員: " + courier.getUsername()));
    }

}