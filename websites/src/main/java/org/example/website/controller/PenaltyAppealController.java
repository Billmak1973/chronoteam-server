package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.AdminPenalty;
import org.example.website.entity.Appeal;
import org.example.website.repository.AdminPenaltyRepository;
import org.example.website.repository.AppealRepository;
import org.example.website.service.AdminPenaltyService;
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

    public PenaltyAppealController(AdminPenaltyRepository adminPenaltyRepository,
                                   AppealRepository appealRepository,
                                   AdminPenaltyService adminPenaltyService) {
        this.adminPenaltyRepository = adminPenaltyRepository;
        this.appealRepository = appealRepository;
        this.adminPenaltyService = adminPenaltyService;
    }


    @GetMapping("/penalties")
    public String managePenaltiesAndAppeals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"));

        adminPenaltyService.updateExpiredStatus();

        adminPenaltyService.updateExpiredAppeals();

        // 1. 獲取處罰記錄 (按開始時間倒序)
        Page<AdminPenalty> penaltiesPage = adminPenaltyRepository.findAll(pageable);

        // 2. 獲取申訴記錄 (按創建時間倒序)
        Pageable appealPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Appeal> appealsPage = appealRepository.findAll(appealPageable);

        //  修复后的正确写法（通过恒定的 notificationId 关联，完美解决多次申诉问题）
        Map<Long, String> reviewContentMap = new HashMap<>();
        for (Appeal appeal : appealsPage.getContent()) {
            // 【核心修复】：不再使用 appealId 反查，而是使用 notificationId 关联！
            // 因为同一个处罚产生的所有申诉（无论第1次还是第3次），它们的 notificationId 都是相同的。
            // 这样即使 AdminPenalty 的 appealId 被更新为最新的申诉ID，旧的申诉依然能通过 notificationId 稳稳找到处罚记录和 reviewContent。
            Optional<AdminPenalty> penaltyOpt = adminPenaltyRepository.findByNotificationId(appeal.getNotificationId());

            if (penaltyOpt.isPresent()) {
                // Map 的 Key 依然使用 appeal.getAppealId()，保证前端渲染时能正确匹配
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

        model.addAttribute("penalties", penaltiesPage.getContent());
        model.addAttribute("penaltyTotalPages", penaltiesPage.getTotalPages());
        model.addAttribute("penaltyCurrentPage", page);

        model.addAttribute("appeals", appealsPage.getContent());
        model.addAttribute("appealTotalPages", appealsPage.getTotalPages());
        model.addAttribute("appealCurrentPage", page);
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