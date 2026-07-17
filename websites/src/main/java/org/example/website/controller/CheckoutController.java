package org.example.website.controller;

import lombok.RequiredArgsConstructor;
import org.example.website.dto.ApiResponse;
import org.example.website.entity.Order;
import org.example.website.entity.OrderItem;
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
public class CheckoutController {

    private final OrderService orderService;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final SystemConfigService systemConfigService;

    /**
     * 渲染结账页面：现在查询的是 OrderItem，而不是 Cart！
     */
    @GetMapping
    public String checkoutPage(@RequestParam String orderNo, Model model, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username).orElse(null);
        model.addAttribute("user", currentUser);

        Order order = orderService.getOrderByOrderNoAndUsername(orderNo, username);

        // 获取订单明细 (OrderItem)
        List<OrderItem> orderItems = orderItemRepository.findByOrder_OrderNo(orderNo);

        model.addAttribute("order", order);
        model.addAttribute("orderItems", orderItems); // 传给前端的变量名改为 orderItems
        model.addAttribute("shippingFee", systemConfigService.getShippingFee());
        model.addAttribute("freeShippingThreshold", systemConfigService.getFreeShippingThreshold());
        return "checkout";
    }

    /**
     * API: 前端點擊「去結賬」時調用，生成訂單並返回 orderNo
     */
    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<?> createOrder(Authentication authentication) {
        try {
            Order order = orderService.createOrder(authentication.getName());
            return ResponseEntity.ok(ApiResponse.okWithData("訂單創建成功", order.getOrderNo()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/api/pay")
    @ResponseBody
    public ResponseEntity<?> simulatePay(@RequestBody Map<String, Object> payload, Authentication authentication) {
        try {
            String orderNo = (String) payload.get("orderNo");

            // 1. 前端傳來的金額轉為 BigDecimal
            BigDecimal payAmount = new BigDecimal(payload.get("amount").toString());

            //  從 payload 中提取配送方式 (如果前端沒傳，默認為 null)
            String deliveryMethod = payload.containsKey("deliveryMethod") ? (String) payload.get("deliveryMethod") : null;

            //  傳遞 4 個參數給 Service 層
            Order order = orderService.simulatePayment(orderNo, authentication.getName(), payAmount, deliveryMethod);

            return ResponseEntity.ok(ApiResponse.okWithData("支付成功", order.getOrderNo()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 支付成功頁面
     */
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
     * 創建線下支付訂單 (Controller 僅負責路由與調用 Service)
     */
    @PostMapping("/api/offline-payment")
    @ResponseBody
    public ResponseEntity<?> createOfflinePayment(
            @RequestBody Map<String, Object> payload,
            Authentication authentication) {
        try {
            String orderNo = (String) payload.get("orderNo");
            String storeId = (String) payload.get("storeId");

            // 基礎參數校驗
            if (storeId == null || storeId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("請選擇線下支付店鋪"));
            }
            // 從 payload 中提取配送方式（線下支付時也可能有配送方式選擇）
            String deliveryMethod = payload.containsKey("deliveryMethod") ? (String) payload.get("deliveryMethod") : "STORE_PICKUP";

            //  核心：調用 Service 層處理業務與數據庫操作
            Order order = orderService.processOfflinePayment(orderNo, authentication.getName(), storeId,deliveryMethod);

            // 構建返回數據
            Map<String, Object> data = new HashMap<>();
            data.put("orderNo", order.getOrderNo());
            data.put("storeId", storeId);

            return ResponseEntity.ok(ApiResponse.okWithData("訂單已創建，請前往店鋪支付", data));
        } catch (Exception e) {
            // 統一異常處理
            return ResponseEntity.badRequest().body(ApiResponse.error("創建失敗: " + e.getMessage()));
        }
    }

    @GetMapping("/offline-success")
    public String offlinePaymentSuccess(
            @RequestParam String orderNo,
            @RequestParam String storeId,
            Model model) {

        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);

        // 店铺信息
        Map<String, String> storeInfo = getStoreInfo(storeId);
        data.put("storeName", storeInfo.get("name"));
        data.put("storeAddress", storeInfo.get("address"));
        data.put("storePhone", storeInfo.get("phone"));
        data.put("storeHours", storeInfo.get("hours"));

        model.addAttribute("data", data);
        return "offline-payment-success";
    }

    private Map<String, String> getStoreInfo(String storeId) {
        Map<String, String> stores = new HashMap<>();
        stores.put("store-central", "中環店|中環皇后大道中 99 號|+852 2123 4567|10:00 AM - 8:00 PM");
        stores.put("store-tsimsatsui", "尖沙咀店|尖沙咀廣東道 88 號|+852 2234 5678|11:00 AM - 9:00 PM");
        stores.put("store-causeway", "銅鑼灣店|銅鑼灣軒尼詩道 500 號|+852 2345 6789|12:00 PM - 10:00 PM");

        String info = stores.getOrDefault(storeId, "未知店鋪|地址待定|電話待定|營業時間待定");
        String[] parts = info.split("\\|");

        Map<String, String> result = new HashMap<>();
        result.put("name", parts[0]);
        result.put("address", parts[1]);
        result.put("phone", parts[2]);
        result.put("hours", parts[3]);

        return result;
    }

    /**
     * API：在结账页面修改订单商品数量
     */
    @PutMapping("/api/update-item/{orderItemId}")
    @ResponseBody
    public ResponseEntity<?> updateOrderItem(
            @PathVariable Long orderItemId,
            @RequestParam Integer quantity,
            Authentication authentication) {
        try {
            OrderItem updatedItem = orderService.updateOrderItemQuantity(orderItemId, quantity, authentication.getName());
            Order order = updatedItem.getOrder(); // 獲取已經被 recalculateOrderTotal 更新過的訂單對象

            Map<String, Object> data = new HashMap<>();
            data.put("quantity", updatedItem.getQuantity());
            data.put("subtotal", updatedItem.getPrice().multiply(BigDecimal.valueOf(updatedItem.getQuantity())));
            data.put("newTotalAmount", order.getTotalAmount());
            data.put("shippingFee", order.getShippingFee()); // 【關鍵】必須返回最新運費

            return ResponseEntity.ok(ApiResponse.okWithData("更新成功", data));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/api/remove-item/{orderItemId}")
    @ResponseBody
    public ResponseEntity<?> removeOrderItem(
            @PathVariable Long orderItemId,
            Authentication authentication) {
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

            return ResponseEntity.ok(ApiResponse.okWithData("已移除", data));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("訂單已自動取消")) {
                return ResponseEntity.ok(ApiResponse.error("ORDER_EMPTY"));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}