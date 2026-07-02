package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.service.AdminPenaltyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/penalty")
public class AdminPenaltyController {

    private final AdminPenaltyService adminPenaltyService;

    public AdminPenaltyController(AdminPenaltyService adminPenaltyService) {
        this.adminPenaltyService = adminPenaltyService;
    }

    // 拉黑
    @PostMapping("/blacklist/{targetUsername}")
    public ResponseEntity<ApiResponse> blacklistUser(
            @PathVariable String targetUsername,
            @RequestParam(required = false, defaultValue = "嚴重違反社區規範") String reason,
            Authentication authentication) {
        if (!"admin".equals(authentication.getName())) return ResponseEntity.status(403).body(ApiResponse.error("無權操作"));
        try {
            adminPenaltyService.blacklistUser(targetUsername, authentication.getName(), reason);
            return ResponseEntity.ok(ApiResponse.ok("已成功永久拉黑該用戶"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // 解除拉黑
    @DeleteMapping("/blacklist/{targetUsername}")
    public ResponseEntity<ApiResponse> unblacklistUser(
            @PathVariable String targetUsername,
            Authentication authentication) {
        if (!"admin".equals(authentication.getName())) return ResponseEntity.status(403).body(ApiResponse.error("無權操作"));
        try {
            adminPenaltyService.unblacklistUser(targetUsername);
            return ResponseEntity.ok(ApiResponse.ok("已解除拉黑"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}