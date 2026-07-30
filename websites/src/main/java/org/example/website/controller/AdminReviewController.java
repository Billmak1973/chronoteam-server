package org.example.website.controller;

import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.service.AdminPenaltyService;
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
                                 AdminPenaltyService adminPenaltyService, KeywordRepository keywordRepository) {
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
     * 1. 頁面骨架渲染 (首次加載時僅渲染基礎 HTML 框架，數據交由前端 AJAX 異步獲取)
     */
    @GetMapping("/reviews")
    public String manageReviewsPage(Model model) {
        return "admin/admin-reviews";
    }



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

        // ========== 1. 數據庫層面精準分頁查詢 ==========
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
                // 無任何篩選條件，查詢所有
                reviewsPage = reviewRepository.findAll(pageable);
            }
        }

        List<Review> reviews = reviewsPage.getContent();

        // ========== 2. 關鍵詞篩選 (若開啟，在內存中過濾當前頁數據) ==========
        // 註：由於分頁限制，關鍵詞篩選僅對「當前頁的25條數據生效」。
        // 若需全局關鍵詞篩選，需在 Repository 中添加 LIKE 查詢。
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
                reviews = new ArrayList<>(); // 沒有設置關鍵詞，視為無匹配
            }
        }

        // ========== 3. 批量加載關聯數據，避免 N+1 查詢 ==========
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

        // ========== 4. 組裝乾淨的 JSON 數據 ==========
        List<Map<String, Object>> cleanReviews = new ArrayList<>();
        for (Review review : reviews) {
            Map<String, Object> item = new HashMap<>();
            item.put("reviewId", review.getReviewId());
            item.put("content", review.getContent());

            Double rating = review.getRating();
            if (rating != null) {
                item.put("rating", String.format("%.1f", rating)); // 確保保留一位小數
            } else {
                item.put("rating", null);
            }

            item.put("createdAt", review.getCreatedAt());
            item.put("pinned", review.getPinned());
            item.put("likeCount", review.getLikeCount());
            item.put("dislikeCount", review.getDislikeCount());

            // 組裝 User 信息
            Map<String, Object> userInfo = new HashMap<>();
            if (review.getUser() != null && review.getUser().getId() != null) {
                User user = userMap.get(review.getUser().getId());
                userInfo.put("username", user != null ? user.getUsername() : "未知");
                userInfo.put("id", user != null ? user.getId() : null);
            }
            item.put("user", userInfo);

            // 組裝 Product 信息
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

        // ========== 5. 返回分頁響應 ==========
        Map<String, Object> response = new HashMap<>();
        response.put("content", cleanReviews);
        response.put("currentPage", reviewsPage.getNumber());
        // 確保即使過濾後為空，總頁數也至少為 1，防止前端分頁組件消失
        response.put("totalPages", reviewsPage.getTotalPages() == 0 ? 1 : reviewsPage.getTotalPages());
        response.put("totalElements", reviewsPage.getTotalElements());

        return ResponseEntity.ok(response);
    }


    /**
     * 3. 獨立 API：獲取「刪除歸檔」分頁數據 (已修復代理序列化問題 + 支持篩選)
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

        // ========== 1. 數據庫層面精準分頁查詢（根據篩選條件）==========
        if (authorUsername != null && !authorUsername.isEmpty()) {
            if (deletedByUsername != null && !deletedByUsername.isEmpty()) {
                // 同时筛选原作者和执行删除者
                // 先查询用户ID
                User author = userRepository.findByUsername(authorUsername).orElse(null);
                User deletedBy = userRepository.findByUsername(deletedByUsername).orElse(null);

                if (author != null && deletedBy != null) {
                    archivesPage = archiveRepository.findByAuthor_UsernameAndDeletedByIdOrderByDeletedAtDesc(
                            authorUsername, deletedBy.getId(), pageable);
                } else {
                    // 用户不存在，返回空结果
                    archivesPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
                }
            } else {
                // 只筛选原作者
                archivesPage = archiveRepository.findByAuthor_UsernameOrderByDeletedAtDesc(authorUsername, pageable);
            }
        } else if (deletedByUsername != null && !deletedByUsername.isEmpty()) {
            // 只筛选执行删除者
            User deletedBy = userRepository.findByUsername(deletedByUsername).orElse(null);
            if (deletedBy != null) {
                archivesPage = archiveRepository.findByDeletedByIdOrderByDeletedAtDesc(deletedBy.getId(), pageable);
            } else {
                // 用户不存在，返回空结果
                archivesPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        } else {
            // 无任何筛选条件，查询所有
            archivesPage = archiveRepository.findAll(pageable);
        }

        List<ReviewArchive> archives = archivesPage.getContent();

        // ========== 2. 批量加載關聯數據，避免 N+1 查詢 ==========
        List<Integer> productIds = archives.stream()
                .map(ReviewArchive::getProductId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        List<Long> authorIds = archives.stream()
                .map(a -> a.getAuthor() != null ? a.getAuthor().getId() : null)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());

        List<Long> deletedByIds = archives.stream()
                .map(ReviewArchive::getDeletedById).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        // 3. 批量查詢關聯實體
        Map<Integer, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));
        Map<Long, User> authorMap = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, User> deletedByUserMap = userRepository.findAllById(deletedByIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 4. 組裝純淨的 Map 數據 (徹底杜絕 Hibernate 代理對象)
        List<Map<String, Object>> archiveDataList = new ArrayList<>();
        for (ReviewArchive archive : archives) {
            Map<String, Object> item = new HashMap<>();

            // 直接將 archive 的字段放到 item 中，而不是嵌套在 archive 對象裡
            item.put("archiveId", archive.getArchiveId());
            item.put("content", archive.getContent());
            item.put("deleteReason", archive.getDeleteReason());
            item.put("deletedAt", archive.getDeletedAt());

            // 手動組裝 author 信息
            Map<String, Object> authorInfo = new HashMap<>();
            if (archive.getAuthor() != null && archive.getAuthor().getId() != null) {
                User author = authorMap.get(archive.getAuthor().getId());
                authorInfo.put("username", author != null ? author.getUsername() : "未知");
            } else {
                authorInfo.put("username", "未知");
            }
            item.put("author", authorInfo);

            // 手動組裝 product 信息
            Map<String, Object> productInfo = new HashMap<>();
            if (archive.getProductId() != null) {
                Product product = productMap.get(archive.getProductId());
                if (product != null) {
                    productInfo.put("description", product.getDescription());
                    productInfo.put("productId", product.getProductId());
                }
            }
            item.put("product", productInfo);

            // 手動組裝 deletedBy 信息
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

        // ========== 5. 返回分頁響應 ==========
        Map<String, Object> response = new HashMap<>();
        response.put("content", archiveDataList);
        response.put("currentPage", archivesPage.getNumber());
        response.put("totalPages", archivesPage.getTotalPages() == 0 ? 1 : archivesPage.getTotalPages());
        response.put("totalElements", archivesPage.getTotalElements());

        return ResponseEntity.ok(response);
    }

    /**
     * 4. 獨立 API：獲取「舉報管理」分頁數據 (已修復代理序列化問題 + 篩選失效問題)
     */
    @GetMapping("/api/reviews/reports")
    @ResponseBody
    public ResponseEntity<?> getReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String reporterUsername,
            @RequestParam(required = false) String reportedUsername) {

        // 構建排序規則
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "status").and(Sort.by(Sort.Direction.DESC, "createdAt")));

        // 【核心修復】：使用支持篩選的 Repository 方法，而不是 findAll
        Page<Report> reportsPage = reportRepository.findByFilters(reporterUsername, reportedUsername, pageable);
        List<Report> reportList = reportsPage.getContent();

        // 1. 提取所有需要查詢的 Review ID (僅用於檢查刪除狀態)
        List<Long> reviewIds = reportList.stream()
                .map(Report::getReviewId).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        Set<Long> existingReviewIds = new HashSet<>();
        if (!reviewIds.isEmpty()) {
            List<Review> existingReviews = reviewRepository.findAllById(reviewIds);
            existingReviewIds = existingReviews.stream().map(Review::getReviewId).collect(Collectors.toSet());
        }

        // 2. 提取 User ID，避免 N+1 查詢與代理序列化問題
        List<Long> reporterIds = reportList.stream()
                .map(r -> r.getReporter() != null ? r.getReporter().getId() : null).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> reportedUserIds = reportList.stream()
                .map(r -> r.getReportedUser() != null ? r.getReportedUser().getId() : null).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        Map<Long, User> reporterMap = userRepository.findAllById(reporterIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, User> reportedUserMap = userRepository.findAllById(reportedUserIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        // 3. 組裝純淨的 Map 數據 (徹底杜絕 Hibernate 代理對象)
        List<Map<String, Object>> reportDataList = new ArrayList<>();
        for (Report report : reportList) {
            Map<String, Object> item = new HashMap<>();
            item.put("reportId", report.getReportId());
            item.put("category", report.getCategory());
            item.put("reason", report.getReason());
            item.put("status", report.getStatus() != null ? report.getStatus().name() : "UNKNOWN");
            item.put("createdAt", report.getCreatedAt());
            item.put("reviewId", report.getReviewId());

            // 優先使用舉報時保存的內容快照
            String contentToShow = report.getReportContent();
            if (contentToShow == null || contentToShow.trim().isEmpty()) {
                contentToShow = "無內容快照";
            }
            item.put("content", contentToShow);

            // 手動組裝 reporter 信息
            Map<String, Object> reporterInfo = new HashMap<>();
            if (report.getReporter() != null && report.getReporter().getId() != null) {
                User reporter = reporterMap.get(report.getReporter().getId());
                reporterInfo.put("username", reporter != null ? reporter.getUsername() : "未知");
            } else {
                reporterInfo.put("username", "未知");
            }
            item.put("reporter", reporterInfo);

            // 手動組裝 reportedUser 信息
            Map<String, Object> reportedUserInfo = new HashMap<>();
            if (report.getReportedUser() != null && report.getReportedUser().getId() != null) {
                User reportedUser = reportedUserMap.get(report.getReportedUser().getId());
                reportedUserInfo.put("username", reportedUser != null ? reportedUser.getUsername() : "未知");
            } else {
                reportedUserInfo.put("username", "未知");
            }
            item.put("reportedUser", reportedUserInfo);

            // 判斷是否已被封禁
            boolean isAlreadyBanned = false;
            if (report.getReviewId() != null) {
                isAlreadyBanned = adminPenaltyRepository.existsByReviewId(report.getReviewId());
            }
            item.put("isAlreadyBanned", isAlreadyBanned);

            // 標記該評論是否已被刪除
            boolean isDeleted = report.getReviewId() == null || !existingReviewIds.contains(report.getReviewId());
            item.put("isDeleted", isDeleted);

            // 判斷是否被拉黑 (從已加載的 Map 中取 username，避免觸發代理)
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

        Map<String, Object> response = new HashMap<>();
        response.put("content", reportDataList);
        response.put("currentPage", reportsPage.getNumber());

        // 【確保分頁邏輯穩健】：即使數據為空或只有1頁，也能正確返回給前端
        int totalPages = reportsPage.getTotalPages();
        response.put("totalPages", totalPages == 0 ? 1 : totalPages);
        response.put("totalElements", reportsPage.getTotalElements());

        return ResponseEntity.ok(response);
    }
}