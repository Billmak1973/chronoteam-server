package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.website.dto.Result;
import org.example.website.entity.OfflineStore;
import org.example.website.entity.Order;
import org.example.website.entity.OrderItem;
import org.example.website.repository.OfflineStoreRepository;
import org.example.website.repository.OrderItemRepository;
import org.example.website.service.OrderService;
import org.example.website.entity.User;
import org.example.website.repository.UserRepository;
import org.example.website.service.SystemConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
@Tag(name = "結帳與訂單管理", description = "處理訂單創建、線上/線下支付、訂單明細修改等相關接口")
public class CheckoutController {

    private final OrderService orderService;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final SystemConfigService systemConfigService;
    private final OfflineStoreRepository offlineStoreRepository;

    /**
     * 渲染結賬頁面：查詢 OrderItem，而不是 Cart
     */
    @Hidden // 隱藏純頁面渲染接口，保持 Swagger UI 專注於 REST API
    @GetMapping
    public String checkoutPage(@RequestParam String orderNo, Model model, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username).orElse(null);
        model.addAttribute("user", currentUser);

        Order order = orderService.getOrderByOrderNoAndUsername(orderNo, username);

        // 獲取訂單明細 (OrderItem)
        List<OrderItem> orderItems = orderItemRepository.findByOrder_OrderNo(orderNo);

        model.addAttribute("order", order);
        model.addAttribute("orderItems", orderItems);
        model.addAttribute("shippingFee", systemConfigService.getShippingFee());
        model.addAttribute("freeShippingThreshold", systemConfigService.getFreeShippingThreshold());

        // 查詢所有啟用的店鋪並加入 Model
        List<OfflineStore> activeStores = offlineStoreRepository.findByIsActiveTrue();
        model.addAttribute("activeStores", activeStores);

