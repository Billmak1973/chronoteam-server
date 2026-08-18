package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
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
@Tag(name = "購物車管理", description = "用戶購物車相關操作接口 (包含頁面渲染與 AJAX API)")
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
    @Hidden // 隱藏純頁面渲染接口，保持 Swagger UI 專注於 REST API
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
    // AJAX API (已加上完整 Swagger 註解)
    // ==========================================

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    @Operation(
            summary = "添加商品到購物車",
            description = "將指定商品加入當前登入用戶的購物車。若該商品已存在，則數量自動 +1。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "添加成功"),
            @ApiResponse(responseCode = "400", description = "庫存不足或商品不存在"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PostMapping("/api/add/{productId}")
    @ResponseBody
    public ResponseEntity<Result> addToCart(
            @Parameter(description = "商品ID", example = "1", required = true)
            @PathVariable Integer productId,
            Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }
        try {
            Cart cart = cartService.addToCart(authentication.getName(), productId);
            return ResponseEntity.ok(Result.ok("已加入購物車"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @Operation(
            summary = "更新購物車商品數量",
            description = "修改指定購物車項目的數量。若數量 <= 0，系統會自動移除該項目。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新或移除成功"),
            @ApiResponse(responseCode = "400", description = "庫存不足或數量無效"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PutMapping("/api/update/{cartId}")
    @ResponseBody
    public ResponseEntity<Result> updateQuantity(
            @Parameter(description = "購物車項目ID", example = "10", required = true)
            @PathVariable Long cartId,
            @Parameter(description = "新數量 (必須 > 0)", example = "2", required = true)
            @RequestParam Integer quantity,
            Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }
        try {
            Cart cart = cartService.updateQuantity(authentication.getName(), cartId, quantity);
            return ResponseEntity.ok(cart == null
                    ? Result.ok("已移除")
                    : Result.ok("已更新"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @Operation(
            summary = "從購物車移除商品",
            description = "根據商品ID，將該商品從當前用戶的購物車中徹底移除。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "移除成功"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @DeleteMapping("/api/remove/{productId}")
    @ResponseBody
    public ResponseEntity<Result> removeFromCart(
            @Parameter(description = "商品ID", example = "1", required = true)
            @PathVariable Integer productId,
            Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }
        try {
            cartService.removeFromCart(authentication.getName(), productId);
            return ResponseEntity.ok(Result.ok("已移除"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @Operation(
            summary = "獲取購物車列表",
            description = "獲取當前登入用戶的購物車所有項目、總件數及總價 (主要供導航欄下拉菜單或購物車頁面初始化使用)。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功，返回包含 cartItems, cartCount, totalAmount 的 JSON"),
            @ApiResponse(responseCode = "401", description = "未登入 (返回空列表與 0)")
    })
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

    @Operation(
            summary = "獲取購物車商品總數",
            description = "獲取當前登入用戶購物車內的商品總件數 (主要供導航欄右上角角標使用)。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功，返回包含 count 的 JSON"),
            @ApiResponse(responseCode = "401", description = "未登入 (返回 0)")
    })
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

    @Operation(
            summary = "切換購物車商品選中狀態",
            description = "用於購物車頁面勾選/取消勾選單個商品，以便動態計算結算總價。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "切換成功"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PutMapping("/api/toggle-selection/{cartId}")
    @ResponseBody
    public ResponseEntity<Result> toggleSelection(
            @Parameter(description = "購物車項目ID", example = "10", required = true)
            @PathVariable Long cartId,
            @Parameter(description = "是否選中 (true/false)", example = "true", required = true)
            @RequestParam Boolean isSelected,
            Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }
        cartService.toggleSelection(authentication.getName(), cartId, isSelected);
        return ResponseEntity.ok(Result.ok("更新成功"));
    }
}