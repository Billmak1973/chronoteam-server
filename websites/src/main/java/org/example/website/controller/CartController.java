package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Cart;
import org.example.website.entity.User;
import org.example.website.service.CartService;
import org.example.website.service.UserService;
import org.example.website.util.PaginationUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * 購物車控制器 (整合頁面渲染 + AJAX API)
 */
@Controller
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;
    private final UserService userService;

    public CartController(CartService cartService, UserService userService) {
        this.cartService = cartService;
        this.userService = userService;
    }

    // ==========================================
    // 頁面渲染 (從 PageController 遷移過來)
    // ==========================================

    /**
     * 購物車頁面 (支援分頁 + 日期分組)
     * 利用 PaginationUtils 統一生成智能分頁列表
     */
    @GetMapping("/view")
    public String viewCartPage(
            @RequestParam(defaultValue = "1") int page,
            Model model,
            Authentication authentication) {

        // 1. 權限校驗
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return "redirect:/";
        }

        String username = authentication.getName();
        User user = userService.findByUsername(username);
        model.addAttribute("user", user);

        // 2. 獲取所有購物車數據
        List<Cart> allCartItems = cartService.getCartItems(username);
        long cartCount = cartService.getCartCount(username);

        // 3. 計算總價 (基於所有選中商品，不受分頁影響)
        double totalAmount = allCartItems.stream()
                .filter(Cart::getSelected)
                .mapToDouble(item -> item.getPrice().doubleValue() * item.getQuantity())
                .sum();

        // 4. 內存分頁計算
        int size = 20;
        int totalElements = allCartItems.size();
        int totalPages = (totalElements == 0) ? 1 : (int) Math.ceil((double) totalElements / size);

        // 邊界檢查 (確保 page 在合法範圍內)
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        // 5. 按時間倒序排序後截取當前頁數據
        allCartItems.sort(Comparator.comparing(Cart::getCreatedAt).reversed());
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Cart> pagedCartItems = (fromIndex < totalElements)
                ? allCartItems.subList(fromIndex, toIndex)
                : new ArrayList<>();

        // 6. 僅對【當前頁數據】進行日期分組
        // 6. 僅對【當前頁數據】進行日期分組
        // 【修復】：將 Lambda 提取為獨立變量，解決 Java 泛型推斷失敗問題
        Function<Cart, String> dateClassifier = item -> {
            LocalDate date = item.getCreatedAt().toLocalDate();
            LocalDate today = LocalDate.now();
            if (date.getYear() == today.getYear() && date.getMonthValue() == today.getMonthValue()) {
                return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else if (date.getYear() == today.getYear()) {
                return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            } else {
                return String.valueOf(date.getYear());
            }
        };

        Collector<Cart, ?, List<Cart>> sortedListCollector = Collectors.collectingAndThen(
                Collectors.toList(),
                list -> {
                    list.sort(Comparator.comparing(Cart::getCreatedAt).reversed());
                    return list;
                }
        );

        Map<String, List<Cart>> groupedPagedCartItems = pagedCartItems.stream()
                .collect(Collectors.groupingBy(
                        dateClassifier,
                        () -> new TreeMap<>(Comparator.reverseOrder()),
                        sortedListCollector
                ));

        // 7. 使用 PaginationUtils 生成智能分頁 (傳入 0-based 索引)
        List<PaginationUtils.PageItem> smartPages =
                PaginationUtils.generateSmartPagination(page - 1, totalPages);

        // 8. 傳遞數據給 Thymeleaf
        model.addAttribute("cartItems", pagedCartItems);
        model.addAttribute("groupedCartItems", groupedPagedCartItems);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("cartCount", cartCount);
        model.addAttribute("currentPage", page);         // 1-based 給前端顯示
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("smartPages", smartPages);

        return "cart-detail";
    }

    // ==========================================
    // AJAX API (保持不變)
    // ==========================================

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    @PostMapping("/api/add/{productId}")
    @ResponseBody
    public ResponseEntity<ApiResponse> addToCart(@PathVariable Integer productId,
                                                 Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }
        try {
            Cart cart = cartService.addToCart(authentication.getName(), productId);
            return ResponseEntity.ok(ApiResponse.ok("已加入購物車"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/api/update/{cartId}")
    @ResponseBody
    public ResponseEntity<ApiResponse> updateQuantity(@PathVariable Long cartId,
                                                      @RequestParam Integer quantity,
                                                      Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }
        try {
            Cart cart = cartService.updateQuantity(authentication.getName(), cartId, quantity);
            return ResponseEntity.ok(cart == null
                    ? ApiResponse.ok("已移除")
                    : ApiResponse.ok("已更新"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/api/remove/{productId}")
    @ResponseBody
    public ResponseEntity<ApiResponse> removeFromCart(@PathVariable Integer productId,
                                                      Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }
        try {
            cartService.removeFromCart(authentication.getName(), productId);
            return ResponseEntity.ok(ApiResponse.ok("已移除"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCartList(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        if (!isAuthenticated(authentication)) {
            response.put("success", true);
            response.put("cartItems", List.of());
            response.put("cartCount", 0);
            response.put("totalAmount", 0.0);
            return ResponseEntity.ok(response);
        }
        String username = authentication.getName();
        List<Cart> cartItems = cartService.getCartItems(username);
        double totalAmount = cartItems.stream()
                .filter(Cart::getSelected)
                .mapToDouble(item -> item.getPrice().doubleValue() * item.getQuantity())
                .sum();
        response.put("success", true);
        response.put("cartItems", cartItems);
        response.put("cartCount", cartService.getCartCount(username));
        response.put("totalAmount", totalAmount);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCartCount(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        if (!isAuthenticated(authentication)) {
            response.put("success", true);
            response.put("count", 0);
            return ResponseEntity.ok(response);
        }
        response.put("success", true);
        response.put("count", cartService.getCartCount(authentication.getName()));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/api/toggle-selection/{cartId}")
    @ResponseBody
    public ResponseEntity<ApiResponse> toggleSelection(@PathVariable Long cartId,
                                                       @RequestParam Boolean isSelected,
                                                       Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }
        cartService.toggleSelection(authentication.getName(), cartId, isSelected);
        return ResponseEntity.ok(ApiResponse.ok("更新成功"));
    }
}