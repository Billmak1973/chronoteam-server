package org.example.website.controller;

import org.example.website.entity.Product;
import org.example.website.repository.FavoriteRepository;
import org.example.website.repository.ProductRepository;
import org.example.website.util.PaginationUtils; // 1. 引入工具類
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/browse")
public class BrowseController {
    private final ProductRepository productRepository;
    private final FavoriteRepository favoriteRepository;

    public BrowseController(ProductRepository productRepository, FavoriteRepository favoriteRepository) {
        this.productRepository = productRepository;
        this.favoriteRepository = favoriteRepository;
    }

    @GetMapping
    public String browseProducts(
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String priceRange,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "1") int page, // 注意：前端傳入的是 1-based
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication,
            Model model) {

        Map<String, Object> filters = new HashMap<>();
        if (category != null && !category.isEmpty()) filters.put("category", category);
        if (brand != null && !brand.isEmpty()) filters.put("brand", brand);
        if (condition != null && !condition.isEmpty()) filters.put("condition", condition);
        if (keyword != null && !keyword.isEmpty()) filters.put("keyword", keyword);
        if (priceRange != null && !priceRange.isEmpty()) filters.put("priceRange", priceRange);

        Sort dynamicSort;
        switch (sort != null ? sort.toLowerCase() : "newest") {
            case "price-asc":
                dynamicSort = Sort.by(Sort.Direction.ASC, "price");
                break;
            case "price-desc":
                dynamicSort = Sort.by(Sort.Direction.DESC, "price");
                break;
            case "newest":
            default:
                dynamicSort = Sort.by(Sort.Direction.DESC, "createdAt");
                break;
        }

        // Spring Data JPA 使用 0-based 頁碼，所以這裡要 page - 1
        Pageable pageable = PageRequest.of(page - 1, size, dynamicSort);
        Page<Product> productPage = productRepository.searchProducts(filters, pageable);

        // 查詢當前用戶的收藏商品 ID 集合
        Set<Integer> favoriteProductIds = new HashSet<>();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            String username = authentication.getName();
            List<Integer> favIds = favoriteRepository.findFavoriteProductIdsByUsername(username);
            if (favIds != null) {
                favoriteProductIds.addAll(favIds);
            }
        }

        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page); // 保持 1-based 給前端顯示用
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("totalElements", productPage.getTotalElements());
        model.addAttribute("currentFilters", filters);
        model.addAttribute("currentSort", sort);
        model.addAttribute("favoriteProductIds", favoriteProductIds);

        // ==========================================
        // 【核心新增】：生成智能分頁列表 (1 ... 4 5 6 ... 10)
        // 注意：PaginationUtils 內部邏輯通常假設 currentPage 是 0-based (Spring Data 標準)
        // 但你的前端傳入的是 1-based，PaginationUtils 內部會處理 +1 邏輯，
        // 所以這裡傳入 page - 1 (即 0-based) 給工具類是安全的。
        // ==========================================
        List<PaginationUtils.PageItem> smartPages = PaginationUtils.generateSmartPagination(page - 1, productPage.getTotalPages());
        model.addAttribute("smartPages", smartPages);

        String pageTitle = buildPageTitle(category, brand, condition);
        model.addAttribute("pageTitle", pageTitle);

        return "browse";
    }

    private String buildPageTitle(List<String> category, List<String> brand, String condition) {
        if (brand != null && !brand.isEmpty()) {
            return "品牌篩選: " + String.join(", ", brand).toUpperCase();
        }
        if (category != null && !category.isEmpty()) {
            return "分類篩選: " + String.join(", ", category);
        }
        return "瀏覽商品";
    }
}