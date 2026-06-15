package org.example.website.controller;

import org.example.website.entity.Product;
import org.example.website.repository.FavoriteRepository; //  新增導入
import org.example.website.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication; // 新增導入
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
    private final FavoriteRepository favoriteRepository; //  新增依賴

    //  修改構造函數，注入 FavoriteRepository
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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication, // Spring Security 自動注入當前用戶認證信息
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

        Pageable pageable = PageRequest.of(page - 1, size, dynamicSort);
        Page<Product> productPage = productRepository.searchProducts(filters, pageable);

        //  核心修復：查詢當前用戶的收藏商品 ID 集合
        //使用 Set (如 HashSet)，contains() 的時間複雜度是 O(1)，無論收藏多少商品，判斷速度都是瞬間完成，性能更好。
        Set<Integer> favoriteProductIds = new HashSet<>();
        // 判斷用戶是否已登入 (排除匿名用戶)
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {

            String username = authentication.getName();
            List<Integer> favIds = favoriteRepository.findFavoriteProductIdsByUsername(username);
            if (favIds != null) {
                favoriteProductIds.addAll(favIds);
            }
        }

        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("totalElements", productPage.getTotalElements());
        model.addAttribute("currentFilters", filters);
        model.addAttribute("currentSort", sort);

        //  將收藏 ID 集合傳遞給 Thymeleaf
        model.addAttribute("favoriteProductIds", favoriteProductIds);

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