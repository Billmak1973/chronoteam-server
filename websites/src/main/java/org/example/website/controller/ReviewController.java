package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.service.AdminPenaltyService;
import org.example.website.service.ReviewReactionService;
import org.example.website.repository.*; // 確保引入了 ReviewReactionRepository
import org.example.website.service.UserBlockService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ReviewReactionService reactionService;
    private final UserRepository userRepository; //  新增：用於樓中樓回覆時獲取用戶實體
    private final ReviewReactionRepository reviewReactionRepository;
    private final NotificationRepository notificationRepository;
    private final UserBlockService userBlockService;
    private final UserBlockRepository userBlockRepository;
    private final ReviewArchiveRepository reviewArchiveRepository;
    private final AdminPenaltyService adminPenaltyService;

    //  構造函數注入所有依賴
    public ReviewController(ReviewRepository reviewRepository,
                            OrderRepository orderRepository,
                            ProductRepository productRepository,
                            ReviewReactionService reactionService,
                            UserRepository userRepository,
                            ReviewReactionRepository reviewReactionRepository,
                            NotificationRepository notificationRepository,
                            UserBlockService userBlockService,
                            UserBlockRepository userBlockRepository,
                            ReviewArchiveRepository reviewArchiveRepository,
                            AdminPenaltyService adminPenaltyService
    ) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.reactionService = reactionService;
        this.userRepository = userRepository;
        this.reviewReactionRepository = reviewReactionRepository;
        this.notificationRepository = notificationRepository;
        this.userBlockService = userBlockService;
        this.userBlockRepository = userBlockRepository;
        this.reviewArchiveRepository=reviewArchiveRepository;
        this.adminPenaltyService = adminPenaltyService;
    }



    @PostMapping("/submit")
    public ResponseEntity<ApiResponse> submitReview(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            // 1. 先驗證認證狀態
            if (authentication == null || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
            }

            String username = authentication.getName();

            // ================= 新增：檢查管理員全局禁言 =================
            var activeBan = adminPenaltyService.getActiveGlobalBan(username);
            if (activeBan.isPresent()) {
                // 構造包含過期時間的錯誤數據
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("banned", true);
                errorData.put("expiresAt", activeBan.get().getEndTime()); // 發送 ISO 格式時間給前端

                // 返回特定錯誤 "GLOBAL_BAN"，前端 React 會捕獲這個 message 並彈窗
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "GLOBAL_BAN", errorData));
            }
            // ================= 檢查結束 =================

            //檢查是否被管理員永久拉黑
            if (adminPenaltyService.isBlacklisted(username)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse(false, "BLACKLISTED", "您已被管理員永久拉黑，無法發表評論或回復"));
            }

            String replyToUser = (String) request.get("replyToUser");
            if (replyToUser != null && !replyToUser.trim().isEmpty()) {
                // 調用 UserBlockService 檢查 A→B 或 B→A 是否存在 (普通用戶互相禁言)
                if (userBlockService.isBlocked(username, replyToUser)) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("您已禁言對方，因此無法回復"));
                }
            }

            // 2. 驗證 productId
            if (request.get("productId") == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("商品ID不能為空"));
            }
            Integer productId = Integer.valueOf(request.get("productId").toString());

            // 3. 驗證並處理 content（核心修改：區分純文本與富文本）
            String rawContent = request.get("content") != null ? (String) request.get("content") : null;
            if (rawContent == null || rawContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("回覆內容不能為空"));
            }

            // 【核心修復 1】：使用 Jsoup 清理 HTML，防止 XSS 攻擊
            // 升級白名單：允許常見的富文本標籤以及 style 屬性（用於字體顏色、背景色等）
            Safelist safelist = Safelist.relaxed()
                    .addTags("span", "div", "ul", "ol", "li", "u", "s", "font", "b", "i", "strong", "em", "mark")
                    .addAttributes(":all", "style", "class", "id") // 關鍵：允許所有標籤攜帶 style 屬性
                    .addAttributes("span", "style", "class")
                    .addAttributes("div", "style", "class")
                    .addAttributes("font", "color", "size", "face") // 兼容舊版字體標籤
                    .addProtocols("img", "src", "https", "http")
                    .preserveRelativeLinks(true);

            String safeHtml = Jsoup.clean(rawContent, safelist);

            // 【核心修復 2】：提取純文本，用於 content 字段 (方便後台搜索、發送系統通知時不帶 HTML 標籤)
            String plainText = Jsoup.parse(safeHtml).text();

            if (plainText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("回覆內容不能為空"));
            }

            // 解析樓中樓參數 (parentId)
            // 注意：replyToUser 已在上方解析，此處直接使用即可
            Long parentId = request.get("parentId") != null ? Long.valueOf(request.get("parentId").toString()) : null;

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("商品不存在"));

            // 修改：使用 UserRepository 獲取 User 實體
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("用戶不存在"));

            Review review = new Review();
            review.setUser(user); // 修改：設置 User 關聯
            review.setProduct(product);

            // 【核心修復 3】：分別設置純文本和富文本字段
            review.setContent(plainText);           // 存純文本 (用於通知、搜索)
            review.setFormattedContent(safeHtml);   // 存安全的 HTML (用於前端渲染)
            review.setIsFormatted(true);            // 標記為富文本

            if (parentId != null) {
                // ================= 樓中樓回覆邏輯 =================
                review.setParentId(parentId);
                review.setReplyToUser(replyToUser);
                review.setOrderNo(null);
                review.setRating(null);
            } else {
                // ================= 根評論邏輯 =================
                // 核心修復：精準判斷是否為管理員 (同時校驗 username 和 Role 枚舉)
                boolean isAdmin = "admin".equals(username) ||
                        (user.getRole() != null && user.getRole() == User.Role.ADMIN);

                if (isAdmin) {
                    // 【管理員邏輯】：無需訂單、無需評分、絕對不更新統計數據
                    review.setOrderNo("ADMIN_COMMENT"); // 特殊標識
                    review.setRating(null);             // 評分為 null
                } else {
                    // 【普通用戶邏輯】：必須有評分、必須有訂單、必須更新統計
                    String orderNo = (String) request.get("orderNo");

                    // 普通用戶必須有評分
                    if (request.get("rating") == null) {
                        return ResponseEntity.badRequest().body(ApiResponse.error("評分不能為空"));
                    }
                    Double rating = Double.valueOf(request.get("rating").toString());

                    // 校驗訂單
                    Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username)
                            .orElseThrow(() -> new RuntimeException("訂單不存在或無權訪問"));

                    if (reviewRepository.findByOrderNoAndProduct_ProductId(orderNo, productId) != null) {
                        return ResponseEntity.badRequest().body(ApiResponse.error("您已經評價過該商品"));
                    }

                    review.setOrderNo(orderNo);
                    review.setRating(rating);

                    // 更新商品統計數據 (僅普通用戶觸發)
                    Integer currentCount = product.getTotalReviewCount();
                    product.setTotalReviewCount(currentCount == null ? 1 : currentCount + 1);
                    BigDecimal currentScore = product.getTotalScore();
                    product.setTotalScore(currentScore == null ? BigDecimal.valueOf(rating) : currentScore.add(BigDecimal.valueOf(rating)));
                    productRepository.save(product);
                }

                review.setParentId(null);
                review.setReplyToUser(null);
            }

            // 保存評論到數據庫
            reviewRepository.save(review);

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
                                        @RequestParam(defaultValue = "popular") String sort,
                                        Authentication authentication) {
        try {
            // 根據排序參數構建排序規則
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

            // 獲取當前登錄用戶名
            String currentUsername = null;
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                currentUsername = authentication.getName();
            }
            final String username = currentUsername;

            // 手動提取需要的字段，避免序列化 Hibernate Proxy
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (Review reply : replies.getContent()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", reply.getReviewId());
                map.put("content", reply.getContent());
                map.put("createdAt", reply.getCreatedAt());
                map.put("likeCount", reply.getLikeCount());
                map.put("dislikeCount", reply.getDislikeCount());
                map.put("replyToUser", reply.getReplyToUser());

                // 提取 User 信息（只取需要的字段）
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("username", reply.getUser().getUsername());
                map.put("customer", userMap);

                // 查詢當前用戶是否對這條樓中樓點讚/踩
                boolean isLikedByMe = false;
                boolean isDislikedByMe = false;
                if (username != null) {
                    var reactionOpt = reviewReactionRepository.findByReviewIdAndUser_Username(reply.getReviewId(), username);
                    if (reactionOpt.isPresent()) {
                        var reaction = reactionOpt.get();
                        if ("LIKE".equals(reaction.getReactionType())) {
                            isLikedByMe = true;
                        } else if ("DISLIKE".equals(reaction.getReactionType())) {
                            isDislikedByMe = true;
                        }
                    }
                }
                map.put("isLikedByMe", isLikedByMe);       // 發送給前端
                map.put("isDislikedByMe", isDislikedByMe); // 發送給前端

                resultList.add(map);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("replies", resultList);  // 使用手動構建的列表
            data.put("currentPage", page);
            data.put("totalPages", replies.getTotalPages());
            data.put("totalReplies", reviewRepository.countByParentId(parentId));

            return ResponseEntity.ok(ApiResponse.okWithData("成功", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }


    @PutMapping("/{id}/update")
    public ResponseEntity<ApiResponse> updateReview(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request, // 【核心修改 1】：改為 Object 以接收前端傳來的 isFormatted 布爾值
            Authentication authentication) {
        try {
            String username = authentication.getName();

            // 獲取前端傳來的內容
            String rawContent = request.get("content") != null ? request.get("content").toString() : null;

            if (rawContent == null || rawContent.trim().isEmpty()) {
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
            if (!review.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(ApiResponse.error("無權修改此評論"));
            }

            // 檢查當前用戶是否被管理員全局禁言
            var activeBan = adminPenaltyService.getActiveGlobalBan(username);
            if (activeBan.isPresent()) {
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("banned", true);
                errorData.put("expiresAt", activeBan.get().getEndTime());

                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "GLOBAL_BAN", errorData));
            }

            // ================= 檢查是否被管理員永久拉黑 =================
            if (adminPenaltyService.isBlacklisted(username)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ApiResponse(false, "BLACKLISTED", "您已被管理員永久拉黑，無法修改評論"));
            }

            // ================= 【核心修改 2】：像 submitReview 一樣處理富文本 =================

            // 1. 配置 Jsoup 白名單 (與 submitReview 完全一致，允許 style 等屬性)
            Safelist safelist = Safelist.relaxed()
                    .addTags("span", "div", "ul", "ol", "li", "u", "s", "font", "b", "i", "strong", "em", "mark")
                    .addAttributes(":all", "style", "class", "id") // 關鍵：允許所有標籤攜帶 style 屬性
                    .addAttributes("span", "style", "class")
                    .addAttributes("div", "style", "class")
                    .addAttributes("font", "color", "size", "face") // 兼容舊版字體標籤
                    .addProtocols("img", "src", "https", "http")
                    .preserveRelativeLinks(true);

            // 2. 清理 HTML，防止 XSS 攻擊
            String safeHtml = Jsoup.clean(rawContent, safelist);

            // 3. 提取純文本 (用於後台搜索、發送系統通知時不帶 HTML 標籤)
            String plainText = Jsoup.parse(safeHtml).text();

            if (plainText.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("回覆內容不能為空"));
            }

            // 4. 判斷前端是否明確指示這是富文本 (如果前端沒傳，默認視為富文本)
            boolean isFormatted = request.containsKey("isFormatted")
                    ? Boolean.parseBoolean(request.get("isFormatted").toString())
                    : true;

            // 5. 更新 Review 實體的字段
            review.setContent(plainText);           // 存純文本
            review.setFormattedContent(safeHtml);   // 存安全的 HTML (用於前端渲染顏色/格式)
            review.setIsFormatted(isFormatted);     // 標記是否為富文本

            // =====================================================================

            reviewRepository.save(review);

            return ResponseEntity.ok(ApiResponse.ok("評論修改成功"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(ApiResponse.error("修改評論失敗: " + e.getMessage()));
        }
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteReview(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> requestBody,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            Review review = reviewRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("評論不存在"));

            if (Boolean.TRUE.equals(review.getPinned())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("置頂評論不能被刪除，請先取消置頂"));
            }

            // 權限校驗：管理員或評論作者
            if (!review.getUser().getUsername().equals(username) && !"admin".equals(username)) {
                return ResponseEntity.status(403).body(ApiResponse.error("無權刪除此評論"));
            }

            // ==========================================
            //  核心修改：獲取刪除原因 (用戶自行刪除留空，管理員刪除則讀取前端傳來的原因，不再寫死默認值)
            String deleteReason = null;

            // 只有管理員刪除時，才會嘗試從請求體中獲取原因
            if ("admin".equals(username) && requestBody != null && requestBody.containsKey("deleteReason")) {
                String reasonFromBody = requestBody.get("deleteReason");
                if (reasonFromBody != null && !reasonFromBody.trim().isEmpty()) {
                    deleteReason = reasonFromBody.trim();
                }
            }
            // ==========================================

            // 日誌記錄：方便後續審計 (優化輸出，避免打印 null)
            System.out.println("刪除評論 ID: " + id + ", 操作者: [" + username + "], 原因: " + (deleteReason != null ? deleteReason : "無"));

            // 獲取關聯的商品和評分
            Product product = review.getProduct();
            Double rating = review.getRating();

            // 保存被刪除的內容和用戶名，用於發送通知與歸檔
            String deletedContent = review.getContent();
            User targetUser = review.getUser();
            Long relatedReviewId = review.getReviewId();

            // ==========================================
            //  【核心新增】：將評論歸檔到 ReviewArchive (無論是用戶自己刪還是管理員刪，都會執行這裡)
            ReviewArchive archive = new ReviewArchive();
            archive.setOriginalReviewId(relatedReviewId);
            archive.setProductId(product.getProductId());
            archive.setAuthor(targetUser);
            archive.setContent(deletedContent);
            archive.setRating(rating);
            archive.setParentId(review.getParentId());
            archive.setReplyToUser(review.getReplyToUser());
            archive.setOriginalCreatedAt(review.getCreatedAt());

            // 查找執行刪除操作的 User 實體 (如果是 admin 字符串，需查詢對應 User，若為系統自動刪除可為 null)
            User operatorUser = userRepository.findByUsername(username).orElse(null);
            archive.setDeletedById(operatorUser != null ? operatorUser.getId() : null);

            archive.setDeleteReason(deleteReason);    // 記錄刪除原因 (用戶自刪為 null，管理員刪則為填寫的原因)
            archive.setLikeCountAtDelete(review.getLikeCount());
            archive.setDislikeCountAtDelete(review.getDislikeCount());

            reviewArchiveRepository.save(archive);    // 保存快照到歸檔表
            // ==========================================

            // 1. 刪除評論記錄 (在歸檔之後執行)
            reviewRepository.delete(review);

            // ==========================================
            // 2. 同步更新 Product 的統計數據 (僅針對 普通用戶 的根評論)
            // ==========================================
            // 核心修復：判斷被刪除的評論是否為管理員發表的
            boolean isDeletedReviewByAdmin = "ADMIN_COMMENT".equals(review.getOrderNo()) ||
                    (review.getUser().getRole() != null && review.getUser().getRole() == User.Role.ADMIN);

            // 只有當它是根評論，且【不是】管理員發表的，才去扣除統計數據
            if (product != null && review.getParentId() == null && !isDeletedReviewByAdmin) {
                Integer currentCount = product.getTotalReviewCount();
                if (currentCount != null && currentCount > 0) {
                    product.setTotalReviewCount(currentCount - 1);
                } else {
                    product.setTotalReviewCount(0);
                }

                BigDecimal currentScore = product.getTotalScore();
                if (currentScore != null && rating != null) {
                    BigDecimal deductedScore = currentScore.subtract(BigDecimal.valueOf(rating));
                    if (deductedScore.compareTo(BigDecimal.ZERO) < 0) {
                        deductedScore = BigDecimal.ZERO;
                    }
                    product.setTotalScore(deductedScore);
                }
                productRepository.save(product);
            }

            // 3. 創建系統通知發送給用戶 (僅限管理員刪除時)
            if ("admin".equals(username)) {
                Notification notification = new Notification();
                notification.setRecipient(targetUser);

                User adminUser = userRepository.findByUsername(username).orElse(null);
                notification.setSender(adminUser);

                notification.setType(Notification.NotificationType.SYSTEM);
                notification.setTitle("您的評論已被移除");
                notification.setContent("管理員對您的評論進行了處理。");
                notification.setDeletedContent(deletedContent); // 存儲原文
                notification.setDeleteReason(deleteReason);     // 存儲原因 (如果管理員沒填則為 null，前端會自動隱藏該區塊)
                notification.setRelatedReviewId(relatedReviewId);
                // createdAt 由 @CreationTimestamp 自動處理

                notificationRepository.save(notification);
            }

            return ResponseEntity.ok(ApiResponse.ok("評論已刪除"));
        } catch (Exception e) {
            e.printStackTrace();
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
            Order order = orderRepository.findByOrderNoAndUser_Username(orderNo, username).orElse(null);
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

            Review existingReview = reviewRepository.findByOrderNoAndProduct_ProductId(orderNo, productId);
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


    @GetMapping("/interactions")
    public ResponseEntity<?> getInteractions(
            @RequestParam(defaultValue = "MY") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {

        String username = authentication.getName();
        int maxRecords = 1000;

        if (page * size >= maxRecords) {
            Map<String, Object> emptyData = new HashMap<>();
            emptyData.put("content", new ArrayList<>());
            emptyData.put("totalPages", 34);
            emptyData.put("totalElements", maxRecords);
            emptyData.put("currentPage", page);
            return ResponseEntity.ok(ApiResponse.okWithData("success", emptyData));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> resultPage;

        try {
            switch (type) {
                case "MY":
                    resultPage = reviewRepository.findByUser_UsernameOrderByCreatedAtDesc(username, pageable);
                    break;
                case "REPLY":
                    resultPage = reviewRepository.findByReplyToUserAndUser_UsernameNotOrderByCreatedAtDesc(username, username, pageable);
                    break;
                case "MENTION":
                    String keyword = "@" + username;
                    resultPage = reviewRepository.findMentions(keyword, username, pageable);
                    break;
                case "LIKED_BY_ME":
                    resultPage = reviewRepository.findReviewsLikedByMe(username, pageable);
                    break;
                case "LIKED_ME":
                    // 特殊處理：返回 Review，實際數據在後面單獨查詢
                    resultPage = Page.empty();
                    break;
                default:
                    return ResponseEntity.badRequest().body(ApiResponse.error("無效的類型"));
            }

            List<Map<String, Object>> list = new ArrayList<>();

            // 特殊處理 LIKED_ME
            if ("LIKED_ME".equals(type)) {
                Page<Object[]> likesPage = reviewRepository.findLikesOnMyReviews(username, username, pageable);

                for (Object[] row : likesPage.getContent()) {
                    Review r = (Review) row[0];
                    String likerUsername = (String) row[1];
                    LocalDateTime likeTime = (LocalDateTime) row[2];

                    Map<String, Object> map = new HashMap<>();
                    map.put("id", r.getReviewId());
                    map.put("content", r.getContent());
                    map.put("createdAt", r.getCreatedAt());
                    map.put("rating", r.getRating());
                    map.put("parentId", r.getParentId());
                    map.put("replyToUser", r.getReplyToUser());
                    map.put("likeCount", r.getLikeCount());
                    map.put("updatedAt", likeTime); // 點贊時間

                    // 點贊者信息
                    Map<String, Object> likerInfo = new HashMap<>();
                    likerInfo.put("username", likerUsername);
                    map.put("customer", likerInfo);

                    Map<String, Object> prodInfo = new HashMap<>();
                    prodInfo.put("id", r.getProduct().getProductId());
                    prodInfo.put("desc", r.getProduct().getDescription());
                    map.put("product", prodInfo);

                    list.add(map);
                }

                // 更新總數
                long actualTotal = likesPage.getTotalElements();
                long displayTotal = Math.min(actualTotal, maxRecords);
                int displayTotalPages = (int) Math.ceil((double) displayTotal / size);

                Map<String, Object> data = new HashMap<>();
                data.put("content", list);
                data.put("totalPages", displayTotalPages);
                data.put("totalElements", displayTotal);
                data.put("currentPage", page);

                return ResponseEntity.ok(ApiResponse.okWithData("success", data));
            }

            // 其他類型的原有邏輯
            for (Review r : resultPage.getContent()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", r.getReviewId());
                map.put("content", r.getContent());
                map.put("createdAt", r.getCreatedAt());
                map.put("rating", r.getRating());
                map.put("parentId", r.getParentId());
                map.put("replyToUser", r.getReplyToUser());
                map.put("likeCount", r.getLikeCount());
                map.put("updatedAt", r.getCreatedAt());//无法解析 'Review' 中的方法 'getUpdatedAt'

                Map<String, Object> prodInfo = new HashMap<>();
                prodInfo.put("id", r.getProduct().getProductId());
                prodInfo.put("desc", r.getProduct().getDescription());
                map.put("product", prodInfo);

                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("username", r.getUser().getUsername());
                map.put("customer", userInfo);

                list.add(map);
            }

            long actualTotal = resultPage.getTotalElements();
            long displayTotal = Math.min(actualTotal, maxRecords);
            int displayTotalPages = (int) Math.ceil((double) displayTotal / size);

            Map<String, Object> data = new HashMap<>();
            data.put("content", list);
            data.put("totalPages", displayTotalPages);
            data.put("totalElements", displayTotal);
            data.put("currentPage", page);

            return ResponseEntity.ok(ApiResponse.okWithData("success", data));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(ApiResponse.error("查詢失敗: " + e.getMessage()));
        }
    }

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
                //  核心修復：如果要設置為置頂，先檢查該商品已置頂的數量
                if (pinned) {
                    long pinnedCount = reviewRepository.countByProduct_ProductIdAndPinned(review.getProduct().getProductId(), true);
                    if (pinnedCount >= 3) {
                        return ResponseEntity.badRequest().body(ApiResponse.error("每個物品的評論區最多只能置頂三條評論，請先取消先前的置頂！"));
                    }
                }

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


    @GetMapping("/product/{productId}/root")
    public ResponseEntity<?> getRootReviews(
            @PathVariable Integer productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "popular") String sort,
            Authentication authentication) {

        // 1. 構建排序規則 (置頂優先)
        Sort dynamicSort;
        switch (sort) {
            case "newest":
                dynamicSort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt"));
                break;
            case "oldest":
                dynamicSort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.asc("createdAt"));
                break;
            default: // popular
                dynamicSort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"));
                break;
        }
        Pageable pageable = PageRequest.of(page, size, dynamicSort);

        // 2. 查詢根評論
        Page<Review> rootReviewsPage = reviewRepository.findByProduct_ProductIdAndParentIdIsNull(productId, pageable);

        // 3. 統計每條根評論的樓中樓數量 (避免 N+1 問題)
        Map<Long, Long> replyCounts = new HashMap<>();
        for (Review root : rootReviewsPage.getContent()) {
            replyCounts.put(root.getReviewId(), reviewRepository.countByParentId(root.getReviewId()));
        }

        //  4. 獲取當前用戶名 (如果未登錄則為 null)
        String currentUsername = null;
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            currentUsername = authentication.getName();
        }
        final String username = currentUsername; // 用於 lambda 或內部邏輯

        // 5. 手動提取數據，避免 Hibernate 循環引用，並加入點贊狀態
        List<Map<String, Object>> list = new ArrayList<>();
        for (Review r : rootReviewsPage.getContent()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getReviewId());
            map.put("content", r.getContent());
            map.put("rating", r.getRating());
            map.put("likeCount", r.getLikeCount());
            map.put("dislikeCount", r.getDislikeCount());
            map.put("pinned", r.getPinned());
            map.put("createdAt", r.getCreatedAt());
            map.put("replyCount", replyCounts.getOrDefault(r.getReviewId(), 0L));
            map.put("formattedContent", r.getFormattedContent());
            map.put("isFormatted", r.getIsFormatted());

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("username", r.getUser().getUsername());
            map.put("customer", userMap);

            //  6. 檢查當前用戶是否點贊/踩
            boolean isLikedByMe = false;
            boolean isDislikedByMe = false;

            if (username != null) {
                // 查詢當前用戶對這條評論的反應
                // 注意：這裡需要確保 reviewReactionRepository 已經注入到 Controller 中
                var reactionOpt = reviewReactionRepository.findByReviewIdAndUser_Username(r.getReviewId(), username);

                if (reactionOpt.isPresent()) {
                    var reaction = reactionOpt.get();
                    if ("LIKE".equals(reaction.getReactionType())) {
                        isLikedByMe = true;
                    } else if ("DISLIKE".equals(reaction.getReactionType())) {
                        isDislikedByMe = true;
                    }
                }
            }

            map.put("isLikedByMe", isLikedByMe);       // 發送給前端
            map.put("isDislikedByMe", isDislikedByMe); // 發送給前端

            list.add(map);
        }

        // 7. 構建返回數據並計算平均分
        Map<String, Object> data = new HashMap<>();
        data.put("reviews", list);
        data.put("currentPage", page);
        data.put("totalPages", rootReviewsPage.getTotalPages());
        data.put("totalElements", rootReviewsPage.getTotalElements());

        // 核心修復：計算並返回平均分
        Product product = productRepository.findById(productId).orElse(null);
        if (product != null) {
            // 【關鍵修復】：除數必須使用 product.getTotalReviewCount() (只包含普通用戶的評論數)
            // 絕對不能用 rootReviewsPage.getTotalElements() (這會包含管理員的官方備註)
            Integer validReviewCount = product.getTotalReviewCount();
            double avgRating = 0.0;

            // 只有當普通用戶評論數大於 0 且總分不為空時才計算
            if (validReviewCount != null && validReviewCount > 0 && product.getTotalScore() != null) {
                avgRating = product.getTotalScore().doubleValue() / validReviewCount;
            }

            // 保留一位小數 (例如: 4.12 -> 4.1)
            data.put("avgRating", Math.round(avgRating * 10) / 10.0);
            data.put("totalScore", product.getTotalScore());
        } else {
            // 如果商品不存在，返回默認值防止前端報錯
            data.put("avgRating", 0.0);
            data.put("totalScore", 0.0);
        }

        return ResponseEntity.ok(ApiResponse.okWithData("成功", data));
    }
}