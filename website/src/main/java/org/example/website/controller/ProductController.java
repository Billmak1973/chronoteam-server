package org.example.website.controller;

import org.example.website.entity.Order;
import org.example.website.entity.Product;
import org.example.website.entity.Review;
import org.example.website.repository.FavoriteRepository;
import org.example.website.repository.OrderItemRepository;
import org.example.website.repository.OrderRepository;
import org.example.website.repository.ProductRepository;
import org.example.website.repository.ReviewRepository;
import org.example.website.service.ViewHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ProductController {
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ViewHistoryService viewHistoryService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final FavoriteRepository favoriteRepository;

    public ProductController(ProductRepository productRepository,
                             ReviewRepository reviewRepository,
                             ViewHistoryService viewHistoryService,
                             OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             FavoriteRepository favoriteRepository) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.viewHistoryService = viewHistoryService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.favoriteRepository = favoriteRepository;
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

        // 2. 根據 sort 參數構建動態排序規則
        Sort dynamicSort;
        switch (sort) {
            case "newest":
                dynamicSort = Sort.by(Sort.Direction.DESC, "createdAt");  // 最新在前
                break;
            case "oldest":
                dynamicSort = Sort.by(Sort.Direction.ASC, "createdAt");   // 最早在前
                break;
            case "popular":
            default:
                // 按點贊數降序，點贊數相同則按時間倒序
                dynamicSort = Sort.by(
                        Sort.Order.desc("likeCount"),
                        Sort.Order.desc("createdAt")
                );
                break;
        }

        // 3. 🟢 使用分頁 + 動態排序查詢根評論（每頁30條）
        Pageable pageable = PageRequest.of(page - 1, 30, dynamicSort);
        Page<Review> rootReviewsPage = reviewRepository.findByProduct_IdAndParentIdIsNull(id, pageable);

        // 4. 傳遞分頁相關數據到前端
        model.addAttribute("reviews", rootReviewsPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", rootReviewsPage.getTotalPages());
        model.addAttribute("totalElements", rootReviewsPage.getTotalElements());
        model.addAttribute("sortOrder", sort);

        // 5. 智能頁碼列表（顯示當前頁 ±2 的頁碼）
        List<Integer> pageNumberList = new ArrayList<>();
        int startPage = Math.max(1, page - 2);
        int endPage = Math.min(rootReviewsPage.getTotalPages(), page + 2);
        for (int i = startPage; i <= endPage; i++) {
            pageNumberList.add(i);
        }
        model.addAttribute("pageNumberList", pageNumberList);

        // 6. 統計每條根評論的樓中樓回復數量
        Map<Long, Long> replyCounts = new HashMap<>();
        for (Review root : rootReviewsPage.getContent()) {
            long count = reviewRepository.countByParentId(root.getId());
            replyCounts.put(root.getId(), count);
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

        //  新增：初始化收藏狀態（預設為 false）
        boolean isFavorite = false;

        // 8. 【核心修改】檢查當前用戶是否可以評價該商品（基於訂單邏輯） & 查詢收藏狀態
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {

            String username = authentication.getName();

            // 記錄瀏覽歷史
            viewHistoryService.recordView(username, id);

            //  查詢當前登入用戶是否已收藏該商品
            if (favoriteRepository.findByCustomer_UsernameAndProduct_Id(username, id) != null) {
                isFavorite = true;
            }

            // 評價邏輯應基於「訂單」而非「用戶+商品」，允許同一商品不同訂單多次評價
            boolean canReview = false;
            String reviewOrderNo = null;
            Review userReview = null;

            // 修改：查詢所有根評論（可能有多條，對應不同訂單）
            List<Review> userReviews = reviewRepository.findByCustomer_UsernameAndProduct_IdAndParentIdIsNull(username, id);

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
                    Order order = orderRepository.findByOrderNoAndCustomer_Username(orderNo, username).orElse(null);
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

            model.addAttribute("userReview", userReview);
            model.addAttribute("canReview", canReview);
            model.addAttribute("reviewOrderNo", reviewOrderNo);
        }

        // 無論用戶是否登入，都將收藏狀態傳遞給前端
        model.addAttribute("isFavorite", isFavorite);

        return "product-detail";
    }
}