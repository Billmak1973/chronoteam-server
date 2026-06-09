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
}