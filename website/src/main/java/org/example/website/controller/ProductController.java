package org.example.website.controller;

import org.example.website.entity.Product;
import org.example.website.entity.Review;
import org.example.website.repository.ProductRepository;
import org.example.website.repository.ReviewRepository;
import org.example.website.service.ViewHistoryService; // 🟢 新增：引入瀏覽歷史 Service
import org.springframework.security.core.Authentication; // 🟢 新增：引入 Authentication
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class ProductController {
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ViewHistoryService viewHistoryService;

    //  修改構造函數，注入 ViewHistoryService
    public ProductController(ProductRepository productRepository,
                             ReviewRepository reviewRepository,
                             ViewHistoryService viewHistoryService) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.viewHistoryService = viewHistoryService;
    }

    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable Integer id,
                                Model model,
                                Authentication authentication) { // 🟢 新增 Authentication 參數

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));

        model.addAttribute("product", product);

        // 查詢該商品的評論並傳入 Model
        List<Review> reviews = reviewRepository.findByProduct_IdOrderByCreatedAtDesc(id);
        model.addAttribute("reviews", reviews);

        //  【新增】：記錄瀏覽歷史 (僅限已登入用戶)
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            String username = authentication.getName();
            viewHistoryService.recordView(username, id);
        }

        return "product-detail";
    }
}