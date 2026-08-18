package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.ProductUpdateRequest;
import org.example.website.dto.ProductVariantDTO;
import org.example.website.dto.Result;
import org.example.website.entity.Order;
import org.example.website.entity.Product;
import org.example.website.entity.Review;
import org.example.website.entity.User;
import org.example.website.repository.FavoriteRepository;
import org.example.website.repository.OrderItemRepository;
import org.example.website.repository.OrderRepository;
import org.example.website.repository.ProductRepository;
import org.example.website.repository.ReviewRepository;
import org.example.website.security.CustomUserDetails;
import org.example.website.service.ProductService;
import org.example.website.service.ViewHistoryService;
import org.example.website.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/") // 建議加上基礎路徑，便於 Swagger 分類
@Tag(name = "商品管理", description = "商品詳情頁面渲染、變體查詢及管理端商品更新接口")
public class ProductController {

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ViewHistoryService viewHistoryService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final FavoriteRepository favoriteRepository;
    private final ProductService productService;

    public ProductController(ProductRepository productRepository,
                             ReviewRepository reviewRepository,
                             ViewHistoryService viewHistoryService,
                             OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             FavoriteRepository favoriteRepository,
                             ProductService productService) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.viewHistoryService = viewHistoryService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.favoriteRepository = favoriteRepository;
        this.productService = productService;
    }

    /**
     * 核心修復：權限校驗基於 user_type (Role == ADMIN)，而非用戶名是否等於 "admin"
     * CustomUserDetails 在登入時已從數據庫載入 Role 枚舉，直接判斷，零查庫開銷
     */
    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails
                && userDetails.getRole() == User.Role.ADMIN;
    }

    /**
     * 商品詳情頁面渲染 (Thymeleaf)
     * 使用 @Hidden 隱藏此接口，因為 Swagger 專注於 REST API，不需要展示頁面渲染接口
     */
    @Hidden
    @GetMapping("/product/{id}")
    public String productDetail(@PathVariable Integer id,
                                Model model,
                                Authentication authentication,
                                @RequestParam(required = false) String orderNo,
                                @RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "popular") String sort) {

        // 1. 查詢商品
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        model.addAttribute("product", product);

        // 2. 根據 sort 參數構建動態排序規則（加入置頂權重）
        Sort dynamicSort;
        switch (sort) {
            case "newest":
                dynamicSort = Sort.by(
                        Sort.Order.desc("pinned"),
                        Sort.Order.desc("createdAt")
                );
                break;
            case "oldest":
                dynamicSort = Sort.by(
                        Sort.Order.desc("pinned"),
                        Sort.Order.asc("createdAt")
                );
                break;
            case "popular":
            default:
                dynamicSort = Sort.by(
                        Sort.Order.desc("pinned"),
                        Sort.Order.desc("likeCount"),
                        Sort.Order.desc("createdAt")
                );
                break;
        }

        // 3. 使用分頁 + 動態排序查詢根評論（每頁30條）
        Pageable pageable = PageRequest.of(page - 1, 30, dynamicSort);
        Page<Review> rootReviewsPage = reviewRepository.findByProduct_ProductIdAndParentIdIsNull(id, pageable);

        // 4. 傳遞分頁相關數據到前端
        model.addAttribute("reviews", rootReviewsPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", rootReviewsPage.getTotalPages());
        model.addAttribute("totalElements", rootReviewsPage.getTotalElements());
        model.addAttribute("sortOrder", sort);

        // 5. 使用 PaginationUtils 生成智能分頁列表
        List<PaginationUtils.PageItem> smartPages = PaginationUtils.generateSmartPagination(page - 1, rootReviewsPage.getTotalPages());
        model.addAttribute("smartPages", smartPages);

        // 6. 統計每條根評論的樓中樓回復數量
        Map<Long, Long> replyCounts = new HashMap<>();
        for (Review root : rootReviewsPage.getContent()) {
            long count = reviewRepository.countByParentId(root.getReviewId());
            replyCounts.put(root.getReviewId(), count);
        }
        model.addAttribute("replyCounts", replyCounts);

        // 7. 計算平均分（只對根評論計算）
        double averageRating = 0.0;
        List<Review> rootReviews = rootReviewsPage.getContent();
        if (rootReviews != null && !rootReviews.isEmpty()) {
            averageRating = rootReviews.stream()
                    .filter(r -> r.getRating() != null)
                    .mapToDouble(r -> r.getRating().doubleValue())
                    .average()
                    .orElse(0.0);
        }
        model.addAttribute("averageRating", averageRating);

        boolean isFavorite = false;

        // 8. 檢查當前用戶是否可以評價該商品 & 查詢收藏狀態
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {

            String username = authentication.getName();
            viewHistoryService.recordView(username, id);

            if (favoriteRepository.findByUser_UsernameAndProduct_ProductId(username, id) != null) {
                isFavorite = true;
            }

            boolean canReview = false;
            String reviewOrderNo = null;
            Review userReview = null;

            if (isAdmin(authentication)) {
                canReview = true;
                reviewOrderNo = "ADMIN_COMMENT";
            } else {
                List<Review> userReviews = reviewRepository.findByUser_UsernameAndProduct_ProductIdAndParentIdIsNull(username, id);

                if (orderNo != null) {
                    if (!userReviews.isEmpty()) {
                        userReview = userReviews.stream()
                                .filter(r -> orderNo.equals(r.getOrderNo()))
                                .findFirst()
                                .orElse(null);
                    }
                    if (userReview == null) {
                        Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username).orElse(null);
                        if (order != null) {
                            Order.PaymentStatus status = order.getPaymentStatus();
                            boolean isPaid = (status == Order.PaymentStatus.PAID_SIMULATED ||
                                    status == Order.PaymentStatus.PAID_REAL ||
                                    status == Order.PaymentStatus.PAID_OFFLINE);
                            canReview = isPaid;
                            reviewOrderNo = orderNo;
                        }
                    }
                } else {
                    List<Order.PaymentStatus> paidStatuses = Arrays.asList(
                            Order.PaymentStatus.PAID_SIMULATED,
                            Order.PaymentStatus.PAID_REAL,
                            Order.PaymentStatus.PAID_OFFLINE
                    );
                    List<String> paidOrderNos = orderItemRepository.findPaidOrderNosByUsernameAndProductId(
                            username, id, paidStatuses
                    );

                    for (String no : paidOrderNos) {
                        boolean alreadyReviewed = userReviews.stream()
                                .anyMatch(r -> no.equals(r.getOrderNo()));
                        if (!alreadyReviewed) {
                            canReview = true;
                            reviewOrderNo = no;
                            break;
                        }
                    }

                    if (!canReview && !userReviews.isEmpty()) {
                        userReview = userReviews.get(0);
                    }
                }
            }

            model.addAttribute("userReview", userReview);
            model.addAttribute("canReview", canReview);
            model.addAttribute("reviewOrderNo", reviewOrderNo);
        }

        model.addAttribute("isFavorite", isFavorite);
        model.addAttribute("isAdmin", isAdmin(authentication));
        return "product-detail";
    }

    /**
     * 管理員更新商品信息 API
     */
    @Operation(
            summary = "管理員更新商品信息",
            description = "管理員專屬接口，用於更新商品的價格、庫存、描述、圖片及首頁推薦排序等資訊。包含智能排序邏輯：設置推薦時會自動調整其他商品的排序權重。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "修改成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤或商品不存在"),
            @ApiResponse(responseCode = "401", description = "未登入"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員 (Role: ADMIN)")
    })
    @PutMapping("/api/admin/product/{id}")
    @ResponseBody
    public ResponseEntity<Result> updateProduct(
            @Parameter(description = "要更新的商品 ID", example = "1", required = true)
            @PathVariable Integer id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "包含商品更新資訊的請求體",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ProductUpdateRequest.class))
            )
            @RequestBody ProductUpdateRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Result.error("未登入，請先登入"));
        }

        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Result.error("無權操作，僅限管理員"));
        }

        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("商品不存在"));

            if (request.getPrice() != null) product.setPrice(request.getPrice());
            if (request.getStock() != null) product.setStock(request.getStock());
            if (request.getCategory() != null) product.setCategory(request.getCategory());
            if (request.getBrand() != null) product.setBrand(request.getBrand());
            if (request.getDescription() != null) product.setDescription(request.getDescription());
            if (request.getDetails() != null) product.setDetails(request.getDetails());
            if (request.getImage() != null) product.setImage(request.getImage());
            if (request.getVisible() != null) product.setVisible(request.getVisible());
            if (request.getConditionVisible() != null) product.setConditionVisible(request.getConditionVisible());
            if (request.getCondition() != null) product.setCondition(request.getCondition());
            if (request.getGroupCode() != null) product.setGroupCode(request.getGroupCode());

            // 處理首頁推薦的智能排序邏輯
            Integer newOrder = request.getHomeDisplayOrder();
            if (newOrder != null && newOrder > 0) {
                productService.updateHomeDisplayOrder(id, newOrder);
            } else {
                productService.updateHomeDisplayOrder(id, null);
            }

            productRepository.save(product);
            return ResponseEntity.ok(Result.ok("修改成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("修改失敗: " + e.getMessage()));
        }
    }

    /**
     * 獲取同款商品變體 API
     */
    @Operation(
            summary = "獲取同款商品的不同成色變體",
            description = "根據商品的同款分組碼 (groupCode)，獲取所有可見的成色變體列表，並動態計算平均評分。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回變體列表", content = @Content(schema = @Schema(implementation = ProductVariantDTO.class)))
    })
    @GetMapping("/variants/{groupCode}")
    @ResponseBody
    public ResponseEntity<List<ProductVariantDTO>> getProductVariants(
            @Parameter(description = "同款商品的分組碼 (groupCode)", example = "ROLEX_SUB_001", required = true)
            @PathVariable String groupCode) {

        List<Product> products = productRepository.findByGroupCode(groupCode);

        // 過濾出 conditionVisible = true 的產品並排序
        products = products.stream()
                .filter(p -> p.getConditionVisible() != null && p.getConditionVisible())
                .sorted(Comparator.comparing(Product::getCondition))
                .collect(Collectors.toList());

        List<ProductVariantDTO> variants = products.stream().map(p -> {
            ProductVariantDTO dto = new ProductVariantDTO();
            dto.setProductId(p.getProductId());
            dto.setPrice(p.getPrice());
            dto.setStock(p.getStock());
            dto.setDescription(p.getDescription());
            dto.setDetails(p.getDetails());
            dto.setTotalReviewCount(p.getTotalReviewCount());
            dto.setTotalScore(p.getTotalScore());

            // 動態計算平均分
            if (p.getTotalReviewCount() != null && p.getTotalReviewCount() > 0 && p.getTotalScore() != null) {
                double avgRating = p.getTotalScore().doubleValue() / p.getTotalReviewCount();
                dto.setRating(avgRating);
            } else {
                dto.setRating(0.0);
            }

            if (p.getCondition() != null) {
                dto.setCondition(p.getCondition().name().toLowerCase());
            } else {
                dto.setCondition("good");
            }

            dto.setBrand(p.getBrand());
            dto.setCategory(p.getCategory());
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(variants);
    }

    /**
     * 獲取首頁推薦最大排序值 API
     */
    @Operation(
            summary = "獲取首頁推薦商品的最大排序權重",
            description = "管理端專用：獲取當前首頁推薦商品中最大的 homeDisplayOrder 值，用於新增推薦商品時自動分配下一個排序號。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功返回最大排序值，若無推薦商品或出錯則返回 0")
    })
    @GetMapping("/api/admin/products/featured/max-order")
    @ResponseBody
    public ResponseEntity<Integer> getMaxHomeDisplayOrder() {
        try {
            Integer maxOrder = productRepository.findMaxHomeDisplayOrder();
            return ResponseEntity.ok(maxOrder != null ? maxOrder : 0);
        } catch (Exception e) {
            return ResponseEntity.ok(0);
        }
    }
}