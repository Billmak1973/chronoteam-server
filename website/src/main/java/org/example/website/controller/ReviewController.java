
package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.service.NotificationService;
import org.example.website.service.ReviewReactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ReviewReactionService reactionService;
    private final CustomerRepository customerRepository; //  新增：用於樓中樓回覆時獲取用戶實體
    private final NotificationService notificationService;
    //  構造函數注入所有依賴
    public ReviewController(ReviewRepository reviewRepository,
                            OrderRepository orderRepository,
                            ProductRepository productRepository,
                            ReviewReactionService reactionService,
                            CustomerRepository customerRepository,
                            NotificationService notificationService) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.reactionService = reactionService;
        this.customerRepository = customerRepository;
        this.notificationService=notificationService;
    }


@PostMapping("/submit")
public ResponseEntity<ApiResponse> submitReview(@RequestBody Map<String, Object> request, Authentication authentication) {
    try {
        //  1. 先验证认证状态
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        String username = authentication.getName();

        //  2. 验证 productId
        if (request.get("productId") == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("商品ID不能為空"));
        }
        Integer productId = Integer.valueOf(request.get("productId").toString());

        //  3. 验证 content（关键修复！）
        String content = request.get("content") != null ? (String) request.get("content") : null;
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("回覆內容不能為空"));
        }
        content = content.trim();

        // 解析樓中樓參數
        Long parentId = request.get("parentId") != null ? Long.valueOf(request.get("parentId").toString()) : null;
        String replyToUser = (String) request.get("replyToUser");

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("商品不存在"));
        Customer customer = customerRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));

        Review review = new Review();
        review.setCustomer(customer);
        review.setProduct(product);
        review.setContent(content);  //  确保 content 不为 null

        if (parentId != null) {
            // ================= 樓中樓回覆邏輯 =================
            review.setParentId(parentId);
            review.setReplyToUser(replyToUser);
            review.setOrderNo(null);
            review.setRating(null);
        } else {
            // ================= 根評論邏輯 =================
            String orderNo = (String) request.get("orderNo");

            if (request.get("rating") == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("評分不能為空"));
            }

            Double rating = Double.valueOf(request.get("rating").toString());

            Order order = orderRepository.findByOrderNoAndCustomer_Username(orderNo, username)
                    .orElseThrow(() -> new RuntimeException("訂單不存在或無權訪問"));

            if (reviewRepository.findByOrderNoAndProduct_Id(orderNo, productId) != null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("您已經評價過該商品"));
            }

            review.setOrderNo(orderNo);
            review.setRating(rating);
            review.setParentId(null);
            review.setReplyToUser(null);

            // 更新商品統計數據
            Integer currentCount = product.getTotalReviewCount();
            product.setTotalReviewCount(currentCount == null ? 1 : currentCount + 1);
            BigDecimal currentScore = product.getTotalScore();
            product.setTotalScore(currentScore == null ? BigDecimal.valueOf(rating) : currentScore.add(BigDecimal.valueOf(rating)));
            productRepository.save(product);
        }

        //  保存評論到數據庫
        reviewRepository.save(review);

        // ================= 新增：發送消息通知邏輯 =================

        // 1. 如果是回復 (有 parentId)，通知父評論的作者
        if (review.getParentId() != null) {
            Review parentReview = reviewRepository.findById(review.getParentId()).orElse(null);
            if (parentReview != null) {
                String parentAuthor = parentReview.getCustomer().getUsername();

                // 如果父評論作者不是自己，則發送回復通知
                if (!parentAuthor.equals(username)) {
                    notificationService.createReplyNotification(
                            parentAuthor,
                            username,
                            review.getId(),
                            review.getContent(),
                            productId // 傳入 productId 用於生成跳轉連結
                    );
                }
            }
        }

        // 2. 檢查內容中是否有 @用戶 (正則匹配 @username)
        // 提示：如果你的用戶名支持中文，正則建議改為 "@([\\w\\u4e00-\\u9fa5]+)"
        Pattern pattern = Pattern.compile("@(\\w+)");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String mentionedUsername = matcher.group(1);
            // 確保 @的人不是自己
            if (!mentionedUsername.equals(username)) {
                // 優化：先去數據庫查一下該用戶是否真實存在，避免產生無效的垃圾通知
                if (customerRepository.existsById(mentionedUsername)) {
                    notificationService.createMentionNotification(
                            mentionedUsername,
                            username,
                            review.getId(),
                            review.getContent(),
                            productId
                    );
                }
            }
        }
        // ==========================================================

        return ResponseEntity.ok(ApiResponse.ok("提交成功"));

    } catch (Exception e) {
        System.err.println(" 提交評論/回覆失敗: " + e.getClass().getName() + " - " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("提交失敗: " + (e.getMessage() != null ? e.getMessage() : "未知錯誤")));
    }
}

    @GetMapping("/{parentId}/replies")
    public ResponseEntity<?> getReplies(@PathVariable Long parentId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(defaultValue = "popular") String sort) {
        try {
            //  根据排序参数构建排序规则
            Sort dynamicSort;
            switch (sort) {
                case "oldest":
                    dynamicSort = Sort.by(Sort.Direction.ASC, "createdAt");
                    break;
                case "newest":
                    dynamicSort = Sort.by(Sort.Direction.DESC, "createdAt");
                    break;
                case "popular":
                default:
                    dynamicSort = Sort.by(
                            Sort.Order.desc("likeCount"),
                            Sort.Order.desc("createdAt")
                    );
                    break;
            }

            Pageable pageable = PageRequest.of(page, size, dynamicSort);
            Page<Review> replies = reviewRepository.findRepliesByParentId(parentId, pageable);

            //  關鍵修復：手動提取需要的字段，避免序列化 Hibernate Proxy
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (Review reply : replies.getContent()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", reply.getId());
                map.put("content", reply.getContent());
                map.put("createdAt", reply.getCreatedAt());
                map.put("likeCount", reply.getLikeCount());
                map.put("dislikeCount", reply.getDislikeCount());
                map.put("replyToUser", reply.getReplyToUser());

                // 提取 Customer 信息（只取需要的字段）
                Map<String, Object> customerMap = new HashMap<>();
                customerMap.put("username", reply.getCustomer().getUsername());
                map.put("customer", customerMap);

                resultList.add(map);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("replies", resultList);  //  使用手動構建的列表
            data.put("currentPage", page);
            data.put("totalPages", replies.getTotalPages());
            data.put("totalReplies", reviewRepository.countByParentId(parentId));

            return ResponseEntity.ok(ApiResponse.okWithData("成功", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 修改評論內容
     */
    @PutMapping("/{id}/update")
    public ResponseEntity<ApiResponse> updateReview(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            String newContent = request.get("content");

            if (newContent == null || newContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("評論內容不能為空"));
            }

            Review review = reviewRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("評論不存在"));

            // 检查是否置顶
            if (Boolean.TRUE.equals(review.getPinned())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("置頂評論不能被修改，請先取消置頂"));
            }

            // 權限校驗：只能修改自己的評論
            if (!review.getCustomer().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(ApiResponse.error("無權修改此評論"));
            }

            // 更新內容
            review.setContent(newContent.trim());
            reviewRepository.save(review);

            return ResponseEntity.ok(ApiResponse.ok("評論修改成功"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("修改評論失敗: " + e.getMessage()));
        }
    }

    /**
     * 刪除評論 (並同步更新商品的統計數據)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteReview(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            String username = authentication.getName();

            Review review = reviewRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("評論不存在"));

            if(Boolean.TRUE.equals(review.getPinned())){
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("置頂評論不能被刪除，請先取消置頂"));
            }

            // 權限校驗：只能刪除自己的評論
            if (!review.getCustomer().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(ApiResponse.error("無權刪除此評論"));
            }

            // 獲取關聯的商品和評分
            Product product = review.getProduct();
            Double rating = review.getRating();

            // 1. 刪除評論記錄
            reviewRepository.delete(review);

            // 2. 同步更新 Product 的統計數據 (優化：僅當刪除的是「根評論」時才扣減商品統計)
            if (product != null && review.getParentId() == null) {
                // 扣減總評論數
                Integer currentCount = product.getTotalReviewCount();
                if (currentCount != null && currentCount > 0) {
                    product.setTotalReviewCount(currentCount - 1);
                } else {
                    product.setTotalReviewCount(0);
                }

                // 扣減總分數
                BigDecimal currentScore = product.getTotalScore();
                if (currentScore != null && rating != null) {
                    BigDecimal deductedScore = currentScore.subtract(BigDecimal.valueOf(rating));
                    // 防止極端情況下出現負數
                    if (deductedScore.compareTo(BigDecimal.ZERO) < 0) {
                        deductedScore = BigDecimal.ZERO;
                    }
                    product.setTotalScore(deductedScore);
                } else {
                    product.setTotalScore(BigDecimal.ZERO);
                }

                // 保存更新後的 Product
                productRepository.save(product);
            }

            return ResponseEntity.ok(ApiResponse.ok("評論已刪除"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("刪除評論失敗: " + e.getMessage()));
        }
    }

    /**
     * 檢查是否可以評價
     */
    @GetMapping("/can-review")
    public ResponseEntity<Map<String, Object>> canReview(
            @RequestParam Integer productId,
            @RequestParam String orderNo,
            Authentication authentication) {

        Map<String, Object> response = new HashMap<>();
        String username = authentication.getName();
        try {
            Order order = orderRepository.findByOrderNoAndCustomer_Username(orderNo, username).orElse(null);
            if (order == null) {
                response.put("canReview", false);
                response.put("message", "訂單不存在");
                return ResponseEntity.ok(response);
            }

            Order.PaymentStatus paymentStatus = order.getPaymentStatus();
            boolean isPaid = (paymentStatus == Order.PaymentStatus.PAID_SIMULATED ||
                    paymentStatus == Order.PaymentStatus.PAID_REAL ||
                    paymentStatus == Order.PaymentStatus.PAID_OFFLINE);

            if (!isPaid) {
                response.put("canReview", false);
                response.put("message", "訂單未付款，不能評價");
                return ResponseEntity.ok(response);
            }

            Review existingReview = reviewRepository.findByOrderNoAndProduct_Id(orderNo, productId);
            if (existingReview != null) {
                response.put("canReview", false);
                response.put("message", "已評價");
                response.put("reviewed", true);
                return ResponseEntity.ok(response);
            }

            response.put("canReview", true);
            response.put("message", "可以評價");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("canReview", false);
            response.put("message", "檢查失敗: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * 點讚
     */
    @PostMapping("/{reviewId}/like")
    public ResponseEntity<Map<String, Object>> likeReview(
            @PathVariable Long reviewId,
            Principal principal) {

        if (principal == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "請先登入");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String username = principal.getName();
        Map<String, Object> result = reactionService.toggleLike(reviewId, username);

        HttpStatus status = result.get("success").equals(true) ?
                HttpStatus.OK : HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(result);
    }

    /**
     * 踩
     */
    @PostMapping("/{reviewId}/dislike")
    public ResponseEntity<Map<String, Object>> dislikeReview(
            @PathVariable Long reviewId,
            Principal principal) {

        if (principal == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "請先登入");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String username = principal.getName();
        Map<String, Object> result = reactionService.toggleDislike(reviewId, username);

        HttpStatus status = result.get("success").equals(true) ?
                HttpStatus.OK : HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(result);
    }


    /**
     * 獲取互動消息列表 (我的評論 / 回復我的 / @我的)
     * 限制：最多只加載最近的 1000 條數據，每頁 30 條
     */
    @GetMapping("/interactions")
    public ResponseEntity<?> getInteractions(
            @RequestParam(defaultValue = "MY") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size, //  默認每頁 30 條
            Authentication authentication) {

        String username = authentication.getName();
        int maxRecords = 1000; //  核心限制：最多 1000 條

        // 🟢攔截：如果請求的起始位置已經超過 1000 條，直接返回空數據
        if (page * size >= maxRecords) {
            Map<String, Object> emptyData = new HashMap<>();
            emptyData.put("content", new ArrayList<>());
            emptyData.put("totalPages", 34); // 1000 / 30 = 33.33 -> 34頁
            emptyData.put("totalElements", maxRecords);
            emptyData.put("currentPage", page);
            return ResponseEntity.ok(ApiResponse.okWithData("success", emptyData));
        }

        //  按時間倒序排列 (選最近的)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> resultPage;

        try {
            switch (type) {
                case "MY":
                    resultPage = reviewRepository.findByCustomer_UsernameOrderByCreatedAtDesc(username, pageable);
                    break;
                case "REPLY":
                    resultPage = reviewRepository.findByReplyToUserAndCustomer_UsernameNotOrderByCreatedAtDesc(username, username, pageable);
                    break;
                case "MENTION":
                    String keyword = "@" + username;
                    resultPage = reviewRepository.findMentions(keyword, username, pageable);
                    break;
                default:
                    return ResponseEntity.badRequest().body(ApiResponse.error("無效的類型"));
            }

            // 手動提取需要的數據，避免 Hibernate 關聯導致的 JSON 循環引用
            List<Map<String, Object>> list = new ArrayList<>();
            for (Review r : resultPage.getContent()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", r.getId());
                map.put("content", r.getContent());
                map.put("createdAt", r.getCreatedAt());
                map.put("rating", r.getRating());
                map.put("parentId", r.getParentId());
                map.put("replyToUser", r.getReplyToUser());

                Map<String, Object> prodInfo = new HashMap<>();
                prodInfo.put("id", r.getProduct().getId());
                prodInfo.put("desc", r.getProduct().getDescription());
                map.put("product", prodInfo);

                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("username", r.getCustomer().getUsername());
                map.put("customer", userInfo);

                list.add(map);
            }

            //  核心：截斷總數，確保前端計算的總頁數不超過 1000 條的限制
            long actualTotal = resultPage.getTotalElements();
            long displayTotal = Math.min(actualTotal, maxRecords); // 強制最大為 1000
            int displayTotalPages = (int) Math.ceil((double) displayTotal / size); // 計算總頁數 (34頁)

            Map<String, Object> data = new HashMap<>();
            data.put("content", list);
            data.put("totalPages", displayTotalPages);
            data.put("totalElements", displayTotal);
            data.put("currentPage", page); // 返回當前頁碼給前端

            return ResponseEntity.ok(ApiResponse.okWithData("success", data));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("查詢失敗"));
        }
    }

    /**
     * 置頂/取消置頂評論 (僅限 Admin)
     */
    @PostMapping("/{reviewId}/pin")
    public ResponseEntity<ApiResponse> pinReview(
            @PathVariable Long reviewId,
            @RequestBody Map<String, Boolean> request,
            Authentication authentication) {
        try {
            // 1. 權限校驗：必須是 admin
            if (!"admin".equals(authentication.getName())) {
                return ResponseEntity.status(403).body(ApiResponse.error("無權操作，僅限管理員"));
            }

            // 2. 查找評論
            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new RuntimeException("評論不存在"));

            // 3. 更新置頂狀態
            Boolean pinned = request.get("pinned");
            if (pinned != null) {
                review.setPinned(pinned);
                reviewRepository.save(review);
                return ResponseEntity.ok(ApiResponse.ok(pinned ? "置頂成功" : "已取消置頂"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("參數錯誤"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("操作失敗: " + e.getMessage()));
        }
    }
}