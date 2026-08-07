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
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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

    /**
     * 1. 頁面骨架渲染 (首次加載時僅渲染基礎 HTML 框架，數據交由前端 AJAX 異步獲取)
     */
    @GetMapping("/penalties")
    public String managePenaltiesPage(Model model) {
        return "admin/admin-penalties";
    }

    // ==========================================
    // API 1: 獲取處罰記錄列表 (已修復篩選功能)
    // ==========================================
    @GetMapping("/api/admin/penalties/list")
    @ResponseBody
    public ResponseEntity<?> getPenaltiesList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String type,      // 接收類型參數
            @RequestParam(required = false) String status,    // 接收狀態參數
            @RequestParam(required = false) String username   // 接收用戶名參數
    ) {
        // 【關鍵步驟】：在查詢前，先執行批量更新，確保數據庫狀態是最新的
        adminPenaltyService.updateExpiredStatus();
        adminPenaltyService.updateExpiredAppeals();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        Page<AdminPenalty> penaltiesPage;

        // 【核心修改】：根據參數動態調用不同的 Repository 方法 (整合用戶名篩選)
        boolean hasType = type != null && !type.isEmpty();
        boolean hasStatus = status != null && !status.isEmpty();
        boolean hasUser = username != null && !username.isEmpty();

        if (!hasType && !hasStatus && !hasUser) {
            // 情況 A: 無任何篩選，查全部
            penaltiesPage = adminPenaltyRepository.findAll(pageable);
        } else if (hasUser) {
            // 【新增邏輯】情況 B: 包含用戶名篩選 (優先級最高，可組合 Type/Status)
            AdminPenalty.PenaltyType pType = hasType ? AdminPenalty.PenaltyType.valueOf(type) : null;
            AdminPenalty.PenaltyStatus pStatus = hasStatus ? AdminPenalty.PenaltyStatus.valueOf(status) : null;

            if (pType != null && pStatus != null) {
                // 用戶名 + 類型 + 狀態
                penaltiesPage = adminPenaltyRepository.findByTargetUser_UsernameAndTypeAndStatus(username, pType, pStatus, pageable);
            } else if (pType != null) {
                // 用戶名 + 類型
                penaltiesPage = adminPenaltyRepository.findByTargetUser_UsernameAndType(username, pType, pageable);
            } else if (pStatus != null) {
                // 用戶名 + 狀態
                penaltiesPage = adminPenaltyRepository.findByTargetUser_UsernameAndStatus(username, pStatus, pageable);
            } else {
                // 僅用戶名
                penaltiesPage = adminPenaltyRepository.findByTargetUser_Username(username, pageable);
            }
        } else {
            // 情況 C: 不含用戶名，僅篩選 Type/Status (原有邏輯)
            if (hasType && !hasStatus) {
                penaltiesPage = adminPenaltyRepository.findByType(AdminPenalty.PenaltyType.valueOf(type), pageable);
            } else if (!hasType && hasStatus) {
                penaltiesPage = adminPenaltyRepository.findByStatus(AdminPenalty.PenaltyStatus.valueOf(status), pageable);
            } else {
                // 同時篩選類型和狀態
                penaltiesPage = adminPenaltyRepository.findByTypeAndStatus(
                        AdminPenalty.PenaltyType.valueOf(type),
                        AdminPenalty.PenaltyStatus.valueOf(status),
                        pageable
                );
            }
        }

        // 構建關聯數據 Map (申訴狀態映射) - 保持原有邏輯不變
        Map<Long, String> appealStatusMap = new HashMap<>();
        for (AdminPenalty penalty : penaltiesPage.getContent()) {
            if (penalty.getAppealId() != null) {
                Optional<Appeal> appealOpt = appealRepository.findById(penalty.getAppealId());
                if (appealOpt.isPresent()) {
                    appealStatusMap.put(penalty.getAppealId(), appealOpt.get().getStatus().name());
                }
            }
        }

        // 構建返回數據 (保持原有數據清洗邏輯)
        List<Map<String, Object>> cleanPenalties = penaltiesPage.getContent().stream().map(penalty -> {
            Map<String, Object> item = new HashMap<>();
            item.put("penaltyId", penalty.getPenaltyId());
            item.put("type", penalty.getType().name());
            item.put("status", penalty.getStatus().name());
            item.put("reason", penalty.getReason());
            item.put("reviewContent", penalty.getReviewContent());
            item.put("startTime", penalty.getStartTime());
            item.put("endTime", penalty.getEndTime());
            item.put("appealId", penalty.getAppealId());

            if (penalty.getTargetUser() != null) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("username", penalty.getTargetUser().getUsername());
                userInfo.put("id", penalty.getTargetUser().getId());
                item.put("targetUser", userInfo);
            }

            if (penalty.getAdminUser() != null) {
                Map<String, Object> adminInfo = new HashMap<>();
                adminInfo.put("username", penalty.getAdminUser().getUsername());
                item.put("adminUser", adminInfo);
            }

            return item;
        }).collect(Collectors.toList());

        int totalPages = penaltiesPage.getTotalPages() == 0 ? 1 : penaltiesPage.getTotalPages();
        Map<String, Object> response = new HashMap<>();
        response.put("content", cleanPenalties);
        response.put("currentPage", penaltiesPage.getNumber());
        response.put("totalPages", totalPages);
        response.put("totalElements", penaltiesPage.getTotalElements());

        // 【全新思路】：後端直接計算並返回智能分頁結構
        response.put("smartPages", generateSmartPagination(penaltiesPage.getNumber(), totalPages));

        // 附加關聯數據
        response.put("appealStatusMap", appealStatusMap);

        return ResponseEntity.ok(response);
    }

    // ==========================================
    // API 2: 獲取申訴記錄列表 (已修復篩選功能)
    // ==========================================
    @GetMapping("/api/admin/appeals/list")
    @ResponseBody
    public ResponseEntity<?> getAppealsList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String appealType,   // 【新增】接收申訴類型參數
            @RequestParam(required = false) String status        // 【新增】接收狀態參數
    ) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Appeal> appealsPage;

        // 【核心修復】：根據參數動態調用不同的 Repository 方法
        if ((appealType == null || appealType.isEmpty()) && (status == null || status.isEmpty())) {
            // 情況 A: 無篩選，查全部
            appealsPage = appealRepository.findAll(pageable);
        } else if (appealType != null && !appealType.isEmpty() && (status == null || status.isEmpty())) {
            // 情況 B: 僅篩選類型
            try {
                appealsPage = appealRepository.findByAppealType(Appeal.AppealType.valueOf(appealType), pageable);
            } catch (IllegalArgumentException e) {
                // 如果枚舉值不匹配，降級為查詢全部或返回空頁
                appealsPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        } else if ((appealType == null || appealType.isEmpty()) && status != null && !status.isEmpty()) {
            // 情況 C: 僅篩選狀態
            try {
                appealsPage = appealRepository.findByStatus(Appeal.AppealStatus.valueOf(status), pageable);
            } catch (IllegalArgumentException e) {
                appealsPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        } else {
            // 情況 D: 同時篩選類型和狀態 (注意：這需要 Repository 支持組合查詢)
            // 如果您的 Repository 沒有 findByAppealTypeAndStatus，這裡可以改為內存過濾或添加對應方法
            try {
                appealsPage = appealRepository.findByAppealTypeAndStatus(
                        Appeal.AppealType.valueOf(appealType),
                        Appeal.AppealStatus.valueOf(status),
                        pageable
                );
            } catch (Exception e) {
                // 若無組合查詢方法，可先按類型查再手動過濾狀態，或直接返回空
                appealsPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        }

        // 構建關聯數據 Map (處罰內容映射) - 保持原有邏輯不變
        Map<Long, String> reviewContentMap = new HashMap<>();
        for (Appeal appeal : appealsPage.getContent()) {
            Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository.findByNotificationId(appeal.getNotificationId());
            if (penaltyOpt.isPresent()) {
                reviewContentMap.put(appeal.getAppealId(), penaltyOpt.get().getReviewContent());
            } else {
                reviewContentMap.put(appeal.getAppealId(), "無關聯處罰記錄");
            }
        }

        // 構建返回數據
        List<Map<String, Object>> cleanAppeals = appealsPage.getContent().stream().map(appeal -> {
            Map<String, Object> item = new HashMap<>();
            item.put("appealId", appeal.getAppealId());
            item.put("notificationId", appeal.getNotificationId());
            item.put("appealType", appeal.getAppealType().name());
            item.put("status", appeal.getStatus().name());
            item.put("reason", appeal.getReason());
            item.put("adminResponse", appeal.getAdminResponse());
            item.put("createdAt", appeal.getCreatedAt());
            item.put("reviewedAt", appeal.getReviewedAt());

            // 用戶信息
            if (appeal.getUser() != null) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("username", appeal.getUser().getUsername());
                userInfo.put("id", appeal.getUser().getId());
                item.put("user", userInfo);
            }

            return item;
        }).collect(Collectors.toList());

        int totalPages = appealsPage.getTotalPages() == 0 ? 1 : appealsPage.getTotalPages();
        Map<String, Object> response = new HashMap<>();
        response.put("content", cleanAppeals);
        response.put("currentPage", appealsPage.getNumber());
        response.put("totalPages", totalPages);
        response.put("totalElements", appealsPage.getTotalElements());

        // 【全新思路】：後端直接計算並返回智能分頁結構
        response.put("smartPages", generateSmartPagination(appealsPage.getNumber(), totalPages));

        // 附加關聯數據
        response.put("reviewContentMap", reviewContentMap);

        return ResponseEntity.ok(response);
    }

    // ==========================================
    // API 3: 獲取限流與封禁記錄列表
    // ==========================================
    @GetMapping("/api/admin/rate-limits/list")
    @ResponseBody
    public ResponseEntity<?> getRateLimitsList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        // 【關鍵步驟】：在查詢前，先執行批量更新，確保數據庫狀態是最新的
        rateLimitService.updateExpiredBans();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "actionTime"));
        Page<RateLimitLog> rateLimitsPage = rateLimitLogRepository.findAll(pageable);

        // 構建返回數據
        List<Map<String, Object>> cleanRateLimits = rateLimitsPage.getContent().stream().map(log -> {
            Map<String, Object> item = new HashMap<>();
            item.put("logId", log.getLogId());
            item.put("actionTime", log.getActionTime());
            item.put("times", log.getTimes());
            item.put("bannedUntil", log.getBannedUntil());
            item.put("bannedBy", log.getBannedBy());
            item.put("banReason", log.getBanReason());
            item.put("status", log.getStatus().name());
            item.put("updatedAt", log.getUpdatedAt());

            // 用戶信息
            if (log.getUser() != null) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("username", log.getUser().getUsername());
                userInfo.put("id", log.getUser().getId());
                item.put("user", userInfo);
            }

            return item;
        }).collect(Collectors.toList());

        int totalPages = rateLimitsPage.getTotalPages() == 0 ? 1 : rateLimitsPage.getTotalPages();
        Map<String, Object> response = new HashMap<>();
        response.put("content", cleanRateLimits);
        response.put("currentPage", rateLimitsPage.getNumber());
        response.put("totalPages", totalPages);
        response.put("totalElements", rateLimitsPage.getTotalElements());

        // 【全新思路】：後端直接計算並返回智能分頁結構
        response.put("smartPages", generateSmartPagination(rateLimitsPage.getNumber(), totalPages));

        return ResponseEntity.ok(response);
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

    // ==========================================
    // 內部類：用於智能分頁渲染數據傳輸 (與 AdminReviewController 保持一致)
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