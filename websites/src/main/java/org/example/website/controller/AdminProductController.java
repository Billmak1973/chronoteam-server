package org.example.website.controller;

import org.example.website.entity.Product;
import org.example.website.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }


    /**
     * 商品管理列表頁面 (支援多條件篩選與動態排序)
     */
    @GetMapping("/products")
    public String manageProducts(
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
                // 品牌篩選
                .filter(p -> currentBrand == null || currentBrand.isEmpty() || currentBrand.equals(p.getBrand()))
                // 分類篩選
                .filter(p -> currentCategory == null || currentCategory.isEmpty() || currentCategory.equals(p.getCategory()))
                // 成色篩選
                .filter(p -> condition == null || condition.isEmpty() ||
                        (p.getCondition() != null && p.getCondition().name().equals(condition)))
                // 狀態篩選 (visible: true / hidden: false)
                .filter(p -> status == null || status.isEmpty() ||
                        (status.equals("visible") && Boolean.TRUE.equals(p.getVisible())) ||
                        (status.equals("hidden") && Boolean.FALSE.equals(p.getVisible())))
                //  核心修復：成色顯示狀態篩選 (比對字串 "visible" 或 "hidden")
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
                    case "id":
                        cmp = Integer.compare(p1.getProductId(), p2.getProductId());
                        break;
                    case "price":
                        cmp = p1.getPrice().compareTo(p2.getPrice());
                        break;
                    case "stock":
                        Integer stock1 = p1.getStock() != null ? p1.getStock() : 0;
                        Integer stock2 = p2.getStock() != null ? p2.getStock() : 0;
                        cmp = Integer.compare(stock1, stock2);
                        break;
                    case "rating":
                        double rating1 = (p1.getTotalReviewCount() != null && p1.getTotalReviewCount() > 0 && p1.getTotalScore() != null)
                                ? p1.getTotalScore().doubleValue() / p1.getTotalReviewCount() : 0.0;
                        double rating2 = (p2.getTotalReviewCount() != null && p2.getTotalReviewCount() > 0 && p2.getTotalScore() != null)
                                ? p2.getTotalScore().doubleValue() / p2.getTotalReviewCount() : 0.0;
                        cmp = Double.compare(rating1, rating2);
                        break;
                    default:
                        cmp = 0;
                }
                return isAsc ? cmp : -cmp;
            });
        }

        // 5. 將數據傳遞給前端 Thymeleaf
        model.addAttribute("products", filteredProducts);
        model.addAttribute("allBrands", allBrands);

        // 傳遞當前篩選條件，方便前端保持選中狀態
        model.addAttribute("currentBrand", currentBrand);
        model.addAttribute("currentCategory", currentCategory);
        model.addAttribute("currentCondition", condition);
        model.addAttribute("currentStatus", status);
        model.addAttribute("currentSortField", sortField);
        model.addAttribute("currentSortDirection", sortDirection);
        model.addAttribute("currentConditionVisible", conditionVisible); // 傳遞 String 給前端

        return "admin-products";
    }
}