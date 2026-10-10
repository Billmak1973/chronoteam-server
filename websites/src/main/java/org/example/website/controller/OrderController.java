package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.AfterSalesRequest;
import org.example.website.entity.AfterSalesRequestItem;
import org.example.website.entity.Order;
import org.example.website.entity.OrderItem;
import org.example.website.repository.AfterSalesRequestRepository;
import org.example.website.repository.OrderItemRepository;
import org.example.website.repository.OrderRepository;
import org.example.website.service.OrderService;
import org.example.website.service.SystemConfigService;
import org.example.website.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/order")
@Tag(name = "用戶訂單管理", description = "用戶查看、刪除、取消及確認收貨等訂單相關操作接口")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderService orderService;
    private final AfterSalesRequestRepository afterSalesRequestRepository;
    private final SystemConfigService systemConfigService;

    // 構造函數注入
    public OrderController(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                           OrderService orderService, AfterSalesRequestRepository afterSalesRequestRepository, SystemConfigService systemConfigService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderService = orderService;
        this.afterSalesRequestRepository = afterSalesRequestRepository;
        this.systemConfigService = systemConfigService;
    }


    @Operation(summary = "獲取待付款訂單", description = "分頁獲取當前用戶的待付款訂單列表")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @GetMapping("/unpaid")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUnpaidOrders(
            @RequestParam(defaultValue = "1") int page,
            Authentication authentication) {
        String username = authentication.getName();
        int zeroBasedPage = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(zeroBasedPage, 25);

        Page<Order> orderPage = orderRepository.findUnpaidOrders(username, pageable);

        List<Map<String, Object>> cleanOrders = orderPage.getContent().stream().map(order -> {
            Map<String, Object> map = new HashMap<>();
            map.put("orderNo", order.getOrderNo());
            map.put("totalAmount", order.getTotalAmount());
            map.put("paymentMethod", order.getPaymentMethod());
            map.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
            map.put("status", order.getStatus() != null ? order.getStatus().name() : null);
            map.put("createdAt", order.getCreatedAt());
            map.put("paidAt", order.getPaidAt());
            map.put("receivedAt", order.getReceivedAt());

            if (order.getOfflineStore() != null) {
                Map<String, Object> storeMap = new HashMap<>();
                storeMap.put("name", order.getOfflineStore().getName());
                storeMap.put("address", order.getOfflineStore().getAddress());
                map.put("offlineStore", storeMap);
            } else {
                map.put("offlineStore", null);
            }
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = PaginationUtils.buildPageResponse(orderPage, cleanOrders);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "獲取待線下付款訂單", description = "分頁獲取當前用戶的待線下付款訂單列表")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @GetMapping("/pending-offline")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPendingOfflineOrders(
            @RequestParam(defaultValue = "1") int page,
            Authentication authentication) {
        String username = authentication.getName();
        int zeroBasedPage = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(zeroBasedPage, 25);

        Page<Order> orderPage = orderRepository.findPendingOfflineOrders(username, pageable);

        List<Map<String, Object>> cleanOrders = orderPage.getContent().stream().map(order -> {
            Map<String, Object> map = new HashMap<>();
            map.put("orderNo", order.getOrderNo());
            map.put("totalAmount", order.getTotalAmount());
            map.put("paymentMethod", order.getPaymentMethod());
            map.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
            map.put("status", order.getStatus() != null ? order.getStatus().name() : null);
            map.put("createdAt", order.getCreatedAt());
            map.put("paidAt", order.getPaidAt());
            map.put("receivedAt", order.getReceivedAt());
            map.put("appointmentDate", order.getAppointmentDate());

            if (order.getOfflineStore() != null) {
                Map<String, Object> storeMap = new HashMap<>();
                storeMap.put("name", order.getOfflineStore().getName());
                storeMap.put("address", order.getOfflineStore().getAddress());
                storeMap.put("phone",order.getOfflineStore().getPhone());
                storeMap.put("scheduleMode", order.getOfflineStore().getScheduleMode());
                storeMap.put("hours", order.getOfflineStore().getHours());
                storeMap.put("dailyHours", order.getOfflineStore().getDailyHours());
                map.put("offlineStore", storeMap);
            } else {
                map.put("offlineStore", null);
            }
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> response = PaginationUtils.buildPageResponse(orderPage, cleanOrders);
        return ResponseEntity.ok(response);
    }


@Operation(summary = "獲取已支付訂單", description = "分頁獲取當前用戶的已支付訂單列表，支持售後狀態篩選")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "獲取成功"),
        @ApiResponse(responseCode = "401", description = "未登入")
})
@GetMapping("/paid")
@ResponseBody
public ResponseEntity<Map<String, Object>> getPaidOrders(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(required = false) String requestType,      // 新增：售後類型 (RETURN / EXCHANGE)
        @RequestParam(required = false) String requestStatus,    // 新增：售後狀態 (PENDING / COMPLETED 等)
        @RequestParam(required = false) String productName,      // 新增：商品名稱模糊搜尋
        Authentication authentication) {

    String username = authentication.getName();
    int zeroBasedPage = Math.max(0, page - 1);
    Pageable pageable = PageRequest.of(zeroBasedPage, 25);

    Page<Order> orderPage;

    // 判斷是否有售後篩選條件
    boolean hasFilter = (requestType != null && !requestType.trim().isEmpty()) ||
            (requestStatus != null && !requestStatus.trim().isEmpty()) ||
            (productName != null && !productName.trim().isEmpty());

    if (hasFilter) {
        // 將字符串轉換為枚舉 (如果為空則傳 null，JPQL 會自動忽略該條件)
        AfterSalesRequest.RequestType reqType = null;
        if (requestType != null && !requestType.trim().isEmpty()) {
            reqType = AfterSalesRequest.RequestType.valueOf(requestType.trim().toUpperCase());
        }

        AfterSalesRequest.RequestStatus reqStatus = null;
        if (requestStatus != null && !requestStatus.trim().isEmpty()) {
            reqStatus = AfterSalesRequest.RequestStatus.valueOf(requestStatus.trim().toUpperCase());
        }

        String cleanProductName = (productName != null && !productName.trim().isEmpty()) ? productName.trim() : null;

        // 調用帶篩選條件的查詢
        orderPage = orderRepository.findPaidOrdersWithAfterSalesFilter(
                username, reqType, reqStatus, cleanProductName, pageable
        );
    } else {
        // 無篩選條件，使用原有查詢
        orderPage = orderRepository.findPaidOrders(username, pageable);
    }

    List<Map<String, Object>> cleanOrders = orderPage.getContent().stream().map(order -> {
        Map<String, Object> map = new HashMap<>();
        map.put("orderNo", order.getOrderNo());
        map.put("totalAmount", order.getTotalAmount());
        map.put("paymentMethod", order.getPaymentMethod());
        map.put("paymentStatus", order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
        map.put("status", order.getStatus() != null ? order.getStatus().name() : null);
        map.put("createdAt", order.getCreatedAt());
        map.put("paidAt", order.getPaidAt());
        map.put("receivedAt", order.getReceivedAt());
        map.put("deliveryMethod", order.getDeliveryMethod());
        map.put("estimatedDeliveryDate", order.getEstimatedDeliveryDate() != null ? order.getEstimatedDeliveryDate().toString() : null);
        map.put("appointmentDate", order.getAppointmentDate() != null ? order.getAppointmentDate().toString() : null);

        if (order.getOfflineStore() != null) {
            Map<String, Object> storeMap = new HashMap<>();
            storeMap.put("name", order.getOfflineStore().getName());
            storeMap.put("address", order.getOfflineStore().getAddress());
            storeMap.put("scheduleMode", order.getOfflineStore().getScheduleMode());
            storeMap.put("hours", order.getOfflineStore().getHours());
            storeMap.put("dailyHours", order.getOfflineStore().getDailyHours());
            storeMap.put("closedStartDate", order.getOfflineStore().getClosedStartDate());
            storeMap.put("closedEndDate", order.getOfflineStore().getClosedEndDate());
            map.put("offlineStore", storeMap);
        } else {
            map.put("offlineStore", null);
        }

        // ==========================================
        // 【新增】：提取快遞員資訊 (username 和 workPhone)
        // ==========================================
        if (order.getCourier() != null) {
            Map<String, Object> courierMap = new HashMap<>();
            courierMap.put("username", order.getCourier().getUsername());
            // 防止 workPhone 為 null 導致前端顯示 "null"
            courierMap.put("workPhone", order.getCourier().getWorkPhone() != null ? order.getCourier().getWorkPhone() : "預留電話");
            map.put("courier", courierMap);
        } else {
            map.put("courier", null);
        }

        // ==========================================
        // 提取收貨地址資訊 (userAddress)
        // ==========================================
        if (order.getUserAddress() != null) {
            Map<String, Object> addressMap = new HashMap<>();
            addressMap.put("fullAddress", order.getUserAddress().getFullAddress());
            addressMap.put("contactPhone", order.getUserAddress().getContactPhone());
            map.put("userAddress", addressMap);
        } else {
            map.put("userAddress", null);
        }

        // ==========================================
        // 售後狀態判斷邏輯 (保持不變)
        // ==========================================
        List<AfterSalesRequest> requests = afterSalesRequestRepository.findByOriginalOrder_OrderId(order.getOrderId());
        boolean hasReturn = requests.stream().anyMatch(r -> r.getRequestType() == AfterSalesRequest.RequestType.RETURN && r.getStatus() != AfterSalesRequest.RequestStatus.CANCELLED);
        boolean hasExchange = requests.stream().anyMatch(r -> r.getRequestType() == AfterSalesRequest.RequestType.EXCHANGE && r.getStatus() != AfterSalesRequest.RequestStatus.CANCELLED);

        if (hasReturn && hasExchange) {
            map.put("afterSalesHint", "該訂單已有物品已經申請退貨和換貨，請留意時間和地點！");
        } else if (hasReturn) {
            map.put("afterSalesHint", "該訂單已有物品已經申請退貨，請留意時間和地點！");
        } else if (hasExchange) {
            map.put("afterSalesHint", "該訂單已有物品已經申請換貨，請留意時間和地點！");
        }
        // ==========================================

        return map;
    }).collect(Collectors.toList());

    Map<String, Object> response = PaginationUtils.buildPageResponse(orderPage, cleanOrders);
    // ==========================================
    // 【新增】將全局送貨時間配置加入響應，供前端使用
    // ==========================================
    response.put("deliveryStartTime", systemConfigService.getDeliveryStartTime());
    response.put("deliveryEndTime", systemConfigService.getDeliveryEndTime());
    return ResponseEntity.ok(response);
}

    @Operation(
            summary = "獲取訂單商品明細",
            description = "根據訂單編號獲取該訂單下的商品列表。包含防越權校驗，僅限訂單所屬用戶訪問。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "獲取成功",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(responseCode = "400", description = "訂單不存在或無權訪問"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @GetMapping("/{orderNo}/details")
    public ResponseEntity<?> getOrderDetails(
            @Parameter(description = "訂單編號", example = "ORD-1715600000000-ABC123", required = true)
            @PathVariable String orderNo,
            @Parameter(hidden = true)
            Authentication authentication) {
        try {
            String username = authentication.getName();
            Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                    .orElseThrow(() -> new RuntimeException("訂單不存在或無權訪問"));

            List<OrderItem> items = orderItemRepository.findByOrder_OrderNo(orderNo);

            // 【新增】獲取該訂單所有 PENDING 狀態的售後申請
            List<AfterSalesRequest> pendingRequests = afterSalesRequestRepository
                    .findByOriginalOrder_OrderNoAndStatus(orderNo, AfterSalesRequest.RequestStatus.PENDING);

            List<Map<String, Object>> resultList = new ArrayList<>();
            for (OrderItem item : items) {
                Map<String, Object> map = new HashMap<>();
                map.put("orderItemId", item.getOrderItemId()); // 用於匹配
                map.put("quantity", item.getQuantity());
                map.put("price", item.getPrice());

                Map<String, Object> productMap = new HashMap<>();
                productMap.put("id", item.getProduct().getProductId());
                productMap.put("description", item.getProduct().getDescription());
                productMap.put("image", item.getProduct().getImage());
                productMap.put("category", item.getProduct().getCategory());
                map.put("product", productMap);

                // ==========================================
                // 【核心邏輯】檢查該 OrderItem 是否已經全額申請了售後
                // ==========================================
                boolean hideExchangeBtn = false;
                boolean hideReturnBtn = false;

                for (AfterSalesRequest req : pendingRequests) {
                    for (AfterSalesRequestItem reqItem : req.getItems()) {
                        // 1. after_sales_request_item 的 order_item_id 和 order_item 的 order_item_id 匹配
                        if (reqItem.getOrderItem().getOrderItemId().equals(item.getOrderItemId())) {
                            // 2. 如果 order_item 的 quantity == after_sales_request_item 的 return_quantity
                            if (reqItem.getReturnQuantity().equals(item.getQuantity())) {
                                // 3. 獲取其 request_type
                                if (req.getRequestType() == AfterSalesRequest.RequestType.RETURN) {
                                    // 4. 如果 request_type 為 RETURN，那麼申請換貨的按鈕消失
                                    hideExchangeBtn = true;
                                } else if (req.getRequestType() == AfterSalesRequest.RequestType.EXCHANGE) {
                                    // 5. 否則 (即 EXCHANGE)，就是退貨按鈕消失
                                    hideReturnBtn = true;
                                }
                            }
                        }
                    }
                }

                // 【新增】將標誌位返回給前端
                map.put("hideExchangeBtn", hideExchangeBtn);
                map.put("hideReturnBtn", hideReturnBtn);

                resultList.add(map);
            }
            return ResponseEntity.ok(Result.okWithData("成功", resultList));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
            }
    }

    @Operation(
            summary = "刪除訂單",
            description = "刪除指定的訂單記錄。為保障數據安全，僅允許刪除「未付款」或「待線下付款」狀態的訂單。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "刪除成功",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(responseCode = "400", description = "業務異常 (如：已付款無法刪除)"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @DeleteMapping("/{orderNo}")
    public ResponseEntity<Result> deleteOrder(
            @Parameter(description = "訂單編號", example = "ORD-1715600000000-ABC123", required = true)
            @PathVariable String orderNo,

            @Parameter(hidden = true)
            Authentication authentication) {
        try {
            String username = authentication.getName();
            // 調用 Service 層執行刪除
            orderService.deleteOrder(orderNo, username);

            // 返回成功響應 (前端依賴 success 和 message 字段)
            return ResponseEntity.ok(Result.ok("訂單已成功刪除"));
        } catch (RuntimeException e) {
            // 捕獲業務異常 (如：訂單不存在、已付款無法刪除等)
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤，刪除失敗"));
        }
    }

    @Operation(
            summary = "取消已付款訂單",
            description = "取消已付款的訂單。此操作會觸發退款流程、恢復商品庫存，並更新財務報表。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "取消並退款成功",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(responseCode = "400", description = "業務異常 (如：訂單狀態不允許取消)"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PostMapping("/{orderNo}/cancel")
    public ResponseEntity<Result> cancelPaidOrder(
            @Parameter(description = "訂單編號", example = "ORD-1715600000000-ABC123", required = true)
            @PathVariable String orderNo,

            @Parameter(hidden = true)
            Authentication authentication) {
        try {
            String username = authentication.getName();
            // 調用 Service 層的 cancelPaidOrder 方法
            orderService.cancelPaidOrder(orderNo, username);

            return ResponseEntity.ok(Result.ok("訂單已成功取消並退款"));
        } catch (RuntimeException e) {
            // 捕獲業務異常 (如：訂單不存在、狀態不允許取消等)
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤，取消訂單失敗"));
        }
    }

    @Operation(
            summary = "隱藏訂單 (軟刪除)",
            description = "將「已取消」或「已退貨」的訂單從用戶前端視圖中隱藏，不進行物理刪除，以便後台審計。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "隱藏成功",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(responseCode = "400", description = "業務異常 (如：訂單狀態不允許隱藏)"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @DeleteMapping("/{orderNo}/hide")
    public ResponseEntity<Result> hideOrder(
            @Parameter(description = "訂單編號", example = "ORD-1715600000000-ABC123", required = true)
            @PathVariable String orderNo,// @PathVariable 接收 URL 路徑中的參數

            @Parameter(hidden = true)
            Authentication authentication) {
        try {
            String username = authentication.getName();
            orderService.hideOrder(orderNo, username);
            return ResponseEntity.ok(Result.ok("訂單已隱藏"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤，隱藏失敗"));
        }
    }

    @Operation(
            summary = "確認門店取貨",
            description = "用戶或管理員確認已完成門店取貨，訂單狀態將更新為「已完成」，並觸發相關的財務報表記錄。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "確認收貨成功",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(responseCode = "400", description = "業務異常 (如：非門店自取訂單或狀態異常)"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PostMapping("/{orderNo}/pickup")
    public ResponseEntity<Result> confirmPickup(
            @Parameter(description = "訂單編號", example = "ORD-1715600000000-ABC123", required = true)
            @PathVariable String orderNo,

            @Parameter(hidden = true)
            Authentication authentication) {

        // 獲取當前登錄用戶名
        String currentUsername = authentication.getName();

        // 調用 Service 層執行取貨邏輯
        orderService.confirmPickup(orderNo, currentUsername);

        return ResponseEntity.ok(Result.ok("確認收貨成功，訂單狀態已更新為已完成！"));
    }

    @PutMapping("/{orderNo}/change-delivery-date")
    @Transactional
    public ResponseEntity<Result> changeDeliveryDate(
            @PathVariable String orderNo,
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        try {
            String username = authentication.getName();
            String newDeliveryDate = request.get("estimatedDeliveryDate");

            // 验证订单权限和状态
            Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                    .orElseThrow(() -> new RuntimeException("订单不存在或无权操作"));

            if (order.getStatus() != Order.OrderStatus.PAID) {
                return ResponseEntity.badRequest()
                        .body(Result.error("只有已付款且未发货的订单才能更改送货日期"));
            }

            if (!"EXPRESS".equals(order.getDeliveryMethod())) {
                return ResponseEntity.badRequest()
                        .body(Result.error("只有快递配送订单才能更改送货日期"));
            }

            // 验证日期格式
            LocalDate deliveryDate = LocalDate.parse(newDeliveryDate);

            // 更新订单
            order.setEstimatedDeliveryDate(deliveryDate);
            orderRepository.save(order);

            return ResponseEntity.ok(Result.ok("送货日期已更新为：" + newDeliveryDate));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Result.error("更改失败：" + e.getMessage()));
        }
    }

    @Operation(summary = "修改門店自取訂單的取貨日期", description = "用戶修改已付款門店自取訂單的預約取貨日期")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "修改成功"),
            @ApiResponse(responseCode = "400", description = "日期超出允許範圍或訂單狀態不符"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PutMapping("/{orderNo}/change-pickup-date")
    @Transactional
    public ResponseEntity<?> changePickupDate(
            @PathVariable String orderNo,
            @RequestBody Map<String, String> payload,
            Authentication authentication) {

        String username = authentication.getName();
        Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                .orElseThrow(() -> new RuntimeException("訂單不存在或無權操作"));

        // 1. 校驗訂單狀態與配送方式
        if (order.getStatus() != Order.OrderStatus.PAID || !"STORE_PICKUP".equals(order.getDeliveryMethod())) {
            return ResponseEntity.badRequest().body(Result.error("只有已付款且為門店自取的訂單才能修改取貨日期"));
        }

        String newDateStr = payload.get("appointmentDate");
        if (newDateStr == null || newDateStr.isEmpty()) {
            return ResponseEntity.badRequest().body(Result.error("請提供新的預約日期"));
        }

        LocalDate newDate = LocalDate.parse(newDateStr);

        // 2. 提取 paidAt 的年月日 (格式: 2026-10-06 15:39:22.083567 -> 2026-10-06)
        LocalDate paidDate = order.getPaidAt().toLocalDate();

        // 3. 計算最大允許日期 (paidAt + offlinePaymentDays)
        Integer offlinePaymentDays = systemConfigService.getOfflinePaymentDays();
        if (offlinePaymentDays == null) offlinePaymentDays = 3; // 兜底默認 3 天

        LocalDate maxDate = paidDate.plusDays(offlinePaymentDays);

        // 4. 校驗新日期是否在合法範圍內
        if (newDate.isBefore(paidDate) || newDate.isAfter(maxDate)) {
            return ResponseEntity.badRequest().body(Result.error("預約日期必須在付款日期起的 " + offlinePaymentDays + " 天保留期限內"));
        }

        // 5. 更新訂單
        order.setAppointmentDate(newDate);
        orderRepository.save(order);

        return ResponseEntity.ok(Result.ok("取貨日期修改成功"));
    }
}