package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.ViewHistory;
import org.example.website.service.ViewHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class ViewHistoryController {
    private final ViewHistoryService viewHistoryService;

    public ViewHistoryController(ViewHistoryService viewHistoryService) {
        this.viewHistoryService = viewHistoryService;
    }

    /**
     *  清除瀏覽歷史
     */
    @PostMapping("/clear")
    public ResponseEntity<ApiResponse> clearHistory(
            @RequestParam String period,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated() ||
                    "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(401)
                        .body(ApiResponse.error("請先登入"));
            }

            String username = authentication.getName();

            // 驗證 period 參數
            if (!List.of("1day", "1week", "1month", "all").contains(period)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("無效的時間範圍"));
            }

            viewHistoryService.clearHistory(username, period);

            String message = switch (period) {
                case "1day" -> "已清除 1 天內的瀏覽記錄";
                case "1week" -> "已清除 1 週內的瀏覽記錄";
                case "1month" -> "已清除 1 個月內的瀏覽記錄";
                case "all" -> "已清除所有瀏覽記錄";
                default -> "清除成功";
            };

            return ResponseEntity.ok(ApiResponse.ok(message));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("清除失敗: " + e.getMessage()));
        }
    }

    /**
     * 批量刪除瀏覽歷史 (新增/修正)
     * 注意：路徑改為 "/batch-delete"，因為類上已有 "/api/history"
     */
    @DeleteMapping("/batch-delete")
    public ResponseEntity<ApiResponse> batchDeleteHistory(
            @RequestBody List<Long> historyIds,
            Authentication authentication) {
        try {
            // 1. 權限校驗
            if (authentication == null || !authentication.isAuthenticated() ||
                    "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(401)
                        .body(ApiResponse.error("請先登入"));
            }

            String username = authentication.getName();

            // 2. 參數校驗
            if (historyIds == null || historyIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("請選擇要刪除的記錄"));
            }

            // 3. 執行刪除 (傳入 username 確保安全)
            viewHistoryService.batchDelete(username, historyIds);

            return ResponseEntity.ok(ApiResponse.ok("成功刪除 " + historyIds.size() + " 項歷史記錄"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("刪除失敗: " + e.getMessage()));
        }
    }
}