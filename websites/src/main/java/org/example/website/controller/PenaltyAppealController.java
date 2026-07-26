package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.AdminPenalty;
import org.example.website.entity.Appeal;
import org.example.website.entity.RateLimitLog;
import org.example.website.repository.AdminPenaltyRepository;
import org.example.website.repository.AppealRepository;
import org.example.website.repository.RateLimitLogRepository;
import org.example.website.service.AdminPenaltyService;
import org.example.website.service.RateLimitService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/admin")
public class PenaltyAppealController {

    private final AdminPenaltyRepository adminPenaltyRepository;
    private final AppealRepository appealRepository;
    private final AdminPenaltyService adminPenaltyService;

    private final RateLimitLogRepository rateLimitLogRepository;
    private final RateLimitService rateLimitService;

    public PenaltyAppealController(AdminPenaltyRepository adminPenaltyRepository,
                                   AppealRepository appealRepository,
                                   AdminPenaltyService adminPenaltyService,
                                   RateLimitLogRepository rateLimitLogRepository,
                                   RateLimitService rateLimitService) {
        this.adminPenaltyRepository = adminPenaltyRepository;
        this.appealRepository = appealRepository;
        this.adminPenaltyService = adminPenaltyService;
        this.rateLimitLogRepository = rateLimitLogRepository;
        this.rateLimitService = rateLimitService;
    }

    // ==========================================
    // 內部類：用於智能分頁渲染
    // ==========================================
    public static class PageItem {
        private boolean isEllipsis;
        private Integer pageNumber;

        public PageItem(boolean isEllipsis, Integer pageNumber) {
            this.isEllipsis = isEllipsis;
            this.pageNumber = pageNumber;
        }

        public boolean isEllipsis() {
            return isEllipsis;
        }

        public Integer getPageNumber() {
            return pageNumber;
        }
    }

    /**
     * 生成智能分頁列表的核心算法
     */
    private List<PageItem> generateSmartPagination(int currentPage, int totalPages) {
        List<PageItem> pages = new ArrayList<>();
        if (totalPages <= 7) {
            for (int i = 1; i <= totalPages; i++) {
                pages.add(new PageItem(false, i));
            }
        } else {
            pages.add(new PageItem(false, 1)); // 始終顯示第一頁
            if (currentPage > 3) {
                pages.add(new PageItem(true, null)); // 省略號
            }
            int start = Math.max(2, currentPage - 1);
            int end = Math.min(totalPages - 1, currentPage + 1);
            for (int i = start; i <= end; i++) {
                pages.add(new PageItem(false, i));
            }
            if (currentPage < totalPages - 2) {
                pages.add(new PageItem(true, null)); // 省略號
            }
            pages.add(new PageItem(false, totalPages)); // 始終顯示最後一頁
        }
        return pages;
    }

