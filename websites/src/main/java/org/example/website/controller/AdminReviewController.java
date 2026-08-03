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
            if (rating != null) {
                item.put("rating", String.format("%.1f", rating));
            } else {
                item.put("rating", null);
            }

            item.put("createdAt", review.getCreatedAt());
            item.put("pinned", review.getPinned());
            item.put("likeCount", review.getLikeCount());
            item.put("dislikeCount", review.getDislikeCount());

            Map<String, Object> userInfo = new HashMap<>();
            if (review.getUser() != null && review.getUser().getId() != null) {
                User user = userMap.get(review.getUser().getId());
                userInfo.put("username", user != null ? user.getUsername() : "未知");
                userInfo.put("id", user != null ? user.getId() : null);
            }
            item.put("user", userInfo);

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

        int totalPages = reviewsPage.getTotalPages() == 0 ? 1 : reviewsPage.getTotalPages();
        Map<String, Object> response = new HashMap<>();
        response.put("content", cleanReviews);
        response.put("currentPage", reviewsPage.getNumber());
        response.put("totalPages", totalPages);
        response.put("totalElements", reviewsPage.getTotalElements());

        // 【全新思路】：後端直接計算並返回智能分頁結構，前端無需複雜計算，直接無腦遍歷渲染
        response.put("smartPages", generateSmartPagination(reviewsPage.getNumber(), totalPages));

        return ResponseEntity.ok(response);
    }

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

        int totalPages = archivesPage.getTotalPages() == 0 ? 1 : archivesPage.getTotalPages();
        Map<String, Object> response = new HashMap<>();
        response.put("content", archiveDataList);
        response.put("currentPage", archivesPage.getNumber());
        response.put("totalPages", totalPages);
        response.put("totalElements", archivesPage.getTotalElements());

        // 【全新思路】：後端直接計算並返回智能分頁結構
        response.put("smartPages", generateSmartPagination(archivesPage.getNumber(), totalPages));

        return ResponseEntity.ok(response);
    }

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

        int totalPages = reportsPage.getTotalPages() == 0 ? 1 : reportsPage.getTotalPages();
        Map<String, Object> response = new HashMap<>();
        response.put("content", reportDataList);
        response.put("currentPage", reportsPage.getNumber());
        response.put("totalPages", totalPages);
        response.put("totalElements", reportsPage.getTotalElements());

        // 【全新思路】：後端直接計算並返回智能分頁結構
        response.put("smartPages", generateSmartPagination(reportsPage.getNumber(), totalPages));

        return ResponseEntity.ok(response);
    }

    // ==========================================
    // 內部類：用於智能分頁渲染數據傳輸
    // ==========================================
    public static class PageItem {
        private boolean isEllipsis;
        private Integer pageNumber; // 1-based 頁碼

        public PageItem(boolean isEllipsis, Integer pageNumber) {
            this.isEllipsis = isEllipsis;
            this.pageNumber = pageNumber;
        }
        public boolean isEllipsis() { return isEllipsis; }
        public Integer getPageNumber() { return pageNumber; }
    }

    /**
     * 生成智能分頁列表的核心算法 (全新思路：後端計算，前端無腦渲染)
     * 規則：第一頁永遠顯示，最後一頁永遠顯示，當前頁及前後各1頁顯示，使用省略號分隔。
     * @param currentPage 當前頁 (0-based, Spring Data JPA 默認)
     * @param totalPages 總頁數
     * @return 智能分頁項目列表
     */
    private List<PageItem> generateSmartPagination(int currentPage, int totalPages) {
        List<PageItem> pages = new ArrayList<>();

        // 邊界檢查：哪怕總頁數為0，也強制返回第1頁，確保"第一頁永遠出現"
        if (totalPages <= 0) {
            pages.add(new PageItem(false, 1));
            return pages;
        }

        // 情況1：總頁數 <= 7，直接顯示所有頁碼 (1-based)
        if (totalPages <= 7) {
            for (int i = 1; i <= totalPages; i++) {
                pages.add(new PageItem(false, i));
            }
            return pages;
        }

        // 情況2：總頁數 > 7，智能顯示
        // 1. 第一頁永遠顯示 (1-based)
        pages.add(new PageItem(false, 1));

        // 2. 計算當前頁(0-based)對應的 1-based 頁碼
        int current1Based = currentPage + 1;

        // 3. 如果當前頁靠近第一頁（前3頁），不需要第一個省略號
        if (current1Based <= 3) {
            for (int i = 2; i <= 4; i++) {
                pages.add(new PageItem(false, i));
            }
            pages.add(new PageItem(true, null)); // 省略號
        }
        // 4. 如果當前頁靠近最後一頁（後3頁），不需要第二個省略號
        else if (current1Based >= totalPages - 2) {
            pages.add(new PageItem(true, null)); // 省略號
            for (int i = totalPages - 3; i <= totalPages - 1; i++) {
                pages.add(new PageItem(false, i));
            }
        }
        // 5. 當前頁在中間位置
        else {
            pages.add(new PageItem(true, null)); // 第一個省略號
            pages.add(new PageItem(false, current1Based - 1));
            pages.add(new PageItem(false, current1Based));
            pages.add(new PageItem(false, current1Based + 1));
            pages.add(new PageItem(true, null)); // 第二個省略號
        }

        // 6. 最後一頁永遠顯示
        pages.add(new PageItem(false, totalPages));

        return pages;
    }
}