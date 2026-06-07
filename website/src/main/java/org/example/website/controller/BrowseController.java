
package org.example.website.controller;

import org.example.website.entity.Product;
import org.example.website.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List; // <--- 新增导入
import java.util.Map;

@Controller
@RequestMapping("/browse")
public class BrowseController {
    private final ProductRepository productRepository;

    public BrowseController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public String browseProducts(
            @RequestParam(required = false) List<String> category, // <--- 改为 List<String>
            @RequestParam(required = false) List<String> brand,    // <--- 改为 List<String>
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String priceRange,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model) {

        Map<String, Object> filters = new HashMap<>();
        // <--- 修改判断逻辑，存入 List
        if (category != null && !category.isEmpty()) filters.put("category", category);
        if (brand != null && !brand.isEmpty()) filters.put("brand", brand);
        if (condition != null && !condition.isEmpty()) filters.put("condition", condition);
        if (keyword != null && !keyword.isEmpty()) filters.put("keyword", keyword);
        if (priceRange != null && !priceRange.isEmpty()) filters.put("priceRange", priceRange);

        // 2. 動態構建排序規則 (保持不变)
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

        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("totalElements", productPage.getTotalElements());
        model.addAttribute("currentFilters", filters);
        model.addAttribute("currentSort", sort);

        // <--- 修改标题构建方法，传入 List
        String pageTitle = buildPageTitle(category, brand, condition);
        model.addAttribute("pageTitle", pageTitle);

        return "browse";
    }

    // <--- 修改方法签名以接受 List
    private String buildPageTitle(List<String> category, List<String> brand, String condition) {
        if (brand != null && !brand.isEmpty()) {
            // 简单处理：显示选中的品牌
            return "品牌篩選: " + String.join(", ", brand).toUpperCase();
        }
        if (category != null && !category.isEmpty()) {
            return "分類篩選: " + String.join(", ", category);
        }
        return "瀏覽商品";
    }
}