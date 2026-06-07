package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Cart;
import org.example.website.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    //  輔助方法：判斷是否已登入
    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    // 添加商品到购物车（必須登入）
    @PostMapping("/add/{productId}")
    public ResponseEntity<ApiResponse> addToCart(@PathVariable Integer productId,
                                                 Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }
        try {
            String username = authentication.getName();
            Cart cart = cartService.addToCart(username, productId);
            return ResponseEntity.ok(ApiResponse.ok("已加入購物車"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // 更新购物车商品数量（必須登入）
    @PutMapping("/update/{cartId}")
    public ResponseEntity<ApiResponse> updateQuantity(@PathVariable Long cartId,
                                                      @RequestParam Integer quantity,
                                                      Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }
        try {
            String username = authentication.getName();
            Cart cart = cartService.updateQuantity(username, cartId, quantity);
            if (cart == null) {
                return ResponseEntity.ok(ApiResponse.ok("已移除"));
            }
            return ResponseEntity.ok(ApiResponse.ok("已更新"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // 从购物车移除商品（必須登入）
    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<ApiResponse> removeFromCart(@PathVariable Integer productId,
                                                      Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }
        try {
            String username = authentication.getName();
            cartService.removeFromCart(username, productId);
            return ResponseEntity.ok(ApiResponse.ok("已移除"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    //  获取购物车列表（兼容未登入用戶，返回空數據）
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getCartList(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        //  關鍵：如果未登入，返回空購物車數據，避免 NullPointerException
        if (!isAuthenticated(authentication)) {
            response.put("success", true);
            response.put("cartItems", List.of()); // 空列表
            response.put("cartCount", 0);
            response.put("totalAmount", 0.0);
            return ResponseEntity.ok(response);
        }

        String username = authentication.getName();
        List<Cart> cartItems = cartService.getCartItems(username);
        long cartCount = cartService.getCartCount(username);

        // 计算总价
        double totalAmount = cartItems.stream()
                .mapToDouble(item -> item.getPrice().doubleValue() * item.getQuantity())
                .sum();

        response.put("success", true);
        response.put("cartItems", cartItems);
        response.put("cartCount", cartCount);
        response.put("totalAmount", totalAmount);

        return ResponseEntity.ok(response);
    }

    //  获取购物车商品数量（兼容未登入用戶，返回 0）
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getCartCount(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        // 關鍵：如果未登入，返回 0
        if (!isAuthenticated(authentication)) {
            response.put("success", true);
            response.put("count", 0);
            return ResponseEntity.ok(response);
        }

        String username = authentication.getName();
        long count = cartService.getCartCount(username);

        response.put("success", true);
        response.put("count", count);

        return ResponseEntity.ok(response);
    }
}