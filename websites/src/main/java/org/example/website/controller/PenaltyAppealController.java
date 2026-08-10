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
import org.example.website.util.PaginationUtils;
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
     * 1. 頁面骨架渲染
     */
    @GetMapping("/penalties")
    public String managePenaltiesPage(Model model) {
        return "admin/admin-penalties";
    }

    // ==========================================
    // API 1: 獲取處罰記錄列表
    // ==========================================
    @GetMapping("/api/admin/penalties/list")
    @ResponseBody
    public ResponseEntity<?> getPenaltiesList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String username
    ) {
        // 【關鍵步驟】：在查詢前，先執行批量更新，確保數據庫狀態是最新的
        adminPenaltyService.updateExpiredStatus();
        adminPenaltyService.updateExpiredAppeals();

        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "startTime"));

    //    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));
        Page<AdminPenalty> penaltiesPage;

        // 【核心修改】：根據參數動態調用不同的 Repository 方法
        boolean hasType = type != null && !type.isEmpty();
        boolean hasStatus = status != null && !status.isEmpty();
        boolean hasUser = username != null && !username.isEmpty();

        if (!hasType && !hasStatus && !hasUser) {
            penaltiesPage = adminPenaltyRepository.findAll(pageable);
        } else if (hasUser) {
            AdminPenalty.PenaltyType pType = hasType ? AdminPenalty.PenaltyType.valueOf(type) : null;
            AdminPenalty.PenaltyStatus pStatus = hasStatus ? AdminPenalty.PenaltyStatus.valueOf(status) : null;

            if (pType != null && pStatus != null) {
                penaltiesPage = adminPenaltyRepository.findByTargetUser_UsernameAndTypeAndStatus(username, pType, pStatus, pageable);
            } else if (pType != null) {
                penaltiesPage = adminPenaltyRepository.findByTargetUser_UsernameAndType(username, pType, pageable);
            } else if (pStatus != null) {
                penaltiesPage = adminPenaltyRepository.findByTargetUser_UsernameAndStatus(username, pStatus, pageable);
            } else {
                penaltiesPage = adminPenaltyRepository.findByTargetUser_Username(username, pageable);
            }
        } else {
            if (hasType && !hasStatus) {
                penaltiesPage = adminPenaltyRepository.findByType(AdminPenalty.PenaltyType.valueOf(type), pageable);
            } else if (!hasType && hasStatus) {
                penaltiesPage = adminPenaltyRepository.findByStatus(AdminPenalty.PenaltyStatus.valueOf(status), pageable);
            } else {
                penaltiesPage = adminPenaltyRepository.findByTypeAndStatus(
                        AdminPenalty.PenaltyType.valueOf(type),
                        AdminPenalty.PenaltyStatus.valueOf(status),
                        pageable
                );
            }
        }

        // 構建關聯數據 Map (申訴狀態映射) - Key 是 Long (appealId)
        Map<Long, String> appealStatusMap = new HashMap<>();
        for (AdminPenalty penalty : penaltiesPage.getContent()) {
            if (penalty.getAppealId() != null) {
                Optional<Appeal> appealOpt = appealRepository.findById(penalty.getAppealId());
                if (appealOpt.isPresent()) {
                    appealStatusMap.put(penalty.getAppealId(), appealOpt.get().getStatus().name());
                }
            }
        }

        // 數據清洗 (轉換為 Map 列表)
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

        // 【核心修復】：將 Map<Long, String> 包裝進 Map<String, Object>
        // 這樣 PaginationUtils 會將其作為 "appealStatusMap" 字段放入 JSON 根層級
        // 前端可以通過 data.appealStatusMap[appealId] 訪問
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("appealStatusMap", appealStatusMap);

        Map<String, Object> response = PaginationUtils.buildPageResponse(penaltiesPage, cleanPenalties, extraData);

        response.put("currentPage", page);
        return ResponseEntity.ok(response);
    }

    // ==========================================
    // API 2: 獲取申訴記錄列表
    // ==========================================
    @GetMapping("/api/admin/appeals/list")
    @ResponseBody
    public ResponseEntity<?> getAppealsList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String appealType,
            @RequestParam(required = false) String status
    ) {
        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        //Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Appeal> appealsPage;

        if ((appealType == null || appealType.isEmpty()) && (status == null || status.isEmpty())) {
            appealsPage = appealRepository.findAll(pageable);
        } else if (appealType != null && !appealType.isEmpty() && (status == null || status.isEmpty())) {
            try {
                appealsPage = appealRepository.findByAppealType(Appeal.AppealType.valueOf(appealType), pageable);
            } catch (IllegalArgumentException e) {
                appealsPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        } else if ((appealType == null || appealType.isEmpty()) && status != null && !status.isEmpty()) {
            try {
                appealsPage = appealRepository.findByStatus(Appeal.AppealStatus.valueOf(status), pageable);
            } catch (IllegalArgumentException e) {
                appealsPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        } else {
            try {
                appealsPage = appealRepository.findByAppealTypeAndStatus(
                        Appeal.AppealType.valueOf(appealType),
                        Appeal.AppealStatus.valueOf(status),
                        pageable
                );
            } catch (Exception e) {
                appealsPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
        }

        // 構建關聯數據 Map (處罰內容映射) - Key 是 Long (appealId)
        Map<Long, String> reviewContentMap = new HashMap<>();
        for (Appeal appeal : appealsPage.getContent()) {
            Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository.findByNotificationId(appeal.getNotificationId());
            if (penaltyOpt.isPresent()) {
                reviewContentMap.put(appeal.getAppealId(), penaltyOpt.get().getReviewContent());
            } else {
                reviewContentMap.put(appeal.getAppealId(), "無關聯處罰記錄");
            }
        }

        // 數據清洗
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

            if (appeal.getUser() != null) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("username", appeal.getUser().getUsername());
                userInfo.put("id", appeal.getUser().getId());
                item.put("user", userInfo);
            }
            return item;
        }).collect(Collectors.toList());

        // 【核心修復】：將 Map<Long, String> 包裝進 Map<String, Object>
        // 這樣 PaginationUtils 會將其作為 "reviewContentMap" 字段放入 JSON 根層級
        // 前端可以通過 data.reviewContentMap[appealId] 訪問
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("reviewContentMap", reviewContentMap);

        Map<String, Object> response = PaginationUtils.buildPageResponse(appealsPage, cleanAppeals, extraData);

        response.put("currentPage", page);
        return ResponseEntity.ok(response);
    }

    // ==========================================
    // API 3: 獲取限流與封禁記錄列表
    // ==========================================
    @GetMapping("/api/admin/rate-limits/list")
    @ResponseBody
    public ResponseEntity<?> getRateLimitsList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int size) {

        rateLimitService.updateExpiredBans();

        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "actionTime"));

         Page<RateLimitLog> rateLimitsPage = rateLimitLogRepository.findAll(pageable);

        // 數據清洗
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

            if (log.getUser() != null) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("username", log.getUser().getUsername());
                userInfo.put("id", log.getUser().getId());
                item.put("user", userInfo);
            }
            return item;
        }).collect(Collectors.toList());

        // 4. 使用 PaginationUtils 構建響應 (無 extraData)
        Map<String, Object> response = PaginationUtils.buildPageResponse(rateLimitsPage, cleanRateLimits);

        response.put("currentPage", page);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/appeal/{appealId}")
    @ResponseBody
    public ResponseEntity<ApiResponse> getAppealDetail(@PathVariable Long appealId) {
        try {
            Appeal appeal = appealRepository.findById(appealId)
                    .orElseThrow(() -> new RuntimeException("申訴記錄不存在"));

            Map<String, Object> data = new HashMap<>();
            data.put("appealId", appeal.getAppealId());
            data.put("reason", appeal.getReason());
            data.put("appealType", appeal.getAppealType().name());
            data.put("status", appeal.getStatus().name());
            data.put("createdAt", appeal.getCreatedAt());
            data.put("adminResponse", appeal.getAdminResponse());

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