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
import org.example.website.entity.Product;
import org.example.website.entity.WatchCondition;
import org.example.website.repository.ProductRepository;
import org.example.website.service.ProductService;
import org.example.website.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@Tag(name = "後台商品管理", description = "管理員專屬的商品查詢、篩選、排序與新建接口")
public class AdminProductController {

    private final ProductService productService;
    private final ProductRepository productRepository;

    public AdminProductController(ProductService productService, ProductRepository productRepository) {
        this.productService = productService;
        this.productRepository = productRepository;
    }

    /**
     * 1. 頁面骨架渲染 (僅返回 HTML，數據由前端 AJAX 獲取)
     */
    @Hidden // 隱藏純頁面渲染接口，保持 Swagger UI 專注於 REST API
    @GetMapping("/products")
    public String manageProductsPage(Model model) {
        // 這裡可以預加載一些不需要分頁的靜態數據，比如所有品牌列表供篩選下拉框使用
        List<Product> allProducts = productService.getAllProducts();
        Set<String> allBrands = allProducts.stream()
                .map(Product::getBrand)
                .filter(brand -> brand != null && !brand.trim().isEmpty())
                .collect(Collectors.toSet());
        model.addAttribute("allBrands", allBrands);

        return "admin/admin-products";
    }

    /**
     * 2. AJAX API: 獲取商品列表 (支持篩選、排序、分頁)
     * 【核心修改】：接收 1-based 頁碼，內部轉 0-based 計算，返回 1-based 頁碼
     */
    @Operation(
            summary = "獲取後台商品分頁列表",
            description = "支持多條件篩選（品牌、分類、狀態、首頁推薦等）、多字段排序及分頁。返回的 currentPage 為 1-based。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "401", description = "未登入"),
            @ApiResponse(responseCode = "403", description = "無權訪問")
    })
    @GetMapping("/api/products/list")
    @ResponseBody
    public ResponseEntity<?> getProductsList(
            @Parameter(description = "當前頁碼 (1-based)", example = "1")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "每頁顯示數量", example = "30")
            @RequestParam(defaultValue = "30") int size,

            @Parameter(description = "篩選品牌 (例如: rolex)", example = "rolex")
            @RequestParam(required = false) String currentBrand,

            @Parameter(description = "篩選分類 (例如: dive)", example = "dive")
            @RequestParam(required = false) String currentCategory,

            @Parameter(description = "篩選成色 (例如: EXCELLENT)", example = "EXCELLENT")
            @RequestParam(required = false) String condition,

            @Parameter(description = "顯示狀態 (visible / hidden)", example = "visible")
            @RequestParam(required = false) String status,

            @Parameter(description = "是否為首頁推薦 (true / false)", example = "true")
            @RequestParam(required = false) String currentFeatured,

            @Parameter(description = "排序字段 (id / price / stock / rating / favoritecount / stocknotificationcount / homedisplayorder)", example = "price")
            @RequestParam(required = false) String sortField,

            @Parameter(description = "排序方向 (asc / desc)", example = "desc")
            @RequestParam(required = false) String sortDirection,

            @Parameter(description = "成色選項顯示狀態 (visible / hidden)", example = "visible")
            @RequestParam(required = false) String conditionVisible) {

        // 1. 獲取所有商品數據 (保持原有的內存過濾邏輯)
        List<Product> allProducts = productService.getAllProducts();

        // 2. 應用篩選邏輯
        List<Product> filteredProducts = allProducts.stream()
                .filter(p -> currentBrand == null || currentBrand.isEmpty() || currentBrand.equals(p.getBrand()))
                .filter(p -> currentCategory == null || currentCategory.isEmpty() || currentCategory.equals(p.getCategory()))
                .filter(p -> condition == null || condition.isEmpty() || (p.getCondition() != null && p.getCondition().name().equals(condition)))
                .filter(p -> status == null || status.isEmpty() ||
                        (status.equals("visible") && Boolean.TRUE.equals(p.getVisible())) ||
                        (status.equals("hidden") && Boolean.FALSE.equals(p.getVisible())))
                .filter(p -> conditionVisible == null || conditionVisible.isEmpty() ||
                        (conditionVisible.equals("visible") && Boolean.TRUE.equals(p.getConditionVisible())) ||
                        (conditionVisible.equals("hidden") && Boolean.FALSE.equals(p.getConditionVisible())))
                .filter(p -> currentFeatured == null || currentFeatured.isEmpty() ||
                        (currentFeatured.equals("true") && p.getHomeDisplayOrder() != null && p.getHomeDisplayOrder() > 0) ||
                        (currentFeatured.equals("false") && (p.getHomeDisplayOrder() == null || p.getHomeDisplayOrder() <= 0)))
                .collect(Collectors.toList());

        // 3. 應用排序邏輯
        if (sortField != null && !sortField.isEmpty() && sortDirection != null && !sortDirection.isEmpty()) {
            boolean isAsc = sortDirection.equalsIgnoreCase("asc");
            filteredProducts.sort((p1, p2) -> {
                int cmp = 0;
                switch (sortField.toLowerCase()) {
                    case "id": cmp = Integer.compare(p1.getProductId(), p2.getProductId()); break;
                    case "price": cmp = p1.getPrice().compareTo(p2.getPrice()); break;
                    case "stock":
                        Integer stock1 = p1.getStock() != null ? p1.getStock() : 0;
                        Integer stock2 = p2.getStock() != null ? p2.getStock() : 0;
                        cmp = Integer.compare(stock1, stock2); break;
                    case "rating":
                        double rating1 = (p1.getTotalReviewCount() != null && p1.getTotalReviewCount() > 0 && p1.getTotalScore() != null)
                                ? p1.getTotalScore().doubleValue() / p1.getTotalReviewCount() : 0.0;
                        double rating2 = (p2.getTotalReviewCount() != null && p2.getTotalReviewCount() > 0 && p2.getTotalScore() != null)
                                ? p2.getTotalScore().doubleValue() / p2.getTotalReviewCount() : 0.0;
                        cmp = Double.compare(rating1, rating2); break;
                    case "favoritecount":
                        Integer favCount1 = p1.getFavoriteCount() != null ? p1.getFavoriteCount() : 0;
                        Integer favCount2 = p2.getFavoriteCount() != null ? p2.getFavoriteCount() : 0;
                        cmp = Integer.compare(favCount1, favCount2); break;
                    case "stocknotificationcount":
                        Integer notifCount1 = p1.getStockNotificationCount() != null ? p1.getStockNotificationCount() : 0;
                        Integer notifCount2 = p2.getStockNotificationCount() != null ? p2.getStockNotificationCount() : 0;
                        cmp = Integer.compare(notifCount1, notifCount2); break;
                    case "homedisplayorder":
                        Integer order1 = p1.getHomeDisplayOrder() != null ? p1.getHomeDisplayOrder() : 9999;
                        Integer order2 = p2.getHomeDisplayOrder() != null ? p2.getHomeDisplayOrder() : 9999;
                        cmp = Integer.compare(order1, order2); break;
                    default: cmp = 0;
                }
                return isAsc ? cmp : -cmp;
            });
        }

        // 4. 手動分頁 (將 List 轉為 Page 對象以適配 PaginationUtils)
        int totalElements = filteredProducts.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        // 【修改 2】將 1-based 頁碼轉換為 0-based 索引用於計算
        int pageIndex = page - 1;

        // 確保頁碼不越界 (0-based)
        if (pageIndex < 0) pageIndex = 0;
        if (totalPages > 0 && pageIndex >= totalPages) pageIndex = totalPages - 1;
        if (totalPages == 0) totalPages = 1; // 至少顯示 1 頁

        int fromIndex = pageIndex * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<Product> pagedContent = (totalElements > 0) ? filteredProducts.subList(fromIndex, toIndex) : new ArrayList<>();

        // 創建 Pageable 和 PageImpl 對象 (注意：這裡依然使用 0-based 的 pageIndex，因為 Spring Data 需要)
        Pageable pageable = PageRequest.of(pageIndex, size);
        Page<Product> productPage = new PageImpl<>(pagedContent, pageable, totalElements);

        // 5. 使用 PaginationUtils 構建標準響應 (包含 smartPages)
        Map<String, Object> response = PaginationUtils.buildPageResponse(productPage, null);

        // 【修改 3】覆蓋 currentPage 為 1-based，以便前端直接使用
        response.put("currentPage", pageIndex + 1);

        // 同時確保 totalPages 至少為 1 (防止前端報錯)
        if ((int)response.get("totalPages") == 0) {
            response.put("totalPages", 1);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 新建商品 API
     * 【核心修復】：權限校驗從 "用戶名等於admin" 改為 "用戶角色等於ADMIN"
     */
    @Operation(
            summary = "新建商品",
            description = "管理員上傳圖片並填寫商品資訊以新建商品。需要 ADMIN 權限。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "新建成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤或缺少圖片"),
            @ApiResponse(responseCode = "401", description = "未登入"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員 (Role: ADMIN)")
    })
    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<Result> createProduct(
            @Parameter(description = "品牌名稱", example = "Rolex")
            @RequestParam String brand,

            @Parameter(description = "商品分類", example = "dive")
            @RequestParam String category,

            @Parameter(description = "商品簡短描述", example = "Rolex Submariner 黑水鬼 41mm")
            @RequestParam String description,

            @Parameter(description = "商品詳細介紹", example = "陶瓷錶圈，自動上鏈機芯...")
            @RequestParam(required = false) String details,

            @Parameter(description = "售價 (HKD)", example = "85000.00")
            @RequestParam BigDecimal price,

            @Parameter(description = "庫存數量", example = "1")
            @RequestParam Integer stock,

            @Parameter(description = "手錶成色枚舉", example = "EXCELLENT")
            @RequestParam WatchCondition condition,

            @Parameter(description = "是否在前台顯示", example = "true")
            @RequestParam Boolean visible,

            @Parameter(description = "是否顯示成色選項", example = "true")
            @RequestParam Boolean conditionVisible,

            @Parameter(description = "同款分組碼", example = "ROLEX_SUB_001")
            @RequestParam String groupCode,

            @Parameter(description = "商品圖片文件", schema = @Schema(type = "string", format = "binary"))
            @RequestParam("imageFile") MultipartFile imageFile,

            Authentication authentication) {

        // ==========================================
        // 【核心修復】：使用 SecurityUtils 進行嚴格的 Role 權限校驗
        // ==========================================
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }

        // 使用 SecurityUtils.isAdmin() 檢查 user_type 是否為 ADMIN
        // 這會從 SecurityContext 中獲取 CustomUserDetails 並檢查 Role 枚舉
        if (!org.example.website.util.SecurityUtils.isAdmin()) {
            return ResponseEntity.status(403).body(Result.error("無權操作，僅限管理員 (Role: ADMIN)"));
        }

        try {
            if (imageFile == null || imageFile.isEmpty()) {
                return ResponseEntity.badRequest().body(Result.error("請上傳商品圖片"));
            }

            String fileName = imageFile.getOriginalFilename();
            if (fileName == null || fileName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.error("無效的圖片文件名"));
            }

            Product product = new Product();
            product.setBrand(brand);
            product.setCategory(category);
            product.setDescription(description);
            product.setDetails(details);
            product.setPrice(price);
            product.setStock(stock);
            product.setCondition(condition);
            product.setVisible(visible);
            product.setConditionVisible(conditionVisible);
            product.setGroupCode(groupCode);

            // 直接將原始文件名存入數據庫
            product.setImage(fileName);

            productRepository.save(product);
            return ResponseEntity.ok(Result.ok("商品新建成功"));

        } catch (Exception e) {
            System.err.println("❌ 新建商品失敗: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Result.error("新建失敗: " + e.getMessage()));
        }
    }
}