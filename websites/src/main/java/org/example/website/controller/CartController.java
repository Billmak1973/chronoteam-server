package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.Cart;
import org.example.website.entity.OfflineStore;
import org.example.website.entity.User;
import org.example.website.repository.CartRepository;
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
    private final CartRepository cartRepository;

    public CartController(CartService cartService, UserService userService, CartRepository cartRepository) {
        this.cartService = cartService;
        this.userService = userService;
        this.cartRepository = cartRepository;
    }


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

        // 2. 獲取所有購物車數據 (用於分頁與配送選項構建)
        List<Cart> allCartItems = cartService.getCartItems(username);
        long cartCount = cartService.getCartCount(username);

        // 【核心修復】：不再依賴內存分頁後的列表計算，而是直接查庫獲取全局已選中數據
        // 確保無論用戶在第幾頁，統計數據都來自數據庫真實狀態
        List<Cart> allSelectedItems = cartRepository.findByUser_UsernameAndSelectedTrue(username);

        long selectedCount = allSelectedItems.size();
        double globalTotalAmount = allSelectedItems.stream()
                .mapToDouble(item -> item.getPrice().doubleValue() * item.getQuantity())
                .sum();

        // 3. 內存分頁計算
        int size = 20;
        int totalElements = allCartItems.size();
        int totalPages = (totalElements == 0) ? 1 : (int) Math.ceil((double) totalElements / size);

        // 邊界檢查 (確保 page 在合法範圍內)
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        // 4. 按時間倒序排序後截取當前頁數據
        allCartItems.sort(Comparator.comparing(Cart::getCreatedAt).reversed());
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Cart> pagedCartItems = (fromIndex < totalElements)
                ? allCartItems.subList(fromIndex, toIndex)
                : new ArrayList<>();

        // 5. 僅對【當前頁數據】進行日期分組
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

        // 6. 使用 PaginationUtils 生成智能分頁 (傳入 0-based 索引)
        List<PaginationUtils.PageItem> smartPages =
                PaginationUtils.generateSmartPagination(page - 1, totalPages);

        // ==========================================
        // 【新增】構建配送方式下拉選項與智能默認值
        // ==========================================
        List<Map<String, Object>> deliveryOptions = new ArrayList<>();

        // 檢查是否有線上商品 (isOffline == false 或 null)
        boolean hasOnlineItems = allCartItems.stream()
                .anyMatch(item -> !Boolean.TRUE.equals(item.getIsOffline()));

        if (hasOnlineItems) {
            Map<String, Object> onlineOption = new HashMap<>();
            onlineOption.put("value", "ONLINE");
            onlineOption.put("label", "📦 網購配送 (線上總倉)");
            onlineOption.put("storeId", null);
            deliveryOptions.add(onlineOption);
        }

        // 檢查是否有線下商品，並按門店分組 (使用 LinkedHashMap 保持插入順序)
        Map<Long, String> offlineStoresMap = new LinkedHashMap<>();
        for (Cart item : allCartItems) {
            if (Boolean.TRUE.equals(item.getIsOffline()) && item.getStore() != null) {
                Long storeId = item.getStore().getStoreId();
                // 避免重複添加同一個門店
                if (!offlineStoresMap.containsKey(storeId)) {
                    offlineStoresMap.put(storeId, item.getStore().getName());
                }
            }
        }

        for (Map.Entry<Long, String> entry : offlineStoresMap.entrySet()) {
            Map<String, Object> storeOption = new HashMap<>();
            storeOption.put("value", "STORE_" + entry.getKey()); // 標識符: STORE_門店ID
            storeOption.put("label", "🏪 門店自取 - " + entry.getValue());
            storeOption.put("storeId", entry.getKey());
            deliveryOptions.add(storeOption);
        }

        // 【新增】智能計算默認配送方式
        String defaultDeliveryValue = "ONLINE"; // 默認為網購

        // 檢查當前購物車中是否有「已勾選」且屬於「線下門店」的商品
        boolean hasSelectedOfflineItems = allCartItems.stream()
                .filter(Cart::getSelected) // 只看已勾選的商品
                .anyMatch(item -> Boolean.TRUE.equals(item.getIsOffline()) && item.getStore() != null);

        if (hasSelectedOfflineItems) {
            // 獲取第一個已勾選的線下商品的門店ID
            Long firstStoreId = allCartItems.stream()
                    .filter(Cart::getSelected)
                    .filter(item -> Boolean.TRUE.equals(item.getIsOffline()) && item.getStore() != null)
                    .findFirst()
                    .map(item -> item.getStore().getStoreId())
                    .orElse(null);

            if (firstStoreId != null) {
                defaultDeliveryValue = "STORE_" + firstStoreId;
            }
        }

        // 7. 傳遞數據給 Thymeleaf (使用全局真實數據)
        model.addAttribute("cartItems", pagedCartItems);
        model.addAttribute("groupedCartItems", groupedPagedCartItems);
        model.addAttribute("totalAmount", globalTotalAmount); //  使用全局總計
        model.addAttribute("selectedCount", selectedCount);   //  使用全局數量
        model.addAttribute("cartCount", cartCount);
        model.addAttribute("currentPage", page);         // 1-based 給前端顯示
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("smartPages", smartPages);
        model.addAttribute("deliveryOptions", deliveryOptions);
        model.addAttribute("defaultDeliveryValue", defaultDeliveryValue);

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


    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCartList(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        // 1. 未登入或無權限處理
        if (!isAuthenticated(authentication)) {
            response.put("success", true);
            response.put("cartItems", List.of());
            response.put("cartCount", 0);
            response.put("totalAmount", 0.0);
            return ResponseEntity.ok(response);
        }

        String username = authentication.getName();
        List<Cart> cartItems = cartService.getCartItems(username);

        // 2. 【原有邏輯】手動構建清洗後的購物車商品數據
        List<Map<String, Object>> cleanCartItems = new ArrayList<>();
        for (Cart item : cartItems) {
            Map<String, Object> map = new HashMap<>();
            map.put("cartId", item.getCartId());
            map.put("quantity", item.getQuantity());
            map.put("price", item.getPrice());
            map.put("selected", item.getSelected());

            // 處理商品基本信息
            if (item.getProduct() != null) {
                Map<String, Object> prodMap = new HashMap<>();
                prodMap.put("productId", item.getProduct().getProductId());
                prodMap.put("description", item.getProduct().getDescription());
                prodMap.put("image", item.getProduct().getImage());
                prodMap.put("stock", item.getProduct().getStock());
                map.put("product", prodMap);
            }

            // 判斷是否為線下門店商品，並獲取門店名稱
            if (Boolean.TRUE.equals(item.getIsOffline()) && item.getStore() != null) {
                map.put("storeName", item.getStore().getName());
            } else {
                map.put("storeName", null);
            }

            cleanCartItems.add(map);
        }

        // 3. 【新增邏輯】構建配送方式下拉選項
        List<Map<String, Object>> deliveryOptions = new ArrayList<>();

        // 檢查是否有線上商品 (isOffline == false 或 null)
        boolean hasOnlineItems = cartItems.stream()
                .anyMatch(item -> !Boolean.TRUE.equals(item.getIsOffline()));

        if (hasOnlineItems) {
            Map<String, Object> onlineOption = new HashMap<>();
            onlineOption.put("value", "ONLINE");
            onlineOption.put("label", "📦 網購配送 (線上總倉)");
            deliveryOptions.add(onlineOption);
        }

        // 檢查是否有線下商品，並按門店分組 (使用 LinkedHashMap 保持插入順序)
        Map<Long, String> offlineStoresMap = new LinkedHashMap<>();
        for (Cart item : cartItems) {
            if (Boolean.TRUE.equals(item.getIsOffline()) && item.getStore() != null) {
                Long storeId = item.getStore().getStoreId();
                // 避免重複添加同一個門店
                if (!offlineStoresMap.containsKey(storeId)) {
                    offlineStoresMap.put(storeId, item.getStore().getName());
                }
            }
        }

        for (Map.Entry<Long, String> entry : offlineStoresMap.entrySet()) {
            Map<String, Object> storeOption = new HashMap<>();
            storeOption.put("value", "STORE_" + entry.getKey()); // 標識符: STORE_門店ID
            storeOption.put("label", "🏪 門店自取 - " + entry.getValue());
            storeOption.put("storeId", entry.getKey());
            deliveryOptions.add(storeOption);
        }

        // 【新增】智能計算默認配送方式
        String defaultDeliveryValue = "ONLINE"; // 默認為網購

        // 檢查當前購物車中是否有「已勾選」且屬於「線下門店」的商品
        boolean hasSelectedOfflineItems = cartItems.stream()
                .filter(Cart::getSelected) // 只看已勾選的商品
                .anyMatch(item -> Boolean.TRUE.equals(item.getIsOffline()) && item.getStore() != null);

        if (hasSelectedOfflineItems) {
            // 獲取第一個已勾選的線下商品的門店ID
            Long firstStoreId = cartItems.stream()
                    .filter(Cart::getSelected)
                    .filter(item -> Boolean.TRUE.equals(item.getIsOffline()) && item.getStore() != null)
                    .findFirst()
                    .map(item -> item.getStore().getStoreId())
                    .orElse(null);

            if (firstStoreId != null) {
                defaultDeliveryValue = "STORE_" + firstStoreId;
            }
        }

        // 4. 計算選中商品的總金額
        double totalAmount = cartItems.stream()
                .filter(Cart::getSelected)
                .mapToDouble(item -> item.getPrice().doubleValue() * item.getQuantity())
                .sum();

        // 【核心修復】：統計全局已選中的商品件數
        long selectedCount = cartItems.stream()
                .filter(Cart::getSelected)
                .count();

        // 5. 組裝最終響應
        response.put("success", true);
        response.put("cartItems", cleanCartItems);
        response.put("cartCount", cartService.getCartCount(username));
        response.put("totalAmount", totalAmount);
        response.put("selectedCount", selectedCount);
        response.put("deliveryOptions", deliveryOptions); // 返回結構化選項列表
        response.put("defaultDeliveryValue", defaultDeliveryValue); // 將默認值傳給前端

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

    @Operation(
            summary = "添加線下門店商品到購物車",
            description = "將指定門店的指定商品加入購物車，並標記為線下訂單 (isOffline=true)，同時預留庫存。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "添加成功"),
            @ApiResponse(responseCode = "400", description = "庫存不足或參數錯誤"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PostMapping("/api/add-store-item")
    @ResponseBody
    public ResponseEntity<Result> addStoreItemToCart(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "包含 productId, storeId, quantity 的請求體",
                    required = true
            )
            @RequestBody Map<String, Integer> payload,
            Authentication authentication) {

        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }

        try {
            Integer productId = payload.get("productId");
            Long storeId = Long.valueOf(payload.get("storeId"));
            Integer quantity = payload.getOrDefault("quantity", 1);

            if (productId == null || storeId == null) {
                return ResponseEntity.badRequest().body(Result.error("缺少必要參數"));
            }

            // 調用 Service 層處理業務邏輯
            Cart cart = cartService.addStoreItemToCart(
                    authentication.getName(),
                    productId,
                    storeId,
                    quantity
            );

            return ResponseEntity.ok(Result.ok("已加入購物車"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @PutMapping("/api/update-selection-by-delivery")
    @ResponseBody
    public ResponseEntity<Result> updateSelectionByDelivery(
            Authentication authentication,
            @RequestParam(required = false) Long storeId) {

        if (!isAuthenticated(authentication)) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }

        String username = authentication.getName();
        List<Cart> allCartItems = cartService.getCartItems(username);

        int deselectedCount = 0;

        for (Cart item : allCartItems) {
            boolean shouldDeselect = false;

            // 情況 A: 選擇了特定門店自取 (storeId != null)
            // 規則：取消選中 [網購商品] 以及 [非該門店的線下商品]
            if (storeId != null) {
                // 如果是網購商品 (isOffline 為 false 或 null)，取消選中
                if (!Boolean.TRUE.equals(item.getIsOffline())) {
                    shouldDeselect = true;
                }
                // 如果是線下商品，但店鋪 ID 不匹配，取消選中
                else if (item.getStore() == null || !item.getStore().getStoreId().equals(storeId)) {
                    shouldDeselect = true;
                }
            }
            // 情況 B: 選擇了網購配送 (storeId == null)
            // 規則：取消選中 [所有線下商品]
            else {
                if (Boolean.TRUE.equals(item.getIsOffline())) {
                    shouldDeselect = true;
                }
            }

            // 【核心邏輯】：只有當需要取消選中，且當前確實是被選中的時候，才執行更新
            if (shouldDeselect && Boolean.TRUE.equals(item.getSelected())) {
                item.setSelected(false);
                cartRepository.save(item);
                deselectedCount++;
            }
            // 注意：這裡沒有 else 分支去設置 setSelected(true)，所以原本沒選中的商品不會被自動選中
        }

        return ResponseEntity.ok(Result.ok("已清理不符合配送方式的商品 (" + deselectedCount + " 項取消選中)"));
    }
}