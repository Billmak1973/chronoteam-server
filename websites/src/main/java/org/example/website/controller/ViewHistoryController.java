package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.service.ViewHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@Tag(name = "瀏覽歷史管理", description = "用戶瀏覽歷史記錄的清除與批量管理接口")
public class ViewHistoryController {

    private final ViewHistoryService viewHistoryService;

    public ViewHistoryController(ViewHistoryService viewHistoryService) {
        this.viewHistoryService = viewHistoryService;
    }

    /**
     * 清除瀏覽歷史
     */
    @Operation(
            summary = "清除瀏覽歷史",
            description = "根據指定的時間範圍（1天、1週、1個月或全部）清除當前登入用戶的瀏覽歷史記錄。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "清除成功", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "無效的時間範圍參數"),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效"),
            @ApiResponse(responseCode = "500", description = "服務器內部錯誤")
    })
    @PostMapping("/clear")
    public ResponseEntity<Result> clearHistory(
            @Parameter(description = "時間範圍：1day (1天內), 1week (1週內), 1month (1個月內), 或 all (全部)", required = true, example = "1week")
            @RequestParam String period,

            @Parameter(hidden = true)
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated() ||
                    "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(401)
                        .body(Result.error("請先登入"));
            }

            String username = authentication.getName();

            // 驗證 period 參數
            if (!List.of("1day", "1week", "1month", "all").contains(period)) {
                return ResponseEntity.badRequest()
                        .body(Result.error("無效的時間範圍"));
            }

            viewHistoryService.clearHistory(username, period);

            String message = switch (period) {
                case "1day" -> "已清除 1 天內的瀏覽記錄";
                case "1week" -> "已清除 1 週內的瀏覽記錄";
                case "1month" -> "已清除 1 個月內的瀏覽記錄";
                case "all" -> "已清除所有瀏覽記錄";
                default -> "清除成功";
            };

            return ResponseEntity.ok(Result.ok(message));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Result.error("清除失敗: " + e.getMessage()));
        }
    }

    /**
     * 批量刪除瀏覽歷史
     */
    @Operation(
            summary = "批量刪除瀏覽歷史",
            description = "根據提供的歷史記錄 ID 列表，批量刪除當前登入用戶的指定瀏覽歷史記錄。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "刪除成功", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "未提供要刪除的記錄 ID 或參數錯誤"),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效"),
            @ApiResponse(responseCode = "500", description = "服務器內部錯誤")
    })
    @DeleteMapping("/batch-delete")
    public ResponseEntity<Result> batchDeleteHistory(
            @Parameter(description = "要刪除的瀏覽歷史記錄 ID 列表", required = true)
            @RequestBody List<Long> historyIds,

            @Parameter(hidden = true)
            Authentication authentication) {
        try {
            // 1. 權限校驗
            if (authentication == null || !authentication.isAuthenticated() ||
                    "anonymousUser".equals(authentication.getPrincipal())) {
                return ResponseEntity.status(401)
                        .body(Result.error("請先登入"));
            }

            String username = authentication.getName();

            // 2. 參數校驗
            if (historyIds == null || historyIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Result.error("請選擇要刪除的記錄"));
            }

            // 3. 執行刪除 (傳入 username 確保安全，防止越權刪除)
            viewHistoryService.batchDelete(username, historyIds);

            return ResponseEntity.ok(Result.ok("成功刪除 " + historyIds.size() + " 項歷史記錄"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Result.error("刪除失敗: " + e.getMessage()));
        }
    }
}