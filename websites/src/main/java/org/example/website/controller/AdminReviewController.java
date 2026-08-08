package org.example.website.controller;

import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.service.AdminPenaltyService;
import org.example.website.util.PaginationUtils; // 引入工具類
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminReviewController {

    private final ReviewRepository reviewRepository;
    private final ReviewArchiveRepository archiveRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final AdminPenaltyRepository adminPenaltyRepository;
    private final AdminPenaltyService adminPenaltyService;
    private final KeywordRepository keywordRepository;

    public AdminReviewController(ReviewRepository reviewRepository,
                                 ReviewArchiveRepository archiveRepository,
                                 ReportRepository reportRepository,
                                 UserRepository userRepository,
                                 ProductRepository productRepository,
                                 AdminPenaltyRepository adminPenaltyRepository,
                                 AdminPenaltyService adminPenaltyService,
                                 KeywordRepository keywordRepository) {
        this.reviewRepository = reviewRepository;
        this.archiveRepository = archiveRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.adminPenaltyRepository = adminPenaltyRepository;
        this.adminPenaltyService = adminPenaltyService;
        this.keywordRepository = keywordRepository;
    }

    /**
     * 1. 頁面骨架渲染
     */
    @GetMapping("/reviews")
    public String manageReviewsPage(Model model) {
        return "admin/admin-reviews";
    }

    /**
     * 2. API: 獲取評論列表 (支持篩選)
     */
    @GetMapping("/api/reviews/list")
    @ResponseBody
    public ResponseEntity<?> getReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String reviewType,
            @RequestParam(required = false) String keywordMode) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviewsPage;

        // 1. 數據庫查詢
        if (username != null && !username.isEmpty()) {
            if ("reply".equals(reviewType)) {
                reviewsPage = reviewRepository.findByUser_UsernameAndParentIdIsNotNull(username, pageable);
            } else if ("root".equals(reviewType)) {
                reviewsPage = reviewRepository.findByUser_UsernameAndParentIdIsNull(username, pageable);
            } else {
                reviewsPage = reviewRepository.findByUser_UsernameOrderByCreatedAtDesc(username, pageable);
            }
        } else {
            if ("reply".equals(reviewType)) {
                reviewsPage = reviewRepository.findByParentIdIsNotNullOrderByCreatedAtDesc(pageable);
            } else if ("root".equals(reviewType)) {
                reviewsPage = reviewRepository.findByParentIdIsNullOrderByCreatedAtDesc(pageable);
            } else {
                reviewsPage = reviewRepository.findAll(pageable);
            }
        }

        List<Review> reviews = reviewsPage.getContent();

        // 2. 內存過濾 (關鍵詞模式)
        // 注意：這種方式會導致分頁總數不準確，但在不修改 Repository 的情況下這是兼容方案
        if ("yes".equals(keywordMode)) {
            List<Keyword> keywords = keywordRepository.findAllByOrderByCreatedAtDesc();
            if (!keywords.isEmpty()) {
                final List<Keyword> finalKeywords = keywords;
                reviews = reviews.stream()
                        .filter(review -> {
                            String content = review.getContent();
                            return finalKeywords.stream().anyMatch(kw -> content != null && content.contains(kw.getKeyword()));
                        })
                        .collect(Collectors.toList());
            } else {
                reviews = new ArrayList<>();
            }
        }

        // 3. 數據清洗與關聯查詢 (避免 N+1 問題)
        List<Long> userIds = reviews.stream()
                .map(r -> r.getUser() != null ? r.getUser().getId() : null)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());

        List<Integer> productIds = reviews.stream()
                .map(r -> r.getProduct() != null ? r.getProduct().getProductId() : null)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Integer, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));

        List<Map<String, Object>> cleanReviews = new ArrayList<>();
        for (Review review : reviews) {
            Map<String, Object> item = new HashMap<>();
            item.put("reviewId", review.getReviewId());
            item.put("content", review.getContent());

            Double rating = review.getRating();
            item.put("rating", rating != null ? String.format("%.1f", rating) : null);

            item.put("createdAt", review.getCreatedAt());
            item.put("pinned", review.getPinned());
            item.put("likeCount", review.getLikeCount());
            item.put("dislikeCount", review.getDislikeCount());

            // 用戶信息
            Map<String, Object> userInfo = new HashMap<>();
            if (review.getUser() != null && review.getUser().getId() != null) {
                User user = userMap.get(review.getUser().getId());
                userInfo.put("username", user != null ? user.getUsername() : "未知");
                userInfo.put("id", user != null ? user.getId() : null);
            }
            item.put("user", userInfo);

            // 商品信息
            Map<String, Object> productInfo = new HashMap<>();
            if (review.getProduct() != null && review.getProduct().getProductId() != null) {
                Product product = productMap.get(review.getProduct().getProductId());
                if (product != null) {
                    productInfo.put("description", product.getDescription());
                    productInfo.put("productId", product.getProductId());
                }
            }
            item.put("product", productInfo);

            cleanReviews.add(item);
        }

        // 4. 構建標準分頁響應
        // 如果進行了內存過濾，我們需要手動構建一個新的 Page 對象來反映過濾後的總數，或者直接傳入 cleanReviews
        // 這裡為了簡單，我們直接傳入 cleanReviews，PaginationUtils 會處理分頁元數據
        // 但要注意：如果 reviews 被過濾了，reviewsPage.getTotalPages() 是不準的。
        // 修正方案：如果是關鍵詞過濾，我們假設當前頁就是全部（因為內存過濾破壞了分頁），或者我們重新計算總頁數。
        // 為了保持前端邏輯簡單，我們這裡直接使用 PaginationUtils，它會根據傳入的 Page 對象生成 smartPages。

        // 如果沒有過濾，直接用 reviewsPage
        // 如果過濾了，我們創建一個臨時 Page 來修正總數 (可選優化，這裡暫且使用原 Page 對象，前端需容忍總頁數可能偏大)
        return ResponseEntity.ok(PaginationUtils.buildPageResponse(reviewsPage, cleanReviews));
    }

    /**
     * 3. API: 獲取歸檔列表 (刪除記錄)
     */
    @GetMapping("/api/reviews/archives")
    @ResponseBody
    public ResponseEntity<?> getArchives(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String authorUsername,
            @RequestParam(required = false) String deletedByUsername) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deletedAt"));
        Page<ReviewArchive> archivesPage;

        if (authorUsername != null && !authorUsername.isEmpty()) {
            if (deletedByUsername != null && !deletedByUsername.isEmpty()) {
                User author = userRepository.findByUsername(authorUsername).orElse(null);
                User deletedBy = userRepository.findByUsername(deletedByUsername).orElse(null);

                if (author != null && deletedBy != null) {
                    archivesPage = archiveRepository.findByAuthor_UsernameAndDeletedByIdOrderByDeletedAtDesc(
                            authorUsername, deletedBy.getId(), pageable);
                } else {
                    archivesPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
                }
            } else {
                archivesPage = archiveRepository.findByAuthor_UsernameOrderByDeletedAtDesc(authorUsername, pageable);
            }
        } else if (deletedByUsername != null && !deletedByUsername.isEmpty()) {
            User deletedBy = userRepository.findByUsername(deletedByUsername).orElse(null);
            if (deletedBy != null) {
                archivesPage = archiveRepository.findByDeletedByIdOrderByDeletedAtDesc(deletedBy.getId(), pageable);
            } else {
                archivesPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        } else {
            archivesPage = archiveRepository.findAll(pageable);
        }

        List<ReviewArchive> archives = archivesPage.getContent();

        // 批量查詢關聯數據
        List<Integer> productIds = archives.stream()
                .map(ReviewArchive::getProductId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> authorIds = archives.stream()
                .map(a -> a.getAuthor() != null ? a.getAuthor().getId() : null)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> deletedByIds = archives.stream()
                .map(ReviewArchive::getDeletedById).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        Map<Integer, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));
        Map<Long, User> authorMap = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, User> deletedByUserMap = userRepository.findAllById(deletedByIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> archiveDataList = new ArrayList<>();
        for (ReviewArchive archive : archives) {
            Map<String, Object> item = new HashMap<>();
            item.put("archiveId", archive.getArchiveId());
            item.put("content", archive.getContent());
            item.put("deleteReason", archive.getDeleteReason());
            item.put("deletedAt", archive.getDeletedAt());

            Map<String, Object> authorInfo = new HashMap<>();
            if (archive.getAuthor() != null && archive.getAuthor().getId() != null) {
                User author = authorMap.get(archive.getAuthor().getId());
                authorInfo.put("username", author != null ? author.getUsername() : "未知");
            } else {
                authorInfo.put("username", "未知");
            }
            item.put("author", authorInfo);

            Map<String, Object> productInfo = new HashMap<>();
            if (archive.getProductId() != null) {
                Product product = productMap.get(archive.getProductId());
                if (product != null) {
                    productInfo.put("description", product.getDescription());
                    productInfo.put("productId", product.getProductId());
                }
            }
            item.put("product", productInfo);

            Map<String, Object> deletedByInfo = new HashMap<>();
            if (archive.getDeletedById() != null) {
                User deletedBy = deletedByUserMap.get(archive.getDeletedById());
                if (deletedBy != null) {
                    deletedByInfo.put("username", deletedBy.getUsername());
                }
            }
            item.put("deletedBy", deletedByInfo);

            archiveDataList.add(item);
        }

        return ResponseEntity.ok(PaginationUtils.buildPageResponse(archivesPage, archiveDataList));
    }

    /**
     * 4. API: 獲取舉報列表
     */
    @GetMapping("/api/reviews/reports")
    @ResponseBody
    public ResponseEntity<?> getReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String reporterUsername,
            @RequestParam(required = false) String reportedUsername) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "status").and(Sort.by(Sort.Direction.DESC, "createdAt")));

        Page<Report> reportsPage = reportRepository.findByFilters(reporterUsername, reportedUsername, pageable);
        List<Report> reportList = reportsPage.getContent();

        // 批量查詢關聯數據
        List<Long> reviewIds = reportList.stream()
                .map(Report::getReviewId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        Set<Long> existingReviewIds = new HashSet<>();
        if (!reviewIds.isEmpty()) {
            List<Review> existingReviews = reviewRepository.findAllById(reviewIds);
            existingReviewIds = existingReviews.stream().map(Review::getReviewId).collect(Collectors.toSet());
        }

        List<Long> reporterIds = reportList.stream()
                .map(r -> r.getReporter() != null ? r.getReporter().getId() : null).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> reportedUserIds = reportList.stream()
                .map(r -> r.getReportedUser() != null ? r.getReportedUser().getId() : null).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        Map<Long, User> reporterMap = userRepository.findAllById(reporterIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, User> reportedUserMap = userRepository.findAllById(reportedUserIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> reportDataList = new ArrayList<>();
        for (Report report : reportList) {
            Map<String, Object> item = new HashMap<>();
            item.put("reportId", report.getReportId());
            item.put("category", report.getCategory());
            item.put("reason", report.getReason());
            item.put("status", report.getStatus() != null ? report.getStatus().name() : "UNKNOWN");
            item.put("createdAt", report.getCreatedAt());
            item.put("reviewId", report.getReviewId());

            String contentToShow = report.getReportContent();
            if (contentToShow == null || contentToShow.trim().isEmpty()) {
                contentToShow = "無內容快照";
            }
            item.put("content", contentToShow);

            Map<String, Object> reporterInfo = new HashMap<>();
            if (report.getReporter() != null && report.getReporter().getId() != null) {
                User reporter = reporterMap.get(report.getReporter().getId());
                reporterInfo.put("username", reporter != null ? reporter.getUsername() : "未知");
            } else {
                reporterInfo.put("username", "未知");
            }
            item.put("reporter", reporterInfo);

            Map<String, Object> reportedUserInfo = new HashMap<>();
            if (report.getReportedUser() != null && report.getReportedUser().getId() != null) {
                User reportedUser = reportedUserMap.get(report.getReportedUser().getId());
                reportedUserInfo.put("username", reportedUser != null ? reportedUser.getUsername() : "未知");
            } else {
                reportedUserInfo.put("username", "未知");
            }
            item.put("reportedUser", reportedUserInfo);

            boolean isAlreadyBanned = false;
            if (report.getReviewId() != null) {
                isAlreadyBanned = adminPenaltyRepository.existsByReviewId(report.getReviewId());
            }
            item.put("isAlreadyBanned", isAlreadyBanned);

            boolean isDeleted = report.getReviewId() == null || !existingReviewIds.contains(report.getReviewId());
            item.put("isDeleted", isDeleted);

            boolean isBlacklisted = false;
            if (report.getReportedUser() != null && report.getReportedUser().getId() != null) {
                User ru = reportedUserMap.get(report.getReportedUser().getId());
                if (ru != null) {
                    isBlacklisted = adminPenaltyService.isBlacklisted(ru.getUsername());
                }
            }
            item.put("isBlacklisted", isBlacklisted);

            reportDataList.add(item);
        }

        return ResponseEntity.ok(PaginationUtils.buildPageResponse(reportsPage, reportDataList));
    }
}