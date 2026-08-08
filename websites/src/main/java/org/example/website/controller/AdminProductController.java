package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Product;
import org.example.website.entity.WatchCondition;
import org.example.website.repository.ProductRepository;
import org.example.website.service.ProductService;
import org.example.website.util.PaginationUtils; // 引入工具類
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
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
     */
    @GetMapping("/api/products/list")
    @ResponseBody
    public ResponseEntity<?> getProductsList(
            @RequestParam(defaultValue = "0") int page, // 前端傳入 0-based 頁碼
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String currentBrand,
            @RequestParam(required = false) String currentCategory,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String currentFeatured,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
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
        // 確保頁碼不越界
        int totalPages = (int) Math.ceil((double) totalElements / size);
        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<Product> pagedContent = (totalElements > 0) ? filteredProducts.subList(fromIndex, toIndex) : new ArrayList<>();

        // 創建 Pageable 和 PageImpl 對象
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> productPage = new PageImpl<>(pagedContent, pageable, totalElements);

        // 5. 數據清洗 (可選：如果前端只需要部分字段，可以在這裡轉換為 Map 列表)
        // 這裡為了簡單，直接返回 Entity 列表，Jackson 會自動序列化
        // 如果有循環引用問題，請使用 @JsonIgnore 或 DTO

        // 6. 使用 PaginationUtils 構建標準響應
        // 注意：因為我們沒有 extraData，所以調用兩參數的方法
        Map<String, Object> response = PaginationUtils.buildPageResponse(productPage, null);

        return ResponseEntity.ok(response);
    }

    /**
     * 新建商品 API
     * 【核心修復】：權限校驗從 "用戶名等於admin" 改為 "用戶角色等於ADMIN"
     */
    @PostMapping("/add")
    public ResponseEntity<ApiResponse> createProduct(
            @RequestParam String brand,
            @RequestParam String category,
            @RequestParam String description,
            @RequestParam(required = false) String details,
            @RequestParam BigDecimal price,
            @RequestParam Integer stock,
            @RequestParam WatchCondition condition,
            @RequestParam Boolean visible,
            @RequestParam Boolean conditionVisible,
            @RequestParam String groupCode,
            @RequestParam("imageFile") MultipartFile imageFile,
            Authentication authentication) {

        // ==========================================
        // 【核心修復】：使用 SecurityUtils 進行嚴格的 Role 權限校驗
        // ==========================================
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        // 使用 SecurityUtils.isAdmin() 檢查 user_type 是否為 ADMIN
        // 這會從 SecurityContext 中獲取 CustomUserDetails 並檢查 Role 枚舉
        if (!org.example.website.util.SecurityUtils.isAdmin()) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作，僅限管理員 (Role: ADMIN)"));
        }

        try {
            if (imageFile == null || imageFile.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("請上傳商品圖片"));
            }

            String fileName = imageFile.getOriginalFilename();
            if (fileName == null || fileName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("無效的圖片文件名"));
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
            return ResponseEntity.ok(ApiResponse.ok("商品新建成功"));

        } catch (Exception e) {
            System.err.println("❌ 新建商品失敗: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(ApiResponse.error("新建失敗: " + e.getMessage()));
        }
    }
}