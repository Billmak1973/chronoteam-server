package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.dto.ProductUpdateRequest;
import org.example.website.dto.ProductVariantDTO;
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
        // 注意：Spring Data JPA 的 PageRequest 是從 0 開始的，所以這裡要 page - 1
        Pageable pageable = PageRequest.of(page - 1, 30, dynamicSort);
        Page<Review> rootReviewsPage = reviewRepository.findByProduct_ProductIdAndParentIdIsNull(id, pageable);

        // 4. 傳遞分頁相關數據到前端
        model.addAttribute("reviews", rootReviewsPage.getContent());
        model.addAttribute("currentPage", page); // 保持 1-based 給前端顯示
        model.addAttribute("totalPages", rootReviewsPage.getTotalPages());
        model.addAttribute("totalElements", rootReviewsPage.getTotalElements());
        model.addAttribute("sortOrder", sort);

        // ==========================================
        // 5. 【核心修改】使用 PaginationUtils 生成智能分頁列表
        // ==========================================
        // 原來的代碼是手動計算 page-2 到 page+2，現在改用工具類
        // 注意：generateSmartPagination 接收的是 0-based 的 currentPage (即 page.getNumber())
        // 但你的 page 變量是 1-based，所以傳入 page - 1
        List<PaginationUtils.PageItem> smartPages = PaginationUtils.generateSmartPagination(page - 1, rootReviewsPage.getTotalPages());
        model.addAttribute("smartPages", smartPages);

        // 如果你還想保留舊的 pageNumberList 變量名以兼容舊的前端代碼，可以這樣做：
        // model.addAttribute("pageNumberList", smartPages);
        // 但建議前端也對應修改為使用 smartPages 對象列表

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

        // 新增：初始化收藏狀態（預設為 false）
        boolean isFavorite = false;

        // 8. 【核心修改】檢查當前用戶是否可以評價該商品 & 查詢收藏狀態
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {

            String username = authentication.getName();

            // 記錄瀏覽歷史
            viewHistoryService.recordView(username, id);

            // 查詢當前登入用戶是否已收藏該商品
            if (favoriteRepository.findByUser_UsernameAndProduct_ProductId(username, id) != null) {
                isFavorite = true;
            }

            // 評價邏輯變量初始化
            boolean canReview = false;
            String reviewOrderNo = null;
            Review userReview = null;

            // ==========================================
            // 【核心新增】：管理員專屬邏輯 (繞過訂單校驗)
            // ==========================================
            if (isAdmin(authentication)) {
                canReview = true;
                reviewOrderNo = "ADMIN_COMMENT";
            }
            // ==========================================
            // 普通用戶邏輯 (基於訂單狀態)
            // ==========================================
            else {
                // 查詢所有根評論（可能有多條，對應不同訂單）
                List<Review> userReviews = reviewRepository.findByUser_UsernameAndProduct_ProductIdAndParentIdIsNull(username, id);

                // 1. 優先使用傳入的 orderNo (從訂單詳情頁跳轉過來)
                if (orderNo != null) {
                    // 判斷是否已評價過「當前訂單」（通過 orderNo 匹配）
                    if (!userReviews.isEmpty()) {
                        userReview = userReviews.stream()
                                .filter(r -> orderNo.equals(r.getOrderNo()))
                                .findFirst()
                                .orElse(null);
                    }
                    if (userReview == null) {
                        // 未評價，檢查訂單狀態是否允許評價
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
                    // 2. 如果沒有傳入 orderNo (直接訪問商品頁)，查找是否有未評價的已付款訂單
                    List<Order.PaymentStatus> paidStatuses = Arrays.asList(
                            Order.PaymentStatus.PAID_SIMULATED,
                            Order.PaymentStatus.PAID_REAL,
                            Order.PaymentStatus.PAID_OFFLINE
                    );
                    List<String> paidOrderNos = orderItemRepository.findPaidOrderNosByUsernameAndProductId(
                            username, id, paidStatuses
                    );

                    // 遍歷已付款訂單，找到第一個未評價的訂單
                    for (String no : paidOrderNos) {
                        // 檢查該訂單是否已評價（在 userReviews 中查找匹配的 orderNo）
                        boolean alreadyReviewed = userReviews.stream()
                                .anyMatch(r -> no.equals(r.getOrderNo()));
                        if (!alreadyReviewed) {
                            canReview = true;
                            reviewOrderNo = no;
                            break;
                        }
                    }

                    // 如果所有訂單都評價了，或者沒買過，檢查是否有任何評價記錄用於顯示提示
                    if (!canReview && !userReviews.isEmpty()) {
                        userReview = userReviews.get(0);  // 取第一條用於前端顯示提示
                    }
                }
            }

            model.addAttribute("userReview", userReview);
            model.addAttribute("canReview", canReview);
            model.addAttribute("reviewOrderNo", reviewOrderNo);
        }

        // 無論用戶是否登入，都將收藏狀態傳遞給前端
        model.addAttribute("isFavorite", isFavorite);
        model.addAttribute("isAdmin", isAdmin(authentication));
        return "product-detail";
    }

    @PutMapping("/api/admin/product/{id}")
    public ResponseEntity<ApiResponse> updateProduct(
            @PathVariable Integer id,
            @RequestBody ProductUpdateRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("未登入，請先登入"));
        }

        // 权限检查
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("无权操作，仅限管理员"));
        }

        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("商品不存在"));

            // 更新字段
            if (request.getPrice() != null) {
                product.setPrice(request.getPrice());
            }
            if (request.getStock() != null) {
                product.setStock(request.getStock());
            }
            if (request.getCategory() != null) {
                product.setCategory(request.getCategory());
            }
            if (request.getBrand() != null) {
                product.setBrand(request.getBrand());
            }
            if (request.getDescription() != null) {
                product.setDescription(request.getDescription());
            }
            if (request.getDetails() != null) {
                product.setDetails(request.getDetails());
            }
            if (request.getImage() != null) {
                product.setImage(request.getImage());
            }
            if (request.getVisible() != null) {
                product.setVisible(request.getVisible());
            }
            if (request.getConditionVisible() != null) {
                product.setConditionVisible(request.getConditionVisible());
            }
            if (request.getCondition() != null) {
                product.setCondition(request.getCondition());
            }
            if (request.getGroupCode()!=null){
                product.setGroupCode(request.getGroupCode());
            }

            // 3. 【核心修改】处理首页推荐的智能排序逻辑
            // 注意：这里不再直接 product.setHomeDisplayOrder，而是交给 Service 层的智能方法统一处理
            Integer newOrder = request.getHomeDisplayOrder();

            if (newOrder != null && newOrder > 0) {
                // 设置为推荐，并触发智能排序调整（其他商品自动 +1 或 -1）
                productService.updateHomeDisplayOrder(id, newOrder);
            } else {
                // 传入 null，表示取消推荐，触发后续商品排序自动 -1 的逻辑
                productService.updateHomeDisplayOrder(id, null);
            }

            productRepository.save(product);

            return ResponseEntity.ok(ApiResponse.ok("修改成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("修改失败: " + e.getMessage()));
        }
    }

    @GetMapping("/variants/{groupCode}")
    public ResponseEntity<List<ProductVariantDTO>> getProductVariants(@PathVariable String groupCode) {
        List<Product> products = productRepository.findByGroupCode(groupCode);

        //  核心修改：过滤出 conditionVisible = true 的产品
        products = products.stream()
                .filter(p -> p.getConditionVisible() != null && p.getConditionVisible())
                .sorted(Comparator.comparing(Product::getCondition))
                .collect(Collectors.toList());

        List<ProductVariantDTO> variants = products.stream().map(p -> {
            ProductVariantDTO dto = new ProductVariantDTO();
            dto.setProductId(p.getProductId());
            dto.setPrice(p.getPrice());
            dto.setStock(p.getStock());

            // 映射描述和詳情
            dto.setDescription(p.getDescription());
            dto.setDetails(p.getDetails());

            // 映射評論總數和總評分
            dto.setTotalReviewCount(p.getTotalReviewCount());
            dto.setTotalScore(p.getTotalScore());

            // 核心：動態計算平均分 (rating)
            if (p.getTotalReviewCount() != null && p.getTotalReviewCount() > 0 && p.getTotalScore() != null) {
                double avgRating = p.getTotalScore().doubleValue() / p.getTotalReviewCount();
                dto.setRating(avgRating);
            } else {
                dto.setRating(0.0);
            }

            // 映射成色
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

    @GetMapping("/api/admin/products/featured/max-order")
    public ResponseEntity<Integer> getMaxHomeDisplayOrder() {
        try {
            Integer maxOrder = productRepository.findMaxHomeDisplayOrder();
            return ResponseEntity.ok(maxOrder != null ? maxOrder : 0);
        } catch (Exception e) {
            return ResponseEntity.ok(0); // 出错时返回0
        }
    }
}