package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Customer;
import org.example.website.entity.Favorite;
import org.example.website.entity.Product;
import org.example.website.repository.CustomerRepository;
import org.example.website.repository.FavoriteRepository;
import org.example.website.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public FavoriteController(FavoriteRepository favoriteRepository,
                              ProductRepository productRepository,
                              CustomerRepository customerRepository) {
        this.favoriteRepository = favoriteRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    // 🟢 核心接口：切換收藏狀態 (收藏 / 取消收藏)
    @PostMapping("/toggle/{productId}")
    public ResponseEntity<ApiResponse> toggleFavorite(@PathVariable Integer productId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        try {
            String username = authentication.getName();
            Customer customer = customerRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("用戶不存在"));
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("商品不存在"));

            // 檢查是否已收藏
            Favorite existing = favoriteRepository.findByCustomer_UsernameAndProduct_Id(username, productId);

            if (existing != null) {
                // 已收藏 -> 刪除記錄 (取消收藏)
                favoriteRepository.delete(existing);
                return ResponseEntity.ok(ApiResponse.ok("已取消收藏"));
            } else {
                // 未收藏 -> 新增記錄
                Favorite fav = new Favorite();
                fav.setCustomer(customer);
                fav.setProduct(product);
                favoriteRepository.save(fav);
                return ResponseEntity.ok(ApiResponse.ok("已加入收藏"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}