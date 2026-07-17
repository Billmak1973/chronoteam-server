package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Product;
import org.example.website.entity.WatchCondition;
import org.example.website.repository.ProductRepository;
import org.example.website.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminProductController {

    private final ProductService productService;
    private final ProductRepository productRepository;
    public AdminProductController(ProductService productService, ProductRepository productRepository) {
        this.productService = productService;
        this.productRepository= productRepository;
    }


    // === 新增：智能分頁項目的內部類 ===
    public static class PageItem {
        private boolean isEllipsis; // 是否為省略號 "..."
        private Integer pageNumber; // 頁碼數字

        public PageItem(boolean isEllipsis, Integer pageNumber) {
            this.isEllipsis = isEllipsis;
            this.pageNumber = pageNumber;
        }
        public boolean isEllipsis() { return isEllipsis; }
        public Integer getPageNumber() { return pageNumber; }
    }

    @GetMapping("/products")
    public String manageProducts(
            @RequestParam(defaultValue = "1") int page, // 【新增】當前頁碼，默認第 1 頁
            @RequestParam(required = false) String currentBrand,
            @RequestParam(required = false) String currentCategory,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String conditionVisible,
            Model model) {

        // 1. 獲取所有商品數據
        List<Product> allProducts = productService.getAllProducts();

        // 2. 提取所有不重複的品牌
        Set<String> allBrands = allProducts.stream()
                .map(Product::getBrand)
                .filter(brand -> brand != null && !brand.trim().isEmpty())
                .collect(Collectors.toSet());

        // 3. 應用篩選邏輯
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
                .collect(Collectors.toList());

        // 4. 應用排序邏輯
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
                    default: cmp = 0;
                }
                return isAsc ? cmp : -cmp;
            });
        }

        // ==========================================
        // 5. 【核心新增】：內存分頁邏輯 (每頁 30 條)
        // ==========================================
        int size = 30; // 每頁顯示 30 條
        int totalElements = filteredProducts.size();
        int totalPages = (int) Math.ceil((double) totalElements / size); // 計算總頁數

        // 防止頁碼越界
        if (page < 1) page = 1;
        if (page > totalPages && totalPages > 0) page = totalPages;

        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        // 截取當前頁的數據
        List<Product> pagedProducts = (totalElements > 0) ? filteredProducts.subList(fromIndex, toIndex) : new ArrayList<>();

        // 6. 【核心新增】：生成智能分頁列表 (例如: 1, ..., 3, 4, 5, ..., 10)
        List<PageItem> smartPages = generateSmartPagination(page, totalPages);

        // 7. 將數據傳遞給前端 Thymeleaf
        model.addAttribute("products", pagedProducts); // 注意：這裡改為傳遞分頁後的 pagedProducts
        model.addAttribute("allBrands", allBrands);

        // 傳遞分頁相關數據
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("smartPages", smartPages); // 傳遞智能頁碼列表

        // 傳遞當前篩選條件，方便翻頁時保持狀態
        model.addAttribute("currentBrand", currentBrand);
        model.addAttribute("currentCategory", currentCategory);
        model.addAttribute("currentCondition", condition);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentSortField", sortField);
        model.addAttribute("currentSortDirection", sortDirection);
        model.addAttribute("currentConditionVisible", conditionVisible);

        return "admin-products";
    }

    /**
     * 生成智能分頁列表的核心算法
     */
    private List<PageItem> generateSmartPagination(int currentPage, int totalPages) {
        List<PageItem> pages = new ArrayList<>();

        // 如果總頁數小於等於 7 頁，直接顯示所有頁碼
        if (totalPages <= 7) {
            for (int i = 1; i <= totalPages; i++) {
                pages.add(new PageItem(false, i));
            }
        } else {
            // 1. 始終顯示第一頁
            pages.add(new PageItem(false, 1));

            // 2. 如果當前頁大於 3，在第一頁後面加 "..."
            if (currentPage > 3) {
                pages.add(new PageItem(true, null));
            }

            // 3. 顯示當前頁的左右各一頁 (範圍限制在 2 到 totalPages-1 之間)
            int start = Math.max(2, currentPage - 1);
            int end = Math.min(totalPages - 1, currentPage + 1);
            for (int i = start; i <= end; i++) {
                pages.add(new PageItem(false, i));
            }

            // 4. 如果當前頁小於 總頁數-2，在最後一頁前面加 "..."
            if (currentPage < totalPages - 2) {
                pages.add(new PageItem(true, null));
            }

            // 5. 始終顯示最後一頁
            pages.add(new PageItem(false, totalPages));
        }
        return pages;
    }

    /**
     * 新建商品 API
     * 完整路徑: POST /admin/add
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

        // 1. 權限校驗
        if (authentication == null || !"admin".equals(authentication.getName())) {
            return ResponseEntity.status(403).body(ApiResponse.error("無權操作，僅限管理員"));
        }

        try {
            // 2. 核心校驗：檢查圖片文件是否為空
            if (imageFile == null || imageFile.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("請上傳商品圖片"));
            }

            // 3. 【核心修改】：直接使用前端上傳的原始文件名，絕不進行隨機重命名！
            String fileName = imageFile.getOriginalFilename();

            // 確保文件名不為空且合法
            if (fileName == null || fileName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("無效的圖片文件名"));
            }

            // ⚠️ 【重要提醒】：這裡需要將文件實際保存到服務器磁盤
            // 如果您項目中已有 FileStorageService，請在這裡調用，例如：
            // fileStorageService.save(imageFile, "/images/products/");
            // 如果暫時沒有，請確保您的 Spring Boot 配置了靜態資源映射，
            // 且前端上傳的文件確實存在於您配置的 /images/products/ 目錄下。

            // 4. 構建 Product 實體
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
            // 打印完整堆棧到控制台，方便您看到真實的報錯原因
            System.err.println("❌ 新建商品失敗: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(ApiResponse.error("新建失敗: " + e.getMessage()));
        }
    }
}