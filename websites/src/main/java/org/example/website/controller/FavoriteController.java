package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.Favorite;
import org.example.website.entity.Product;
import org.example.website.entity.User;
import org.example.website.repository.FavoriteRepository;
import org.example.website.repository.ProductRepository;
import org.example.website.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
@Tag(name = "收藏管理", description = "用戶收藏商品相關的切換與管理接口")
public class FavoriteController {

    private final FavoriteRepository favoriteRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public FavoriteController(FavoriteRepository favoriteRepository,
                              ProductRepository productRepository,
                              UserRepository userRepository) {
        this.favoriteRepository = favoriteRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    /**
     * 核心接口：切換收藏狀態 (收藏 / 取消收藏)
     */
    @Operation(
            summary = "切換商品收藏狀態",
            description = "根據提供的商品ID，若用戶未收藏該商品則加入收藏，若已收藏則取消收藏。此接口需要用戶登入。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "操作成功 (返回 '已加入收藏' 或 '已取消收藏')",
                    content = @Content(schema = @Schema(implementation = Result.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "請求參數錯誤或商品不存在"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "未登入或認證失效"
            )
    })
    @PostMapping("/toggle/{productId}")
    public ResponseEntity<Result> toggleFavorite(
            @Parameter(description = "目標商品的唯一 ID", example = "1", required = true)
            @PathVariable Integer productId,

            @Parameter(hidden = true) // 隱藏 Authentication，因為它由 Spring Security 自動解析，不需要前端傳遞
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Result.error("請先登入"));
        }

        try {
            String username = authentication.getName();

            // 【修改處 1】：使用 UserRepository 獲取 User 實體
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("用戶不存在"));

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("商品不存在"));

            // 檢查是否已收藏
            Favorite existing = favoriteRepository.findByUser_UsernameAndProduct_ProductId(username, productId);

            if (existing != null) {
                // 已收藏 -> 刪除記錄 (取消收藏)
                favoriteRepository.delete(existing);
                productRepository.decrementFavoriteCount(productId);
                return ResponseEntity.ok(Result.ok("已取消收藏"));
            } else {
                // 未收藏 -> 新增記錄
                Favorite fav = new Favorite();
                // 【修改處 2】：設置 User 關聯，不再是 setCustomer
                fav.setUser(user);
                fav.setProduct(product);
                favoriteRepository.save(fav);
                productRepository.incrementFavoriteCount(productId);
                return ResponseEntity.ok(Result.ok("已加入收藏"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }
}