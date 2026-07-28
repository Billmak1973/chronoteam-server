package org.example.website.controller;

import org.example.website.entity.Report;
import org.example.website.entity.Review;
import org.example.website.entity.ReviewArchive;
import org.example.website.repository.ReportRepository;
import org.example.website.repository.ReviewArchiveRepository;
import org.example.website.repository.ReviewRepository;
import org.example.website.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
public class AdminReviewController {

    private final ReviewRepository reviewRepository;
    private final ReviewArchiveRepository archiveRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    public AdminReviewController(ReviewRepository reviewRepository,
                                 ReviewArchiveRepository archiveRepository,
                                 ReportRepository reportRepository, UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.archiveRepository = archiveRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/reviews")
    public String manageReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        // 1. 評論列表：按創建時間 (createdAt) 倒序
        Pageable reviewPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviews = reviewRepository.findAll(reviewPageable);

        // 2. 刪除歸檔列表：按刪除時間 (deletedAt) 倒序 【核心修復點】
        Pageable archivePageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deletedAt"));
        Page<ReviewArchive> archives = archiveRepository.findAll(archivePageable);

        // 3. 舉報記錄：按狀態和創建時間排序
        Pageable reportPageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "status").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<Report> reports = reportRepository.findAll(reportPageable);

        // 將數據傳遞給 Thymeleaf 模板
        model.addAttribute("reviews", reviews.getContent());
        model.addAttribute("archives", archives.getContent());
        model.addAttribute("reports", reports.getContent());

        // 傳遞分頁信息供前端渲染分頁器
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", Math.max(reviews.getTotalPages(), Math.max(archives.getTotalPages(), reports.getTotalPages())));
        return "admin/admin-reviews";
    }
}