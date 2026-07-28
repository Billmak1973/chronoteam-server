package org.example.website.controller;

import lombok.RequiredArgsConstructor;
import org.example.website.dto.ApiResponse;
import org.example.website.entity.Keyword;
import org.example.website.service.KeywordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/keywords")
@RequiredArgsConstructor
public class KeywordController {
    private final KeywordService keywordService;

    @GetMapping
    public ResponseEntity<?> getAllKeywords() {
        return ResponseEntity.ok(ApiResponse.okWithData("獲取成功", keywordService.getAllKeywords()));
    }

    @PostMapping
    public ResponseEntity<?> addKeyword(@RequestBody Map<String, String> request) {
        try {
            Keyword keyword = keywordService.addKeyword(request.get("keyword"));
            return ResponseEntity.ok(ApiResponse.okWithData("添加成功", keyword));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateKeyword(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            Keyword keyword = keywordService.updateKeyword(id, request.get("keyword"));
            return ResponseEntity.ok(ApiResponse.okWithData("修改成功", keyword));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteKeyword(@PathVariable Long id) {
        keywordService.deleteKeyword(id);
        return ResponseEntity.ok(ApiResponse.ok("刪除成功"));
    }
}