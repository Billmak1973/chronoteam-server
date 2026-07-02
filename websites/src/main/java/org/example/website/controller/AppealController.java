package org.example.website.controller;

import org.example.website.dto.AppealRequest;
import org.example.website.dto.ApiResponse;
import org.example.website.service.AppealService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/appeal")
public class AppealController {

    private final AppealService appealService;

    public AppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse> submitAppeal(
            @RequestBody AppealRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("請先登入"));
        }

        String username = authentication.getName();
        Map<String, Object> result = appealService.submitAppeal(username, request);

        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(ApiResponse.ok((String) result.get("message")));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error((String) result.get("message")));
        }
    }
}