        return "checkout";
    }

    /**
     * API: 前端點擊「去結賬」時調用，生成訂單並返回 orderNo
     */
    @Operation(
            summary = "創建訂單",
            description = "根據用戶當前購物車中已選中的商品，生成一筆新的待付款訂單，並清空已選中的購物車項目。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "訂單創建成功，返回 orderNo", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "購物車為空或商品庫存不足"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<?> createOrder(
            @Parameter(hidden = true) Authentication authentication) {
        try {
            Order order = orderService.createOrder(authentication.getName());
            return ResponseEntity.ok(Result.okWithData("訂單創建成功", order.getOrderNo()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * API: 模擬線上支付
     */
    @Operation(
            summary = "模擬線上支付",
            description = "模擬線上支付流程，校驗前端傳來的金額與後端計算是否一致，更新訂單狀態為已付款，並扣減對應商品庫存。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "支付成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "訂單狀態異常、金額不符或庫存不足"),
            @ApiResponse(responseCode = "401", description = "未登入或無權操作此訂單")
    })
    @PostMapping("/api/pay")
    @ResponseBody
    public ResponseEntity<?> simulatePay(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "支付請求參數，需包含: orderNo (String), amount (Number), deliveryMethod (String, 可選), storeId (Number, 可選)",
                    required = true
            )
            @RequestBody Map<String, Object> payload,
            @Parameter(hidden = true) Authentication authentication) {
        try {
            String orderNo = (String) payload.get("orderNo");
            // 1. 前端傳來的金額轉為 BigDecimal
            BigDecimal payAmount = new BigDecimal(payload.get("amount").toString());
            // 2. 從 payload 中提取配送方式
            String deliveryMethod = payload.containsKey("deliveryMethod") ? (String) payload.get("deliveryMethod") : null;
            // 3. 從 payload 中提取 storeId
            Long storeId = payload.containsKey("storeId") ? Long.valueOf(payload.get("storeId").toString()) : null;

            // 將 storeId 作為第 5 個參數傳遞給 Service 層
            Order order = orderService.simulatePayment(orderNo, authentication.getName(), payAmount, deliveryMethod, storeId);

            return ResponseEntity.ok(Result.okWithData("支付成功", order.getOrderNo()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 渲染支付成功頁面
     */
    @Hidden // 隱藏純頁面渲染接口
    @GetMapping("/payment-success")
    public String paymentSuccess(@RequestParam String orderNo, Model model, Authentication authentication) {
        try {
            String username = authentication.getName();
            // 查詢訂單信息
            Order order = orderService.getOrderByOrderNoAndUsername(orderNo, username);
            model.addAttribute("order", order);
            return "payment-success";  // 返回 templates/payment-success.html
        } catch (Exception e) {
            // 如果訂單不存在或無權訪問，跳轉到首頁
            return "redirect:/";
        }
    }

    /**
     * API: 創建線下支付訂單
     */
    @Operation(
            summary = "創建線下支付訂單",
            description = "用戶選擇線下門店支付，生成訂單並關聯指定的門店信息，同時扣減庫存。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "訂單創建成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "未選擇門店或訂單狀態異常"),
            @ApiResponse(responseCode = "401", description = "未登入或無權操作此訂單")
    })
    @PostMapping("/api/offline-payment")
    @ResponseBody
    public ResponseEntity<?> createOfflinePayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "線下支付請求參數，需包含: orderNo (String), storeId (Number), deliveryMethod (String, 可選)",
                    required = true
            )
            @RequestBody Map<String, Object> payload,
            @Parameter(hidden = true) Authentication authentication) {
        try {
            String orderNo = (String) payload.get("orderNo");
            Long storeId = payload.containsKey("storeId") ? Long.valueOf(payload.get("storeId").toString()) : null;

            // 基礎參數校驗
            if (storeId == null) {
                return ResponseEntity.badRequest().body(Result.error("請選擇線下支付店鋪"));
            }

            // 從 payload 中提取配送方式（線下支付時也可能有配送方式選擇）
            String deliveryMethod = payload.containsKey("deliveryMethod") ? (String) payload.get("deliveryMethod") : "STORE_PICKUP";

            // 核心：調用 Service 層處理業務與數據庫操作
            Order order = orderService.processOfflinePayment(orderNo, authentication.getName(), storeId, deliveryMethod);

            // 構建返回數據
            Map<String, Object> data = new HashMap<>();
            data.put("orderNo", order.getOrderNo());
            data.put("storeId", storeId);

            return ResponseEntity.ok(Result.okWithData("訂單已創建，請前往店鋪支付", data));
        } catch (Exception e) {
            // 統一異常處理
            return ResponseEntity.badRequest().body(Result.error("創建失敗: " + e.getMessage()));
        }
    }

    /**
     * 渲染線下支付成功頁面
     */
    @Hidden // 隱藏純頁面渲染接口
    @GetMapping("/offline-success")
    public String offlinePaymentSuccess(
            @RequestParam String orderNo,
            @RequestParam String storeId,
            Model model) {

        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);

        // 改為從資料庫查詢店鋪信息
        OfflineStore store = offlineStoreRepository.findByStoreCode(storeId).orElse(null);

        if (store != null) {
            data.put("storeName", store.getName());
            data.put("storeAddress", store.getAddress());
            data.put("storePhone", store.getPhone() != null ? store.getPhone() : "未提供");
            data.put("storeHours", store.getHours() != null ? store.getHours() : "未提供");
        } else {
            data.put("storeName", "未知店鋪");
            data.put("storeAddress", "地址待定");
            data.put("storePhone", "電話待定");
            data.put("storeHours", "營業時間待定");
        }

        model.addAttribute("data", data);
        return "offline-payment-success";
    }

    /**
     * API：在結賬頁面修改訂單商品數量
     */
    @Operation(
            summary = "修改訂單商品數量",
            description = "在結賬頁面動態修改某個訂單商品的數量，後端會重新校驗庫存並重新計算訂單總價與運費。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功，返回最新數量、小計、總價與運費", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "庫存不足、數量無效或訂單已支付"),
            @ApiResponse(responseCode = "401", description = "未登入或無權操作此訂單")
    })
    @PutMapping("/api/update-item/{orderItemId}")
    @ResponseBody
    public ResponseEntity<?> updateOrderItem(
            @Parameter(description = "訂單明細ID (OrderItem ID)", required = true, example = "1")
            @PathVariable Long orderItemId,
            @Parameter(description = "新的商品數量 (必須 > 0)", required = true, example = "2")
            @RequestParam Integer quantity,
            @Parameter(hidden = true) Authentication authentication) {
        try {
            OrderItem updatedItem = orderService.updateOrderItemQuantity(orderItemId, quantity, authentication.getName());
            Order order = updatedItem.getOrder(); // 獲取已經被 recalculateOrderTotal 更新過的訂單對象

            Map<String, Object> data = new HashMap<>();
            data.put("quantity", updatedItem.getQuantity());
            data.put("subtotal", updatedItem.getPrice().multiply(BigDecimal.valueOf(updatedItem.getQuantity())));
            data.put("newTotalAmount", order.getTotalAmount());
            data.put("shippingFee", order.getShippingFee()); // 【關鍵】必須返回最新運費

            return ResponseEntity.ok(Result.okWithData("更新成功", data));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * API：從待支付訂單中移除某個商品
     */
    @Operation(
            summary = "移除訂單商品",
            description = "從待支付訂單中移除某個商品，並重新計算總價。若訂單內已無商品，則自動取消該訂單。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "移除成功，返回最新總價與運費。若訂單為空則返回 ORDER_EMPTY 錯誤碼", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "訂單已支付或商品不存在"),
            @ApiResponse(responseCode = "401", description = "未登入或無權操作此訂單")
    })
    @DeleteMapping("/api/remove-item/{orderItemId}")
    @ResponseBody
    public ResponseEntity<?> removeOrderItem(
            @Parameter(description = "訂單明細ID (OrderItem ID)", required = true, example = "1")
            @PathVariable Long orderItemId,
            @Parameter(hidden = true) Authentication authentication) {
        try {
            // 先獲取 orderNo，以便刪除後查詢最新狀態
            OrderItem item = orderItemRepository.findById(orderItemId).orElseThrow(() -> new RuntimeException("訂單商品不存在"));
            String orderNo = item.getOrder().getOrderNo();

            // 執行刪除 (內部會調用 recalculateOrderTotal)
            orderService.removeOrderItem(orderItemId, authentication.getName());

            // 如果沒拋異常，說明訂單還在，重新查詢最新訂單狀態返回給前端
            Order updatedOrder = orderService.getOrderByOrderNoAndUsername(orderNo, authentication.getName());

            Map<String, Object> data = new HashMap<>();
            data.put("newTotalAmount", updatedOrder.getTotalAmount());
            data.put("shippingFee", updatedOrder.getShippingFee()); // 【關鍵】必須返回最新運費

            return ResponseEntity.ok(Result.okWithData("已移除", data));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("訂單已自動取消")) {
                return ResponseEntity.ok(Result.error("ORDER_EMPTY"));
            }
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}