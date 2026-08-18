package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.website.dto.Result;
import org.example.website.entity.Keyword;
import org.example.website.service.KeywordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/keywords")
@RequiredArgsConstructor
@Tag(name = "後台關鍵詞管理", description = "管理員對系統敏感詞/關鍵詞進行增刪改查的相關接口")
public class KeywordController {

    private final KeywordService keywordService;

    @Operation(
            summary = "獲取所有關鍵詞列表",
            description = "按創建時間倒序返回系統中所有的關鍵詞記錄，供後台管理頁面展示。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "401", description = "未登入或無權限"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員")
    })
    @GetMapping
    public ResponseEntity<?> getAllKeywords() {
        return ResponseEntity.ok(Result.okWithData("獲取成功", keywordService.getAllKeywords()));
    }

    @Operation(
            summary = "新增關鍵詞",
            description = "向系統中添加新的關鍵詞。若關鍵詞為空或已存在，將返回錯誤提示。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "添加成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "關鍵詞為空或該關鍵詞已存在"),
            @ApiResponse(responseCode = "401", description = "未登入或無權限"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員")
    })
    @PostMapping
    public ResponseEntity<?> addKeyword(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "包含關鍵詞內容的請求體",
                    required = true,
                    content = @Content(schema = @Schema(example = "{\"keyword\": \"違規詞\"}"))
            )
            @RequestBody Map<String, String> request) {
        try {
            Keyword keyword = keywordService.addKeyword(request.get("keyword"));
            return ResponseEntity.ok(Result.okWithData("添加成功", keyword));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @Operation(
            summary = "修改關鍵詞",
            description = "根據關鍵詞 ID 更新其內容。若新關鍵詞為空或與其他關鍵詞重複，將返回錯誤提示。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "修改成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "關鍵詞不存在、為空或新關鍵詞已存在"),
            @ApiResponse(responseCode = "401", description = "未登入或無權限"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員")
    })
    @PutMapping("/{id}")
    public ResponseEntity<?> updateKeyword(
            @Parameter(description = "要修改的關鍵詞 ID", example = "1", required = true)
            @PathVariable Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "包含新關鍵詞內容的請求體",
                    required = true,
                    content = @Content(schema = @Schema(example = "{\"keyword\": \"新違規詞\"}"))
            )
            @RequestBody Map<String, String> request) {
        try {
            Keyword keyword = keywordService.updateKeyword(id, request.get("keyword"));
            return ResponseEntity.ok(Result.okWithData("修改成功", keyword));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    @Operation(
            summary = "刪除關鍵詞",
            description = "根據關鍵詞 ID 從系統中徹底刪除該關鍵詞記錄。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "刪除成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "關鍵詞不存在"),
            @ApiResponse(responseCode = "401", description = "未登入或無權限"),
            @ApiResponse(responseCode = "403", description = "無權操作，僅限管理員")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteKeyword(
            @Parameter(description = "要刪除的關鍵詞 ID", example = "1", required = true)
            @PathVariable Long id) {
        keywordService.deleteKeyword(id);
        return ResponseEntity.ok(Result.ok("刪除成功"));
    }
}