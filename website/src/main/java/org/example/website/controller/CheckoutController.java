package org.example.website.controller;

import lombok.RequiredArgsConstructor;
import org.example.website.dto.ApiResponse;
import org.example.website.entity.Order;
import org.example.website.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final OrderService orderService;

    /**
     * 渲染結賬/模擬支付頁面
     */
    @GetMapping
    public String checkoutPage(@RequestParam String orderNo, Model model, Authentication authentication) {
        String username = authentication.getName();
        Order order = orderService.getOrderByOrderNoAndUsername(orderNo, username); // 需在 Service 加個簡單查詢方法，或直接查 Repository

        // 為簡化，這裡直接假設 order 已驗證
        model.addAttribute("order", order);
        return "checkout"; // 對應 templates/checkout.html
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

    /**
     * API: 前端點擊「確認支付」時調用
     */
    @PostMapping("/api/pay")
    @ResponseBody
    public ResponseEntity<?> simulatePay(@RequestBody Map<String, Object> payload, Authentication authentication) {
        try {
            String orderNo = (String) payload.get("orderNo");
            // 前端傳來的金額轉為 BigDecimal
            BigDecimal payAmount = new BigDecimal(payload.get("amount").toString());

            Order order = orderService.simulatePayment(orderNo, authentication.getName(), payAmount);

            return ResponseEntity.ok(ApiResponse.okWithData("支付成功", order.getOrderNo()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 🟢 新增：支付成功頁面
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
     * 🟢 新增：創建線下支付訂單 (Controller 僅負責路由與調用 Service)
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

            // 🟢 核心：調用 Service 層處理業務與數據庫操作
            Order order = orderService.processOfflinePayment(orderNo, authentication.getName(), storeId);

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
}