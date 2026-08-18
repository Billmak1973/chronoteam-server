package org.example.website.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.entity.Review;
import org.example.website.entity.User;
import org.example.website.repository.ReviewReactionRepository;
import org.example.website.repository.ReviewRepository;
import org.example.website.service.NotificationService;
import org.example.website.service.UserService;
import org.example.website.util.PaginationUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/account/reviews")
@Tag(name = "用戶互動管理", description = "用戶評論、回覆、點贊等互動記錄的查詢與狀態管理接口")
public class ReviewInteractionController {

    private final UserService userService;
    private final NotificationService notificationService;
    private final ReviewRepository reviewRepository;
    private final ReviewReactionRepository reviewReactionRepository;

    public ReviewInteractionController(UserService userService,
                                       NotificationService notificationService,
                                       ReviewRepository reviewRepository,
                                       ReviewReactionRepository reviewReactionRepository) {
        this.userService = userService;
        this.notificationService = notificationService;
        this.reviewRepository = reviewRepository;
        this.reviewReactionRepository = reviewReactionRepository;
    }

    /**
     * 渲染互動中心頁面 (Thymeleaf)
     * 使用 @Hidden 隱藏此接口，因為 Swagger 專注於 REST API，不需要展示頁面渲染接口
     */
    @Hidden
    @GetMapping // 確保加上 @GetMapping
    public String myReviewsPage(Model model,
                                Authentication authentication,
                                @RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "MY") String type) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String username = authentication.getName();
        User user = userService.findByUsername(username);
        model.addAttribute("user", user);

        // 計算未讀數
        long unreadReplyCount = !"REPLY".equals(type) ? notificationService.getUnreadCount(username, NotificationService.TYPE_REVIEW_REPLY) : 0;
        long unreadMentionCount = !"MENTION".equals(type) ? notificationService.getUnreadCount(username, NotificationService.TYPE_REVIEW_MENTION) : 0;
        long unreadLikeCount = !"LIKED_ME".equals(type) ? notificationService.getUnreadCount(username, NotificationService.TYPE_LIKED_ME) : 0;

        model.addAttribute("unreadReplyCount", unreadReplyCount);
        model.addAttribute("unreadMentionCount", unreadMentionCount);
        model.addAttribute("unreadLikeCount", unreadLikeCount);

        // 標記已讀
        if (authentication.isAuthenticated()) {
            switch (type) {
                case "REPLY": notificationService.markAsRead(username, NotificationService.TYPE_REVIEW_REPLY); break;
                case "MENTION": notificationService.markAsRead(username, NotificationService.TYPE_REVIEW_MENTION); break;
                case "LIKED_ME": notificationService.markAsRead(username, NotificationService.TYPE_LIKED_ME); break;
            }
        }

        int pageSize = 30;
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        List<Map<String, Object>> cleanReviews = new ArrayList<>();
        int totalPages = 1;
        long totalElements = 0;
        List<PaginationUtils.PageItem> smartPages = new ArrayList<>();

        try {
            switch (type) {
                case "REPLY":
                    var replyPage = reviewRepository.findByReplyToUserAndUser_UsernameNotOrderByCreatedAtDesc(username, username, pageable);
                    cleanReviews = convertReviewsToMap(replyPage.getContent(), username);
                    totalPages = replyPage.getTotalPages();
                    totalElements = replyPage.getTotalElements();
                    smartPages = PaginationUtils.generateSmartPagination(replyPage.getNumber(), totalPages);
                    break;

                case "MENTION":
                    var mentionPage = reviewRepository.findMentions("@" + username, username, pageable);
                    cleanReviews = convertReviewsToMap(mentionPage.getContent(), username);
                    totalPages = mentionPage.getTotalPages();
                    totalElements = mentionPage.getTotalElements();
                    smartPages = PaginationUtils.generateSmartPagination(mentionPage.getNumber(), totalPages);
                    break;

                case "LIKED_BY_ME":
                    var likedByMePage = reviewRepository.findReviewsLikedByMe(username, pageable);
                    cleanReviews = convertReviewsToMap(likedByMePage.getContent(), username);
                    totalPages = likedByMePage.getTotalPages();
                    totalElements = likedByMePage.getTotalElements();
                    smartPages = PaginationUtils.generateSmartPagination(likedByMePage.getNumber(), totalPages);
                    break;

                case "LIKED_ME":
                    // 特殊處理：Repository 返回 Object[]
                    var likesPage = reviewRepository.findLikesOnMyReviews(username, username, pageable);

                    // 批量查詢優化
                    List<Long> reviewIds = likesPage.getContent().stream()
                            .map(row -> ((Review) row[0]).getReviewId())
                            .collect(Collectors.toList());

                    Set<Long> likedReviewIds = new HashSet<>();
                    if (!reviewIds.isEmpty()) {
                        List<org.example.website.entity.ReviewReaction> reactions =
                                reviewReactionRepository.findByReviewIdInAndUser_Username(reviewIds, username);
                        likedReviewIds = reactions.stream()
                                .filter(r -> "LIKE".equals(r.getReactionType()))
                                .map(org.example.website.entity.ReviewReaction::getReviewId)
                                .collect(Collectors.toSet());
                    }

                    for (Object[] row : likesPage.getContent()) {
                        Review r = (Review) row[0];
                        String likerUsername = (String) row[1];
                        LocalDateTime likeTime = (LocalDateTime) row[2];

                        Map<String, Object> map = new HashMap<>();
                        map.put("id", r.getReviewId());
                        map.put("content", r.getContent());
                        map.put("createdAt", r.getCreatedAt());
                        map.put("updatedAt", likeTime);
                        map.put("rating", r.getRating());
                        map.put("likeCount", r.getLikeCount());
                        map.put("parentId", r.getParentId());
                        map.put("replyToUser", r.getReplyToUser());
                        map.put("isLikedByMe", likedReviewIds.contains(r.getReviewId()));

                        // 【修復關鍵】：只提取需要的字段，不要放 Entity 對象
                        Map<String, Object> customerMap = new HashMap<>();
                        customerMap.put("username", likerUsername);
                        map.put("customer", customerMap);

                        if (r.getProduct() != null) {
                            Map<String, Object> productMap = new HashMap<>();
                            productMap.put("id", r.getProduct().getProductId());
                            productMap.put("desc", r.getProduct().getDescription());
                            productMap.put("image", r.getProduct().getImage());
                            map.put("product", productMap);
                        }
                        cleanReviews.add(map);
                    }

                    totalPages = likesPage.getTotalPages();
                    totalElements = likesPage.getTotalElements();
                    smartPages = PaginationUtils.generateSmartPagination(likesPage.getNumber(), totalPages);
                    break;

                case "MY":
                default:
                    var myPage = reviewRepository.findByUser_UsernameOrderByCreatedAtDesc(username, pageable);
                    cleanReviews = convertReviewsToMap(myPage.getContent(), username);
                    totalPages = myPage.getTotalPages();
                    totalElements = myPage.getTotalElements();
                    smartPages = PaginationUtils.generateSmartPagination(myPage.getNumber(), totalPages);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            smartPages = PaginationUtils.generateSmartPagination(0, 1);
        }

        model.addAttribute("reviews", cleanReviews);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalElements", totalElements);
        model.addAttribute("smartPages", smartPages);
        model.addAttribute("currentType", type);

        return "reviews";
    }

    // 輔助方法：確保轉換後的 Map 不包含任何 Hibernate Entity
    private List<Map<String, Object>> convertReviewsToMap(List<Review> reviews, String currentUsername) {
        List<Map<String, Object>> list = new ArrayList<>();

        List<Long> reviewIds = reviews.stream().map(Review::getReviewId).collect(Collectors.toList());
        Set<Long> likedReviewIds = new HashSet<>();

        if (!reviewIds.isEmpty() && currentUsername != null) {
            List<org.example.website.entity.ReviewReaction> reactions =
                    reviewReactionRepository.findByReviewIdInAndUser_Username(reviewIds, currentUsername);
            likedReviewIds = reactions.stream()
                    .filter(r -> "LIKE".equals(r.getReactionType()))
                    .map(org.example.website.entity.ReviewReaction::getReviewId)
                    .collect(Collectors.toSet());
        }

        for (Review r : reviews) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getReviewId());
            map.put("content", r.getContent());
            map.put("createdAt", r.getCreatedAt());
            map.put("rating", r.getRating());
            map.put("likeCount", r.getLikeCount());
            map.put("parentId", r.getParentId());
            map.put("replyToUser", r.getReplyToUser());
            map.put("isLikedByMe", likedReviewIds.contains(r.getReviewId()));

            // 【修復關鍵】：手動提取 Product 信息
            if (r.getProduct() != null) {
                Map<String, Object> productMap = new HashMap<>();
                productMap.put("id", r.getProduct().getProductId());
                productMap.put("desc", r.getProduct().getDescription());
                productMap.put("image", r.getProduct().getImage());
                map.put("product", productMap);
            }

            // 【修復關鍵】：手動提取 User 信息
            if (r.getUser() != null) {
                Map<String, Object> customerMap = new HashMap<>();
                customerMap.put("username", r.getUser().getUsername());
                map.put("customer", customerMap);
            }

            list.add(map);
        }
        return list;
    }

    /**
     * 標記互動消息為已讀 (AJAX API)
     */
    @Operation(
            summary = "標記互動消息為已讀",
            description = "將指定類型（回覆、@提及、點贊我的）的互動消息標記為已讀狀態，用於前端切換 Tab 時清除紅點。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "標記成功"),
            @ApiResponse(responseCode = "400", description = "無效的類型參數"),
            @ApiResponse(responseCode = "401", description = "未登入")
    })
    @PostMapping("/api/mark-read")
    @ResponseBody
    public ResponseEntity<?> markReviewsTabAsRead(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "請求體，需包含 'type' 字段，值為 'REPLY', 'MENTION' 或 'LIKED_ME'",
                    required = true
            )
            @RequestBody Map<String, String> payload,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "未登入"));
        }

        String username = authentication.getName();
        String type = payload.get("type");

        try {
            String serviceType = switch (type) {
                case "REPLY" -> NotificationService.TYPE_REVIEW_REPLY;
                case "MENTION" -> NotificationService.TYPE_REVIEW_MENTION;
                case "LIKED_ME" -> NotificationService.TYPE_LIKED_ME;
                default -> null;
            };

            if (serviceType == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "無效的類型"));
            }

            notificationService.markAsRead(username, serviceType);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "操作失敗"));
        }
    }
}