    @GetMapping("/penalties")
    public String managePenaltiesAndAppeals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Model model) {

        // 【關鍵】：Spring Data JPA 的 page 是從 0 開始的，而智能分頁算法是從 1 開始的，所以需要 +1
        int currentPage = page + 1;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));

        adminPenaltyService.updateExpiredStatus();
        adminPenaltyService.updateExpiredAppeals();

        // 1. 獲取處罰記錄 (按開始時間倒序)
        Page<AdminPenalty> penaltiesPage = adminPenaltyRepository.findAll(pageable);

        // 2. 獲取申訴記錄 (按創建時間倒序)
        Pageable appealPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Appeal> appealsPage = appealRepository.findAll(appealPageable);

        // 修复后的正确写法（通过恒定的 notificationId 关联，完美解决多次申诉问题）
        Map<Long, String> reviewContentMap = new HashMap<>();
        for (Appeal appeal : appealsPage.getContent()) {
            Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository.findByNotificationId(appeal.getNotificationId());
            if (penaltyOpt.isPresent()) {
                reviewContentMap.put(appeal.getAppealId(), penaltyOpt.get().getReviewContent());
            } else {
                reviewContentMap.put(appeal.getAppealId(), "无关联处罚记录");
            }
        }

        Map<Long, String> appealStatusMap = new HashMap<>();
        for (AdminPenalty penalty : penaltiesPage.getContent()) {
            if (penalty.getAppealId() != null) {
                Optional<Appeal> appealOpt = appealRepository.findById(penalty.getAppealId());
                if (appealOpt.isPresent()) {
                    appealStatusMap.put(penalty.getAppealId(), appealOpt.get().getStatus().name());
                }
            }
        }

        // ==========================================
        // 3. 獲取限流與封禁記錄 (新增核心邏輯)
        // ==========================================
        // 【關鍵步驟】：在查詢前，先執行批量更新，確保數據庫狀態是最新的
        rateLimitService.updateExpiredBans();

        Pageable rateLimitPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "actionTime"));
        Page<RateLimitLog> rateLimitsPage = rateLimitLogRepository.findAll(rateLimitPageable);

        // ==========================================
        // 4. 生成智能分頁列表並傳遞給前端
        // ==========================================
        List<PageItem> smartPenaltyPages = generateSmartPagination(currentPage, penaltiesPage.getTotalPages());
        List<PageItem> smartAppealPages = generateSmartPagination(currentPage, appealsPage.getTotalPages());
        List<PageItem> smartRateLimitPages = generateSmartPagination(currentPage, rateLimitsPage.getTotalPages());

        // 處罰記錄 Model 屬性
        model.addAttribute("penalties", penaltiesPage.getContent());
        model.addAttribute("penaltyTotalPages", penaltiesPage.getTotalPages());
        model.addAttribute("penaltyCurrentPage", page);
        model.addAttribute("smartPenaltyPages", smartPenaltyPages); // 新增

        // 申訴記錄 Model 屬性
        model.addAttribute("appeals", appealsPage.getContent());
        model.addAttribute("appealTotalPages", appealsPage.getTotalPages());
        model.addAttribute("appealCurrentPage", page);
        model.addAttribute("smartAppealPages", smartAppealPages); // 新增

        // 限流記錄 Model 屬性
        model.addAttribute("rateLimits", rateLimitsPage.getContent());
        model.addAttribute("rateLimitTotalPages", rateLimitsPage.getTotalPages());
        model.addAttribute("rateLimitCurrentPage", page);
        model.addAttribute("smartRateLimitPages", smartRateLimitPages); // 新增

        // 其他關聯數據
        model.addAttribute("reviewContentMap", reviewContentMap);
        model.addAttribute("appealStatusMap", appealStatusMap);

        return "admin/admin-penalties";
    }

    @GetMapping("/appeal/{appealId}")
    @ResponseBody
    public ResponseEntity<ApiResponse> getAppealDetail(@PathVariable Long appealId) {
        try {
            Appeal appeal = appealRepository.findById(appealId)
                    .orElseThrow(() -> new RuntimeException("申訴記錄不存在"));

            // 構建返回數據（包含用戶信息）
            Map<String, Object> data = new HashMap<>();
            data.put("appealId", appeal.getAppealId());
            data.put("reason", appeal.getReason());
            data.put("appealType", appeal.getAppealType().name());
            data.put("status", appeal.getStatus().name());
            data.put("createdAt", appeal.getCreatedAt());
            data.put("adminResponse", appeal.getAdminResponse());

            // 包含用戶信息
            if (appeal.getUser() != null) {
                Map<String, String> userInfo = new HashMap<>();
                userInfo.put("username", appeal.getUser().getUsername());
                userInfo.put("userId", appeal.getUser().getId().toString());
                data.put("user", userInfo);
            }

            return ResponseEntity.ok(ApiResponse.okWithData("獲取成功", data));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("獲取失敗: " + e.getMessage()));
        }
    }
}