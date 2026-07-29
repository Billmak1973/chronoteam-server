package org.example.website.controller;

import org.example.website.entity.*;
import org.example.website.repository.*;
import org.example.website.service.AdminPenaltyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

    public AdminReviewController(ReviewRepository reviewRepository,
                                 ReviewArchiveRepository archiveRepository,
                                 ReportRepository reportRepository,
                                 UserRepository userRepository,
                                 ProductRepository productRepository, AdminPenaltyRepository adminPenaltyRepository, AdminPenaltyService adminPenaltyService) {
        this.reviewRepository = reviewRepository;
        this.archiveRepository = archiveRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.adminPenaltyRepository = adminPenaltyRepository;
        this.adminPenaltyService = adminPenaltyService;
    }

    @GetMapping("/reviews")
    public String manageReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        // 1. 評論列表
        Pageable reviewPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviews = reviewRepository.findAll(reviewPageable);

        // 2. 刪除歸檔列表
        Pageable archivePageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deletedAt"));
        Page<ReviewArchive> archivesPage = archiveRepository.findAll(archivePageable);
        List<ReviewArchive> archives = archivesPage.getContent();

        // 【原有邏輯：組裝歸檔數據】
        List<Integer> productIds = archives.stream().map(ReviewArchive::getProductId).distinct().collect(Collectors.toList());
        Map<Integer, Product> productMap = productRepository.findAllById(productIds).stream().collect(Collectors.toMap(Product::getProductId, p -> p));
        List<Long> deletedByIds = archives.stream().map(ReviewArchive::getDeletedById).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, User> deletedByUserMap = userRepository.findAllById(deletedByIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> archiveDataList = new ArrayList<>();
        for (ReviewArchive archive : archives) {
            Map<String, Object> item = new HashMap<>();
            item.put("archive", archive);
            item.put("product", productMap.get(archive.getProductId()));
            item.put("deletedBy", deletedByUserMap.get(archive.getDeletedById()));
            archiveDataList.add(item);
        }

        // 3. 舉報記錄 (核心修正部分)
        Pageable reportPageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "status").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<Report> reportsPage = reportRepository.findAll(reportPageable);
        List<Report> reportList = reportsPage.getContent();

        // --- 【核心修正】：優先使用 reportContent 快照，並僅用 reviewId 檢查是否被刪除 ---

        // 1. 提取所有需要查詢的 Review ID (僅用於檢查刪除狀態，不再用於獲取內容)
        List<Long> reviewIds = reportList.stream()
                .map(Report::getReviewId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Set<Long> existingReviewIds = new HashSet<>();
        if (!reviewIds.isEmpty()) {
            // 只查詢還存在的評論，用來判斷刪除狀態
            List<Review> existingReviews = reviewRepository.findAllById(reviewIds);
            existingReviewIds = existingReviews.stream()
                    .map(Review::getReviewId)
                    .collect(Collectors.toSet());
        }

        // 2. 組裝數據傳給前端
        List<Map<String, Object>> reportDataList = new ArrayList<>();
        for (Report report : reportList) {
            Map<String, Object> item = new HashMap<>();
            item.put("report", report);

            // 【修正點】：優先使用舉報時保存的內容快照！如果沒有，才顯示提示
            String contentToShow = report.getReportContent();
            if (contentToShow == null || contentToShow.trim().isEmpty()) {
                contentToShow = "無內容快照";
            }
            item.put("content", contentToShow);

            boolean isAlreadyBanned = false;
            if (report.getReviewId() != null) {
                isAlreadyBanned = adminPenaltyRepository.existsByReviewId(report.getReviewId());
            }
            item.put("isAlreadyBanned", isAlreadyBanned);

            // 標記該評論是否已被刪除 (用於控制「刪除」按鈕的禁用狀態)
            boolean isDeleted = report.getReviewId() == null || !existingReviewIds.contains(report.getReviewId());
            item.put("isDeleted", isDeleted);

            boolean isBlacklisted = false;
            if (report.getReportedUser() != null) {
                isBlacklisted = adminPenaltyService.isBlacklisted(report.getReportedUser().getUsername());
            }
            item.put("isBlacklisted", isBlacklisted);

            reportDataList.add(item);
        }
        // --- 【核心修正結束】 ---

        // 傳遞數據給 Thymeleaf
        model.addAttribute("reviews", reviews.getContent());
        model.addAttribute("archives", archiveDataList);
        model.addAttribute("reportDataList", reportDataList); // 傳遞新的列表

        // 傳遞分頁信息
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", Math.max(reviews.getTotalPages(), Math.max(archivesPage.getTotalPages(), reportsPage.getTotalPages())));

        return "admin/admin-reviews";
    }
}