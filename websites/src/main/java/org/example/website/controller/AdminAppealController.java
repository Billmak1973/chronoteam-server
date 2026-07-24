package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Appeal;
import org.example.website.security.CustomUserDetails;
import org.example.website.service.AppealService; // 假设你有一个 Service
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/appeal")
public class AdminAppealController {

    private final AppealService appealService;

    public AdminAppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    /**
     * 处理申诉 (同意/拒绝)
     */
    @PostMapping("/{appealId}/review")
    public ResponseEntity<?> reviewAppeal(
            @PathVariable Long appealId,
            @RequestBody Map<String, String> payload,
            Authentication authentication) {

        String adminResponse = payload.get("adminResponse");
        String decision = payload.get("decision"); // "APPROVED" 或 "REJECTED"

        // 1. 参数校验
        if (adminResponse == null || adminResponse.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("管理员回复不能为空"));
        }
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("无效的操作类型"));
        }

        // 2. 获取当前管理员 ID
        // 注意：根据你的 SecurityConfig，Principal 应该是 CustomUserDetails
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long adminId = userDetails.getId();

        try {
            // 3. 调用 Service 处理业务
            appealService.processAppeal(appealId, adminResponse, decision, adminId);
            return ResponseEntity.ok(ApiResponse.ok("申诉处理成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}